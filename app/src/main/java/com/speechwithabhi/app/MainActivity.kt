package com.speechwithabhi.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.speechwithabhi.app.ai.narration.NarrationPreparationProvider
import com.speechwithabhi.app.ai.security.SecureApiKeyStorage
import com.speechwithabhi.app.ai.synthesis.GeminiSpeechSynthesisProvider
import com.speechwithabhi.app.ai.synthesis.SpeechSynthesisDispatcher
import com.speechwithabhi.app.core.database.BookmarkEntity
import com.speechwithabhi.app.core.database.DocumentEntity
import com.speechwithabhi.app.core.database.SectionEntity
import com.speechwithabhi.app.core.database.SpeechWithAbhiDatabase
import com.speechwithabhi.app.core.datastore.AppUserPreferences
import com.speechwithabhi.app.core.datastore.UserPreferencesRepository
import com.speechwithabhi.app.core.model.DocumentSection
import com.speechwithabhi.app.core.model.PlaybackState
import com.speechwithabhi.app.core.model.SectionType
import com.speechwithabhi.app.core.storage.DocumentStorageManager
import com.speechwithabhi.app.feature.cleanup.DocumentCleanupPipeline
import com.speechwithabhi.app.feature.importing.FileImportCoordinator
import com.speechwithabhi.app.feature.importing.ImportState
import com.speechwithabhi.app.feature.voice.VoiceAuditionManager
import com.speechwithabhi.app.feature.voice.VoiceStudioUiState
import com.speechwithabhi.app.service.playback.PlaybackConnectionManager
import com.speechwithabhi.app.ui.navigation.Screen
import com.speechwithabhi.app.ui.screens.*
import com.speechwithabhi.app.ui.theme.SpeechWithAbhiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var database: SpeechWithAbhiDatabase
    private lateinit var storageManager: DocumentStorageManager
    private lateinit var importCoordinator: FileImportCoordinator
    private lateinit var userPreferencesRepo: UserPreferencesRepository
    private lateinit var apiKeyStorage: SecureApiKeyStorage
    private lateinit var playbackConnection: PlaybackConnectionManager
    private lateinit var synthesisDispatcher: SpeechSynthesisDispatcher
    private lateinit var auditionManager: VoiceAuditionManager
    private val cleanupPipeline = DocumentCleanupPipeline()
    private val narrationPreparer = NarrationPreparationProvider()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = SpeechWithAbhiDatabase.getInstance(this)
        storageManager = DocumentStorageManager(this)
        importCoordinator = FileImportCoordinator(this, storageManager)
        userPreferencesRepo = UserPreferencesRepository(this)
        apiKeyStorage = SecureApiKeyStorage(this)
        synthesisDispatcher = SpeechSynthesisDispatcher(this, apiKeyStorage)
        auditionManager = VoiceAuditionManager(this, synthesisDispatcher, storageManager, lifecycleScope)
        playbackConnection = PlaybackConnectionManager(this, lifecycleScope)
        playbackConnection.connect()

        setContent {
            val userPrefs by userPreferencesRepo.userPreferencesFlow.collectAsState(initial = AppUserPreferences())

            // Request Notification Permission for Android 13+ (POST_NOTIFICATIONS)
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { /* Permission granted or denied */ }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            SpeechWithAbhiTheme(darkTheme = userPrefs.isDarkMode) {
                MainAppShell(
                    userPrefs = userPrefs,
                    database = database,
                    storageManager = storageManager,
                    importCoordinator = importCoordinator,
                    userPreferencesRepo = userPreferencesRepo,
                    apiKeyStorage = apiKeyStorage,
                    cleanupPipeline = cleanupPipeline,
                    narrationPreparer = narrationPreparer,
                    playbackConnection = playbackConnection,
                    auditionManager = auditionManager,
                    onTestApiKey = { key -> testGeminiApiKey(key) }
                )
            }
        }
    }

    private suspend fun testGeminiApiKey(key: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash?key=$key")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Pair(true, "API key valid! Connected to Google Gemini 2.0 Flash.")
                } else {
                    val code = response.code
                    if (code == 400 || code == 403) {
                        Pair(false, "Invalid API key ($code). Please re-check key in AI Studio.")
                    } else {
                        Pair(false, "Gemini service returned HTTP $code.")
                    }
                }
            }
        } catch (e: Exception) {
            Pair(false, "Connection error: ${e.localizedMessage ?: "Network unreachable"}")
        }
    }

    override fun onDestroy() {
        auditionManager.release()
        playbackConnection.disconnect()
        super.onDestroy()
    }
}

