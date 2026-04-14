package com.wew.launcher.ui.screen

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wew.launcher.ui.viewmodel.UrlStatus
import com.wew.launcher.ui.viewmodel.WebViewModel
import java.net.URI

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    initialUrl: String,
    onBack: () -> Unit
) {
    val app = LocalContext.current.applicationContext as Application
    val vm: WebViewModel = viewModel(factory = WebViewModel.factory(app, initialUrl))
    val state by vm.uiState.collectAsState()

    // WebView instance, kept stable across recompositions
    val webView = remember {
        WebView(app).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()
                    return vm.onNavigating(url)
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    vm.onPageStarted(url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    vm.onPageFinished(url, view.title)
                }
            }
        }
    }

    // Back: go back in WebView history first, then exit screen
    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onBack()
    }

    // Load initial URL once ViewModel finishes loading filters
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading && webView.url == null) {
            val blocked = vm.onNavigating(state.initialUrl)
            if (!blocked) webView.loadUrl(state.initialUrl)
        }
    }

    Scaffold(
        topBar = {
            WebTopBar(
                title = state.pageTitle.ifBlank { displayHost(state.currentUrl) },
                isLoading = state.isLoading,
                tokensLeft = state.currentTokens,
                onBack = { if (webView.canGoBack()) webView.goBack() else onBack() }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // WebView — always present, hidden behind interstitial when blocked
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize()
            )

            // Blocked / approval interstitial
            AnimatedVisibility(
                visible = state.urlStatus == UrlStatus.BLOCKED || state.urlStatus == UrlStatus.PENDING,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                BlockedInterstitial(
                    blockedUrl = state.blockedUrl,
                    requestSent = state.requestSent,
                    onRequestApproval = { vm.requestApproval() },
                    onBack = { if (webView.canGoBack()) webView.goBack() else onBack() }
                )
            }

            // Tokens exhausted banner
            if (state.tokensExhausted) {
                TokensExhaustedBanner(modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebTopBar(
    title: String,
    isLoading: Boolean,
    tokensLeft: Int,
    onBack: () -> Unit
) {
    Column {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "$tokensLeft tokens remaining",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

// ── Blocked interstitial ──────────────────────────────────────────────────────

@Composable
private fun BlockedInterstitial(
    blockedUrl: String,
    requestSent: Boolean,
    onRequestApproval: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "This site is blocked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = displayHost(blockedUrl),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Your parent hasn't approved this website. You can ask for permission.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (requestSent) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Request sent! Your parent will be notified.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Button(
                    onClick = onRequestApproval,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Ask parent for permission", fontSize = 15.sp)
                }
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Go back")
            }
        }
    }
}

// ── Tokens exhausted banner ───────────────────────────────────────────────────

@Composable
private fun TokensExhaustedBanner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No tokens left — browsing is paused",
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun displayHost(url: String): String =
    runCatching { URI(url).host?.removePrefix("www.") ?: url }.getOrElse { url }
