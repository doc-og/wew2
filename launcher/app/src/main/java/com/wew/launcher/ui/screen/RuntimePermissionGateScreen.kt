package com.wew.launcher.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wew.launcher.ui.theme.BrandViolet
import com.wew.launcher.ui.theme.Night
import com.wew.launcher.ui.theme.OnNight

@Composable
fun RuntimePermissionGateScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Night)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "a few permissions",
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = OnNight,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "wew needs location, notifications, messages, and phone access so check-in and parent controls work. tap allow, then approve each system prompt that appears — all from here, no hunting in Android settings.",
            fontSize = 16.sp,
            color = OnNight.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandViolet,
                contentColor = Color.White
            )
        ) {
            Text("allow", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}
