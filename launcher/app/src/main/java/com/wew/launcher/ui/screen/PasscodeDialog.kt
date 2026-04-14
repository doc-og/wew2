package com.wew.launcher.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.wew.launcher.ui.theme.SurfaceVariant

@Composable
fun PasscodeDialog(
    appName: String,
    attemptsLeft: Int,
    onPinSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }

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
                    text = "enter passcode",
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

                if (attemptsLeft < 3) {
                    Text(
                        text = "wrong passcode — $attemptsLeft ${if (attemptsLeft == 1) "try" else "tries"} left",
                        fontSize = 13.sp,
                        color = Color(0xFFF59E0B),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // PIN dot display
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    repeat(4) { index ->
                        val filled = index < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (filled) ElectricViolet else SurfaceVariant)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Numpad
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("⌫", "0", "✓")
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { key ->
                                NumpadKey(
                                    label = key,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        when (key) {
                                            "⌫" -> {
                                                if (enteredPin.isNotEmpty()) {
                                                    enteredPin = enteredPin.dropLast(1)
                                                }
                                            }
                                            "✓" -> {
                                                if (enteredPin.length == 4) {
                                                    onPinSubmit(enteredPin)
                                                    enteredPin = ""
                                                }
                                            }
                                            else -> {
                                                if (enteredPin.length < 4) {
                                                    enteredPin += key
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
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
                        .semantics { contentDescription = "cancel passcode dialog" }
                )
            }
        }
    }
}

@Composable
private fun NumpadKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSubmit = label == "✓"
    val isBackspace = label == "⌫"

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSubmit) ElectricViolet else SurfaceVariant)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = when {
                    isSubmit -> "submit passcode"
                    isBackspace -> "backspace"
                    else -> "digit $label"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isBackspace) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = null,
                tint = OnNight,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = label,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSubmit) Color.White else OnNight
            )
        }
    }
}
