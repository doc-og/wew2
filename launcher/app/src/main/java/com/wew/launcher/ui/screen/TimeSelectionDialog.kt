package com.wew.launcher.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wew.launcher.ui.theme.ElectricViolet
import com.wew.launcher.ui.theme.Night
import com.wew.launcher.ui.theme.OnNight

private data class TimeOption(
    val label: String,
    val minutes: Int,
    val description: String
)

@Composable
fun TimeSelectionDialog(
    appName: String,
    onTimeSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        TimeOption("15 minutes", 15, "15 minutes of access"),
        TimeOption("1 hour", 60, "1 hour of access"),
        TimeOption("rest of the day", -1, "access until bedtime")
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = Night
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "how long?",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnNight
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = appName,
                    fontSize = 14.sp,
                    color = ElectricViolet
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "costs 2 credits",
                    fontSize = 13.sp,
                    color = OnNight.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    options.forEach { option ->
                        TimeOptionCard(
                            label = option.label,
                            description = option.description,
                            onClick = { onTimeSelected(option.minutes) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "cancel",
                    fontSize = 14.sp,
                    color = OnNight.copy(alpha = 0.5f),
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(8.dp)
                        .semantics { contentDescription = "cancel time selection" }
                )
            }
        }
    }
}

@Composable
private fun TimeOptionCard(
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .semantics { contentDescription = "select $description" }
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Night
        )
        Text(
            text = description,
            fontSize = 13.sp,
            color = Night.copy(alpha = 0.6f)
        )
    }
}
