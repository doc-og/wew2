package com.wew.launcher.ui.screen

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wew.launcher.ui.theme.BrandViolet
import com.wew.launcher.ui.theme.Night
import com.wew.launcher.ui.theme.OnNight
import com.wew.launcher.ui.theme.SurfaceVariant
import com.wew.launcher.ui.theme.WarningAmber

/** Not a Manifest permission — MainActivity treats this as “must be default SMS app”. */
internal const val REQUIRED_ROLE_DEFAULT_SMS = "com.wew.launcher.internal.REQUIRED_DEFAULT_SMS"

internal data class PermissionInfo(
    val label: String,
    val reason: String,
    val permissions: List<String>
)

private data class PermissionDescription(val label: String, val reason: String)

private fun describePermission(permission: String): PermissionDescription? = when (permission) {
    REQUIRED_ROLE_DEFAULT_SMS -> PermissionDescription(
        "default SMS app",
        "so wew can send, receive, and save messages — Android only allows this for one messaging app"
    )
    Manifest.permission.READ_SMS,
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.SEND_SMS -> PermissionDescription(
        "messages",
        "so wew can read, send, and receive texts using the SMS database"
    )
    Manifest.permission.CALL_PHONE -> PermissionDescription("phone calls", "so wew can make calls for you")
    Manifest.permission.READ_PHONE_STATE -> PermissionDescription("phone state", "so wew can track call activity")
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION -> PermissionDescription("location", "so parents can see check-ins on the map")
    Manifest.permission.POST_NOTIFICATIONS -> PermissionDescription("notifications", "so wew can send important alerts")
    Manifest.permission.READ_MEDIA_IMAGES -> PermissionDescription("photos", "so you can send photos in messages")
    Manifest.permission.READ_MEDIA_VIDEO -> PermissionDescription("videos", "so you can share videos in messages")
    else -> null
}

/** Deduplicates permissions that map to the same label (e.g. READ_SMS + RECEIVE_SMS → "messages"). */
internal fun missingPermissionItems(permissions: List<String>): List<PermissionInfo> =
    permissions
        .mapNotNull { permission ->
            describePermission(permission)?.let { desc -> desc to permission }
        }
        .groupBy(keySelector = { it.first.label }, valueTransform = { it.second })
        .mapNotNull { (label, groupedPermissions) ->
            val description = groupedPermissions
                .firstOrNull()
                ?.let { perm -> describePermission(perm) } ?: return@mapNotNull null
            PermissionInfo(
                label = label,
                reason = description.reason,
                permissions = groupedPermissions.distinct()
            )
        }

@Composable
internal fun RuntimePermissionGateScreen(
    missingPermissions: List<String> = emptyList(),
    onPermissionItemClick: (PermissionInfo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val items = missingPermissionItems(missingPermissions)
    val isRetry = missingPermissions.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Night)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isRetry) "still need access" else "a few permissions",
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = if (isRetry) WarningAmber else OnNight,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (isRetry)
                "wew can't work without the steps below. tap each row and complete the system screen (permissions or default SMS)."
            else
                "wew needs location, SMS access, phone, and to be your default SMS app so messaging and parent controls work. tap each row and follow the prompts.",
            fontSize = 15.sp,
            color = OnNight.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        if (items.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { perm ->
                    Surface(
                        color = SurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPermissionItemClick(perm) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    perm.label,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OnNight
                                )
                                Text(
                                    perm.reason,
                                    fontSize = 12.sp,
                                    color = OnNight.copy(alpha = 0.55f)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "tap to allow",
                                    fontSize = 11.sp,
                                    color = BrandViolet.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = "tap each row, allow the prompt, and repeat until all are saved.",
            fontSize = 13.sp,
            color = OnNight.copy(alpha = 0.55f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
            )
        }) {
            Text("open app settings", color = OnNight.copy(alpha = 0.45f), fontSize = 13.sp)
        }
    }
}
