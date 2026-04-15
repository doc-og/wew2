package com.wew.launcher.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.wew.launcher.telecom.WewCallManager
import com.wew.launcher.ui.theme.ElectricViolet
import com.wew.launcher.ui.theme.OnNight
import com.wew.launcher.ui.theme.SurfaceVariant

/** In-call controls: speaker toggle and hang up. Shown above all launcher screens. */
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
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
