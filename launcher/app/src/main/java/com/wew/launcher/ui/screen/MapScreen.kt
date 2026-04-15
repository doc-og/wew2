package com.wew.launcher.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wew.launcher.ui.theme.BrandViolet
import com.wew.launcher.ui.theme.Night
import com.wew.launcher.ui.theme.OnNight
import com.wew.launcher.ui.viewmodel.MapViewModel

@Composable
fun MapScreen(onBack: () -> Unit) {
    val vm: MapViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    BackHandler { onBack() }

    // As soon as the URI is ready, fire the native Maps intent and go back
    LaunchedEffect(state.pendingMapUri) {
        state.pendingMapUri?.let { uri ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching { context.startActivity(intent) }
            vm.clearUri()
            onBack()
        }
    }

    // If location fails, go back immediately so the user isn't stuck
    LaunchedEffect(state.error) {
        if (state.error != null) onBack()
    }

    // Brief loading screen shown while GPS fixes
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = BrandViolet)
        Spacer(Modifier.height(16.dp))
        Text("Finding your location…", fontSize = 15.sp, color = OnNight.copy(alpha = 0.6f))
    }
}
