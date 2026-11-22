package com.speechwithabhi.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speechwithabhi.app.core.model.DocumentSection
import com.speechwithabhi.app.core.model.PlaybackState
import com.speechwithabhi.app.feature.player.SpeedController
import com.speechwithabhi.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    playbackState: PlaybackState,
    currentSpokenText: String,
    sections: List<DocumentSection> = emptyList(),
    onPlayPause: () -> Unit,
    onRewind10: () -> Unit,
    onForward30: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit = {},
    onSpeedChange: (Float) -> Unit,
    onSleepTimerChange: (Int?) -> Unit = {},
    onJumpToSection: (Int) -> Unit = {},
    onOpenVoiceStudio: () -> Unit,
    onAddBookmark: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSectionsSheet by remember { mutableStateOf(false) }
    var copyNoticeVisible by remember { mutableStateOf(false) }
    var bookmarkSavedNotice by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = playbackState.documentTitle.ifBlank { "Speech Player" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "SpeechWithAbhi • Jaswant Dehariya",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarmAmber
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (sections.isNotEmpty()) {
                        IconButton(onClick = { showSectionsSheet = true }) {
                            Icon(imageVector = Icons.Default.List, contentDescription = "Sections", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    IconButton(onClick = { showSleepTimerDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "Sleep Timer",
                            tint = if (playbackState.sleepTimerMinutes != null) WarmAmber else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        onAddBookmark()
                        bookmarkSavedNotice = true
                    }) {
                        Icon(imageVector = Icons.Default.BookmarkAdd, contentDescription = "Bookmark", tint = CyanAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MidnightNavy.copy(alpha = 0.5f)
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Status and Sleep Timer Banner
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.border(1.dp, BorderDark, RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (playbackState.activeProvider == "gemini") Icons.Default.AutoAwesome else Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = if (playbackState.activeProvider == "gemini") CyanAccent else WarningOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (playbackState.activeProvider == "gemini") {
                                    "Voice: ${playbackState.activeVoiceId} (Gemini AI)"
                                } else {
                                    "Offline Engine (Device TTS)"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (playbackState.sleepTimerMinutes != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = WarmAmber.copy(alpha = 0.2f),
                            modifier = Modifier.border(1.dp, WarmAmber.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        ) {
                            Text(
                                text = "${playbackState.sleepTimerMinutes}m left",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarmAmber,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // AI Buffering / Synthesizing indicator
                if (playbackState.isBuffering) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CyanAccent.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = CyanAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Synthesizing AI audio...",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanAccent
                            )
                        }
                    }
                }

                // Feedback notices
                AnimatedVisibility(visible = copyNoticeVisible, enter = fadeIn(), exit = fadeOut()) {
                    LaunchedEffect(copyNoticeVisible) {
                        kotlinx.coroutines.delay(2000L)
                        copyNoticeVisible = false
                    }
                    Text(
                        text = "Text copied to clipboard",
                        color = SuccessGreen,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                AnimatedVisibility(visible = bookmarkSavedNotice, enter = fadeIn(), exit = fadeOut()) {
                    LaunchedEffect(bookmarkSavedNotice) {
                        kotlinx.coroutines.delay(2000L)
                        bookmarkSavedNotice = false
                    }
                    Text(
                        text = "Bookmark saved successfully",
                        color = CyanAccent,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Audio Equalizer Waveform Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                AudioWaveformVisualizer(isPlaying = playbackState.isPlaying)
            }

            // Spoken Text Glassmorphic Card with Copy Action
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .border(1.dp, BorderDark, RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        item {
                            Text(
                                text = currentSpokenText.ifBlank { "Tap play to start natural narration." },
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Start
                            )
                        }
                    }

                    // Copy text icon button at bottom right
                    if (currentSpokenText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("SpeechWithAbhi Spoken Text", currentSpokenText)
                                clipboard.setPrimaryClip(clip)
                                copyNoticeVisible = true
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy text",
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Timeline & Transport Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Timeline Slider with formatted timestamps
                Column(modifier = Modifier.fillMaxWidth()) {
                    val progress = if (playbackState.durationMs > 0) {
                        (playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Slider(
                        value = progress,
                        onValueChange = { newFrac ->
                            if (playbackState.durationMs > 0) {
                                onSeek((newFrac * playbackState.durationMs).toLong())
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = WarmAmber,
                            activeTrackColor = ElectricBlue,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(playbackState.currentPositionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatTime(playbackState.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Secondary Row: Speed & Voice Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showSpeedDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
                    ) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp), tint = WarmAmber)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${playbackState.playbackSpeed}x")
                    }

                    OutlinedButton(
                        onClick = onOpenVoiceStudio,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
                    ) {
                        Icon(imageVector = Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp), tint = CyanAccent)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(playbackState.activeVoiceId.ifBlank { "Select Voice" })
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Primary Transport Controls with Premium Gradient FAB
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onSkipPrevious) {
                        Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Prev", modifier = Modifier.size(32.dp))
                    }

                    IconButton(onClick = onRewind10) {
                        Icon(imageVector = Icons.Default.Replay10, contentDescription = "-10s", modifier = Modifier.size(36.dp), tint = CyanAccent)
                    }

                    // Large Gradient Play/Pause FAB
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        shadowElevation = 12.dp,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(ElectricBlue, CyanAccent)
                                )
                            )
                    ) {
                        IconButton(onClick = onPlayPause) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }

                    IconButton(onClick = onForward30) {
                        Icon(imageVector = Icons.Default.Forward30, contentDescription = "+30s", modifier = Modifier.size(36.dp), tint = CyanAccent)
                    }

                    IconButton(onClick = onSkipNext) {
                        Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }

    // Speed Modal
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback Speed", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Pitch is automatically preserved via Media3 Sonic algorithms.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        SpeedController.STANDARD_SPEEDS.forEach { speed ->
                            FilterChip(
                                selected = playbackState.playbackSpeed == speed,
                                onClick = {
                                    onSpeedChange(speed)
                                    showSpeedDialog = false
                                },
                                label = { Text("${speed}x") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("Done") }
            }
        )
    }

    // Sleep Timer Modal
    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text("Sleep Timer", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Automatically pause narration after selected duration:")
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        listOf(15, 30, 45, 60).forEach { mins ->
                            FilterChip(
                                selected = playbackState.sleepTimerMinutes == mins,
                                onClick = {
                                    onSleepTimerChange(mins)
                                    showSleepTimerDialog = false
                                },
                                label = { Text("${mins}m") }
                            )
                        }
                    }
                    if (playbackState.sleepTimerMinutes != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(
                            onClick = {
                                onSleepTimerChange(null)
                                showSleepTimerDialog = false
                            }
                        ) {
                            Text("Turn Off Sleep Timer", color = ErrorRed)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) { Text("Close") }
            }
        )
    }

    // Sections Bottom Sheet
    if (showSectionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSectionsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Document Sections (${sections.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(0.6f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(sections) { index, section ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Section ${index + 1}: ${section.sectionType.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = WarmAmber
                                    )
                                    Text(
                                        text = section.cleanedText.take(60) + if (section.cleanedText.length > 60) "..." else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                TextButton(onClick = {
                                    onJumpToSection(index)
                                    showSectionsSheet = false
                                }) {
                                    Text("Play", color = CyanAccent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated audio waveform visualizer showing 20 harmonic dancing bars.
 */
@Composable
fun AudioWaveformVisualizer(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 20.dp)
    ) {
        val barCount = 20
        val spacing = size.width / barCount
        val barWidth = spacing * 0.45f
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val heightFraction = if (isPlaying) {
                val wave = kotlin.math.sin(phase + i * 0.35f)
                val cosWave = kotlin.math.cos(phase * 0.8f + i * 0.2f)
                (kotlin.math.abs(wave * cosWave) * 0.75f + 0.25f).coerceIn(0.15f, 1.0f)
            } else {
                0.15f
            }

            val barHeight = size.height * heightFraction
            val x = i * spacing + (spacing - barWidth) / 2f
            val y = centerY - barHeight / 2f

            val color = if (i % 3 == 0) WarmAmber else if (i % 2 == 0) ElectricBlueLight else CyanAccent

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