@Composable
fun MainAppShell(
    userPrefs: AppUserPreferences,
    database: SpeechWithAbhiDatabase,
    storageManager: DocumentStorageManager,
    importCoordinator: FileImportCoordinator,
    userPreferencesRepo: UserPreferencesRepository,
    apiKeyStorage: SecureApiKeyStorage,
    cleanupPipeline: DocumentCleanupPipeline,
    narrationPreparer: NarrationPreparationProvider,
    playbackConnection: PlaybackConnectionManager,
    auditionManager: VoiceAuditionManager,
    onTestApiKey: suspend (String) -> Pair<Boolean, String>
) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Real state from Room Database
    val recentDocuments by database.documentDao().getRecentDocuments().collectAsState(initial = emptyList())
    val allDocuments by database.documentDao().getAllDocuments().collectAsState(initial = emptyList())
    val allBookmarks by database.bookmarkDao().getAllBookmarks().collectAsState(initial = emptyList())

    val playbackState by playbackConnection.playbackState.collectAsState()
    val activeChunk by playbackConnection.activeChunk.collectAsState()
    val auditioningVoiceId by auditionManager.auditioningVoiceId.collectAsState()

    var activeDocumentId by remember { mutableStateOf<String?>(null) }
    var activeDocumentTitle by remember { mutableStateOf("") }
    var activeSections by remember { mutableStateOf<List<DocumentSection>>(emptyList()) }
    var currentSpokenText by remember { mutableStateOf("") }
    var favoriteVoiceIds by remember { mutableStateOf(setOf("Kore", "Puck", "Charon")) }

    // Synchronize spoken text dynamically with active chunk
    LaunchedEffect(activeChunk) {
        activeChunk?.let { chunk ->
            currentSpokenText = chunk.text
        }
    }

    var cacheSizeBytes by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        cacheSizeBytes = storageManager.getAudioCacheSize()
    }

    // Persist playback position to database periodically during narration
    LaunchedEffect(playbackState.isPlaying, playbackState.currentPositionMs) {
        val docId = playbackState.documentId
        if (docId != null && playbackState.isPlaying) {
            database.documentDao().updatePlaybackPosition(
                id = docId,
                timestamp = System.currentTimeMillis(),
                progressMs = playbackState.currentPositionMs,
                chunkId = activeChunk?.id
            )
        }
    }

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Library.route,
        Screen.VoiceStudio.route,
        Screen.Settings.route,
        Screen.About.route
    )
    val showMiniPlayer = playbackState.documentId != null && currentRoute != Screen.Player.route

    Scaffold(
        bottomBar = {
            Column {
                if (showMiniPlayer) {
                    MiniPlayer(
                        playbackState = playbackState,
                        onPlayPause = { playbackConnection.togglePlayPause() },
                        onClick = {
                            playbackState.documentId?.let { docId ->
                                navController.navigate(Screen.Player.createRoute(docId))
                            }
                        }
                    )
                }

                if (showBottomBar) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentRoute == Screen.Home.route,
                            onClick = { navController.navigate(Screen.Home.route) },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == Screen.Library.route,
                            onClick = { navController.navigate(Screen.Library.route) },
                            icon = { Icon(Icons.Default.LocalLibrary, contentDescription = "Library") },
                            label = { Text("Library") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == Screen.VoiceStudio.route,
                            onClick = { navController.navigate(Screen.VoiceStudio.route) },
                            icon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = "Voices") },
                            label = { Text("Voices") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == Screen.Settings.route,
                            onClick = { navController.navigate(Screen.Settings.route) },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == Screen.About.route,
                            onClick = { navController.navigate(Screen.About.route) },
                            icon = { Icon(Icons.Default.Info, contentDescription = "About") },
                            label = { Text("About") }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route
            ) {
                // Home Screen
                composable(Screen.Home.route) {
                    HomeScreen(
                        recentDocuments = recentDocuments,
                        cacheSizeBytes = cacheSizeBytes,
                        onDocumentSelected = { uri ->
                            coroutineScope.launch {
                                importCoordinator.importFromUri(uri).collect { state ->
                                    if (state is ImportState.Success) {
                                        val cleanResult = cleanupPipeline.process(
                                            documentId = state.document.id,
                                            blocks = state.result.blocks
                                        )

                                        database.documentDao().insertDocument(
                                            DocumentEntity(
                                                id = state.document.id,
                                                displayName = state.document.displayName,
                                                mimeType = state.document.mimeType,
                                                sourceUri = state.document.sourceUri,
                                                fileSize = state.document.fileSize,
                                                pageCount = state.document.pageCount,
                                                importedAt = state.document.importedAt
                                            )
                                        )

                                        database.sectionDao().insertSections(
                                            cleanResult.sections.map { sec ->
                                                SectionEntity(
                                                    id = sec.id,
                                                    documentId = sec.documentId,
                                                    orderIndex = sec.orderIndex,
                                                    title = sec.title,
                                                    sectionType = sec.sectionType.name,
                                                    originalText = sec.originalText,
                                                    cleanedText = sec.cleanedText,
                                                    translatedText = sec.translatedText,
                                                    isSelected = sec.isSelected,
                                                    isRemovedCandidate = sec.isRemovedCandidate,
                                                    removalReason = sec.removalReason
                                                )
                                            }
                                        )

                                        activeDocumentId = state.document.id
                                        activeDocumentTitle = state.document.displayName
                                        activeSections = cleanResult.sections
                                        cacheSizeBytes = storageManager.getAudioCacheSize()
                                        navController.navigate(Screen.Preview.createRoute(state.document.id))
                                    }
                                }
                            }
                        },
                        onResumeDocument = { docId ->
                            coroutineScope.launch {
                                val doc = database.documentDao().getDocumentById(docId)
                                if (doc != null) {
                                    activeDocumentId = doc.id
                                    activeDocumentTitle = doc.displayName

                                    // Load sections from Room Database
                                    val sectionEntities = database.sectionDao().getSectionsForDocument(docId).first()
                                    activeSections = sectionEntities.map { entity ->
                                        DocumentSection(
                                            id = entity.id,
                                            documentId = entity.documentId,
                                            orderIndex = entity.orderIndex,
                                            title = entity.title,
                                            sectionType = SectionType.fromString(entity.sectionType),
                                            originalText = entity.originalText,
                                            cleanedText = entity.cleanedText,
                                            translatedText = entity.translatedText,
                                            isSelected = entity.isSelected,
                                            isRemovedCandidate = entity.isRemovedCandidate,
                                            removalReason = entity.removalReason
                                        )
                                    }

                                    // If playback not already playing this doc, prepare and start
                                    if (playbackState.documentId != doc.id) {
                                        val chunks = narrationPreparer.prepareNarrationChunks(
                                            documentId = doc.id,
                                            sections = activeSections
                                        )
                                        val voice = GeminiSpeechSynthesisProvider.VERIFIED_GEMINI_VOICES.find { it.voiceId == userPrefs.defaultVoiceId }
                                            ?: GeminiSpeechSynthesisProvider.VERIFIED_GEMINI_VOICES.first()

                                        playbackConnection.startPlayback(
                                            documentId = doc.id,
                                            documentTitle = doc.displayName,
                                            chunks = chunks,
                                            voice = voice
                                        )
                                    }

                                    navController.navigate(Screen.Player.createRoute(doc.id))
                                }
                            }
                        },
                        onOpenLibrary = { navController.navigate(Screen.Library.route) }
                    )
                }

                // Preview & Cleanup Review Screen
                composable(Screen.Preview.route) {
                    PreviewScreen(
                        documentTitle = activeDocumentTitle,
                        sections = activeSections,
                        onToggleSection = { sectionId ->
                            activeSections = activeSections.map {
                                if (it.id == sectionId) it.copy(isSelected = !it.isSelected) else it
                            }
                        },
                        onRestoreSection = { sectionId ->
                            activeSections = activeSections.map {
                                if (it.id == sectionId) it.copy(isRemovedCandidate = false, isSelected = true) else it
                            }
                            coroutineScope.launch {
                                database.sectionDao().restoreSection(sectionId)
                            }
                        },
                        onStartNarration = { mode ->
                            val chunks = narrationPreparer.prepareNarrationChunks(
                                documentId = activeDocumentId ?: "",
                                sections = activeSections
                            )
                            val voice = GeminiSpeechSynthesisProvider.VERIFIED_GEMINI_VOICES.find { it.voiceId == userPrefs.defaultVoiceId }
                                ?: GeminiSpeechSynthesisProvider.VERIFIED_GEMINI_VOICES.first()

                            playbackConnection.startPlayback(
                                documentId = activeDocumentId ?: "",
                                documentTitle = activeDocumentTitle,
                                chunks = chunks,
                                voice = voice
                            )

                            currentSpokenText = chunks.firstOrNull()?.text ?: ""
                            activeDocumentId?.let { id ->
                                navController.navigate(Screen.Player.createRoute(id))
                            }
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // Audio Player Screen
                composable(Screen.Player.route) {
                    PlayerScreen(
                        playbackState = playbackState,
                        currentSpokenText = currentSpokenText,
                        sections = activeSections,
                        onPlayPause = { playbackConnection.togglePlayPause() },
                        onRewind10 = { playbackConnection.rewind10Seconds() },
                        onForward30 = { playbackConnection.forward30Seconds() },
                        onSkipNext = { playbackConnection.skipNext() },
                        onSkipPrevious = { playbackConnection.skipPrevious() },
                        onSeek = { positionMs -> playbackConnection.seekTo(positionMs) },
                        onSpeedChange = { speed ->
                            playbackConnection.setSpeed(speed)
                            coroutineScope.launch { userPreferencesRepo.updatePlaybackSpeed(speed) }
                        },
                        onSleepTimerChange = { minutes ->
                            playbackConnection.setSleepTimer(minutes)
                        },
                        onJumpToSection = { sectionIndex ->
                            val selectedSec = activeSections.getOrNull(sectionIndex)
                            if (selectedSec != null) {
                                currentSpokenText = selectedSec.cleanedText
                                val chunks = narrationPreparer.prepareNarrationChunks(
                                    documentId = activeDocumentId ?: "",
                                    sections = activeSections
                                )
                                val chunkIndex = chunks.indexOfFirst { it.sectionId == selectedSec.id }.coerceAtLeast(0)
                                val voice = GeminiSpeechSynthesisProvider.VERIFIED_GEMINI_VOICES.find { it.voiceId == userPrefs.defaultVoiceId }
                                    ?: GeminiSpeechSynthesisProvider.VERIFIED_GEMINI_VOICES.first()

                                playbackConnection.startPlayback(
                                    documentId = activeDocumentId ?: "",
                                    documentTitle = activeDocumentTitle,
                                    chunks = chunks,
                                    voice = voice,
                                    startIndex = chunkIndex
                                )
                            }
                        },
                        onOpenVoiceStudio = { navController.navigate(Screen.VoiceStudio.route) },
                        onAddBookmark = {
                            activeDocumentId?.let { docId ->
                                coroutineScope.launch {
                                    database.bookmarkDao().insertBookmark(
                                        BookmarkEntity(
                                            id = UUID.randomUUID().toString(),
                                            documentId = docId,
                                            sectionId = activeSections.firstOrNull()?.id ?: "",
                                            chunkId = "chunk_0",
                                            title = activeDocumentTitle,
                                            snippet = currentSpokenText.take(100),
                                            positionMs = playbackState.currentPositionMs
                                        )
                                    )
                                }
                            }
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // Voice Studio Screen
                composable(Screen.VoiceStudio.route) {
                    VoiceStudioScreen(
                        state = VoiceStudioUiState(
                            selectedVoiceId = userPrefs.defaultVoiceId,
                            previewingVoiceId = auditioningVoiceId,
                            favoriteVoiceIds = favoriteVoiceIds
                        ),
                        onSelectVoice = { voiceId ->
                            coroutineScope.launch { userPreferencesRepo.updateDefaultVoice(voiceId) }
                        },
                        onPreviewVoice = { voiceId ->
                            val voice = GeminiSpeechSynthesisProvider.VERIFIED_GEMINI_VOICES.find { it.voiceId == voiceId }
                            if (voice != null) {
                                auditionManager.previewVoice(voice)
                            }
                        },
                        onToggleFavorite = { voiceId ->
                            favoriteVoiceIds = if (favoriteVoiceIds.contains(voiceId)) {
                                favoriteVoiceIds - voiceId
                            } else {
                                favoriteVoiceIds + voiceId
                            }
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // Library Screen
                composable(Screen.Library.route) {
                    LibraryScreen(
                        documents = allDocuments,
                        bookmarks = allBookmarks,
                        cacheSizeBytes = cacheSizeBytes,
                        onOpenDocument = { docId ->
                            coroutineScope.launch {
                                val doc = database.documentDao().getDocumentById(docId)
                                if (doc != null) {
                                    activeDocumentId = doc.id
                                    activeDocumentTitle = doc.displayName

                                    val sectionEntities = database.sectionDao().getSectionsForDocument(docId).first()
                                    activeSections = sectionEntities.map { entity ->
                                        DocumentSection(
                                            id = entity.id,
                                            documentId = entity.documentId,
                                            orderIndex = entity.orderIndex,
                                            title = entity.title,
                                            sectionType = SectionType.fromString(entity.sectionType),
                                            originalText = entity.originalText,
                                            cleanedText = entity.cleanedText,
                                            translatedText = entity.translatedText,
                                            isSelected = entity.isSelected,
                                            isRemovedCandidate = entity.isRemovedCandidate,
                                            removalReason = entity.removalReason
                                        )
                                    }

                                    if (playbackState.documentId != doc.id) {
                                        val chunks = narrationPreparer.prepareNarrationChunks(
                                            documentId = doc.id,
                                            sections = activeSections
                                        )
                                        val voice = GeminiSpeechSynthesisProvider.VERIFIED_GEMINI_VOICES.find { it.voiceId == userPrefs.defaultVoiceId }
                                            ?: GeminiSpeechSynthesisProvider.VERIFIED_GEMINI_VOICES.first()

                                        playbackConnection.startPlayback(
                                            documentId = doc.id,
                                            documentTitle = doc.displayName,
                                            chunks = chunks,
                                            voice = voice
                                        )
                                    }

                                    navController.navigate(Screen.Player.createRoute(doc.id))
                                }
                            }
                        },
                        onDeleteDocument = { doc ->
                            coroutineScope.launch {
                                database.documentDao().deleteDocument(doc)
                                storageManager.deleteAppPrivateCopy(doc.id)
                                cacheSizeBytes = storageManager.getAudioCacheSize()
                            }
                        },
                        onClearCache = {
                            coroutineScope.launch {
                                storageManager.clearAudioCache()
                                cacheSizeBytes = storageManager.getAudioCacheSize()
                            }
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // Settings Screen
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        currentApiKey = apiKeyStorage.getGeminiApiKey(),
                        hasConsent = apiKeyStorage.hasCloudProcessingConsent(),
                        preferences = userPrefs,
                        onSaveApiKey = { key -> apiKeyStorage.saveGeminiApiKey(key) },
                        onClearApiKey = { apiKeyStorage.clearCredentials() },
                        onConsentChange = { consent -> apiKeyStorage.setCloudProcessingConsent(consent) },
                        onSensitivityChange = { sens ->
                            coroutineScope.launch { userPreferencesRepo.updateCleanupSensitivity(sens) }
                        },
                        onThemeChange = { isDark ->
                            coroutineScope.launch { userPreferencesRepo.updateDarkMode(isDark) }
                        },
                        onTestApiKey = onTestApiKey,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // About Screen
                composable(Screen.About.route) {
                    AboutScreen(onNavigateBack = { navController.popBackStack() })
                }
            }
        }
    }
}
