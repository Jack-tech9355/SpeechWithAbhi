package com.speechwithabhi.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.speechwithabhi.app.core.model.VoiceProfile
import com.speechwithabhi.app.feature.voice.VoiceStudioUiState
import com.speechwithabhi.app.ui.theme.*

@Composable
fun VoiceStudioScreen(
    state: VoiceStudioUiState,
    onSelectVoice: (String) -> Unit,
    onPreviewVoice: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Voice Studio", fontWeight = FontWeight.Bold)
                        Text(
                            text = "12 Gemini Celestial AI Voices • Jaswant Dehariya",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(ElectricBlue.copy(alpha = 0.15f), WarmAmber.copy(alpha = 0.1f))
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = CyanAccent)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Gemini High-Definition Celestial Voices",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Audition sample audio, mark favorites, and select your preferred narrator for natural document reading.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(state.voices) { voice ->
                val isSelected = voice.voiceId == state.selectedVoiceId
                val isFavorite = state.favoriteVoiceIds.contains(voice.voiceId)
                val isAuditioning = state.previewingVoiceId == voice.voiceId

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isSelected) Modifier.border(1.5.dp, ElectricBlue, RoundedCornerShape(16.dp))
                            else if (isAuditioning) Modifier.border(1.5.dp, CyanAccent, RoundedCornerShape(16.dp))
                            else Modifier.border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) ElectricBlue else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.RecordVoiceOver,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else WarmAmber,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = voice.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${voice.genderPresentation} • ${voice.acousticProfile}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { onToggleFavorite(voice.voiceId) }) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFavorite) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Style tags
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            voice.styleTags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        if (isAuditioning) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CyanAccent.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MiniWaveVisualizer()
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Auditioning voice...",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CyanAccent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                TextButton(onClick = { onPreviewVoice(voice.voiceId) }) {
                                    Text("Stop", color = ErrorRed)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { onPreviewVoice(voice.voiceId) },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isAuditioning) CyanAccent else BorderDark)
                            ) {
                                Icon(
                                    imageVector = if (isAuditioning) Icons.Default.Stop else Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = if (isAuditioning) ErrorRed else CyanAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isAuditioning) "Stop" else "Audition", color = if (isAuditioning) ErrorRed else CyanAccent)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onSelectVoice(voice.voiceId) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) SuccessGreen else ElectricBlue
                                )
                            ) {
                                if (isSelected) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(if (isSelected) "Active Default" else "Set Default")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniWaveVisualizer() {
    val infiniteTransition = rememberInfiniteTransition(label = "miniWave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(animation = tween(800, easing = LinearEasing)),
        label = "phase"
    )

    Canvas(modifier = Modifier.size(width = 32.dp, height = 16.dp)) {
        val barCount = 5
        val spacing = size.width / barCount
        val barWidth = spacing * 0.5f

        for (i in 0 until barCount) {
            val wave = kotlin.math.sin(phase + i * 0.8f)
            val height = size.height * (kotlin.math.abs(wave) * 0.7f + 0.3f)
            val x = i * spacing + (spacing - barWidth) / 2f
            val y = (size.height - height) / 2f
            drawRoundRect(
                color = CyanAccent,
                topLeft = Offset(x, y),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
