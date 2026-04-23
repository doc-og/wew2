package com.wew.launcher.ui.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.wew.launcher.ui.theme.ElectricViolet
import com.wew.launcher.ui.theme.Night
import com.wew.launcher.ui.theme.OnNight
import com.wew.launcher.ui.theme.WewLauncherTheme

class SetupActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SETUP_MODE = "setup_mode"
    }

    private val parentAppLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val deviceId = result.data?.getStringExtra("device_id")
        Log.d("WewSetup", "parentApp returned resultCode=${result.resultCode} deviceId=$deviceId")
        if (result.resultCode == Activity.RESULT_OK && !deviceId.isNullOrEmpty()) {
            getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
                .edit().putString("device_id", deviceId).apply()
            finish()
        }
        // If canceled or no ID, stay on the connect screen so parent can retry
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("WewSetup", "onCreate — showing connect screen")
        setContent {
            WewLauncherTheme {
                SetupScreen(onOpenParentApp = ::openParentApp)
            }
        }
    }

    private fun openParentApp() {
        val intent = packageManager.getLaunchIntentForPackage("com.wew.parent")
        if (intent != null) {
            intent.putExtra(EXTRA_SETUP_MODE, true)
            // We launch for result; task-launch flags (especially NEW_TASK) can prevent
            // the result from being delivered back to this SetupActivity.
            intent.flags = intent.flags and (
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                ).inv()
            parentAppLauncher.launch(intent)
        }
        // If parent app not installed, button stays active so parent can retry after installing
    }
}

@Composable
private fun SetupScreen(onOpenParentApp: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("wew", fontSize = 48.sp, fontWeight = FontWeight.Medium, color = ElectricViolet)
        Spacer(Modifier.height(8.dp))
        SetupHeading("connect to parent account")
        Spacer(Modifier.height(24.dp))
        SetupBody(
            "open the wew parent app to finish setting up this device. sign in, name this device, and the launcher will connect automatically."
        )
        Spacer(Modifier.height(40.dp))
        SetupPrimaryButton("open parent app", onClick = onOpenParentApp)
    }
}

@Composable
private fun SetupHeading(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = OnNight, textAlign = TextAlign.Center)
}

@Composable
private fun SetupBody(text: String) {
    Text(text, fontSize = 16.sp, color = OnNight.copy(alpha = 0.7f), textAlign = TextAlign.Center, lineHeight = 24.sp)
}

@Composable
private fun SetupPrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = BrandViolet, contentColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
