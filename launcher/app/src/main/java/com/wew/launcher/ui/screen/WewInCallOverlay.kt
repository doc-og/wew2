package com.wew.launcher.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wew.launcher.telecom.InCallDisplayMode
import com.wew.launcher.telecom.WewCallManager
import com.wew.launcher.ui.theme.ElectricViolet
import com.wew.launcher.ui.theme.OnNight
import com.wew.launcher.ui.theme.SurfaceVariant
import com.wew.launcher.ui.theme.WarningAmber
import java.util.Locale

private fun formatClock(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}

/** In-call controls, token + timer, group thread members; unknown-caller blocked banner. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WewInCallOverlay() {
    val inCall by WewCallManager.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val state = inCall ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = SurfaceVariant,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .navigationBarsPadding()
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (state.mode == InCallDisplayMode.UNKNOWN_BLOCKED) {
                    Text(
                        text = "blocked unknown caller",
                        color = WarningAmber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "your parent was notified",
                        color = OnNight.copy(alpha = 0.65f),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { WewCallManager.hangUp() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CallEnd,
                                contentDescription = "dismiss",
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    return@Column
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.displayName,
                            color = OnNight,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = state.phoneNumber,
                            color = OnNight.copy(alpha = 0.55f),
                            fontSize = 13.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatClock(state.elapsedSeconds),
                            color = ElectricViolet,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        state.currentTokens?.let { tok ->
                            Text(
                                text = "tokens $tok",
                                color = OnNight.copy(alpha = 0.55f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (state.otherParticipants.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "also on this thread",
                        color = OnNight.copy(alpha = 0.45f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.otherParticipants.forEach { p ->
                            Surface(
                                color = OnNight.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = p.displayName.ifBlank { p.phone },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = OnNight.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { WewCallManager.toggleSpeaker(context) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SpeakerPhone,
                            contentDescription = if (state.speakerOn) "use earpiece" else "use speaker",
                            tint = if (state.speakerOn) ElectricViolet else OnNight.copy(alpha = 0.45f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { WewCallManager.hangUp() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CallEnd,
                            contentDescription = "hang up",
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    }
}
