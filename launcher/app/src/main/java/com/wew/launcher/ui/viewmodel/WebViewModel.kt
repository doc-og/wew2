package com.wew.launcher.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wew.launcher.data.model.ActionType
import com.wew.launcher.data.model.UrlFilter
import com.wew.launcher.data.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI

// ── URL check result ──────────────────────────────────────────────────────────

enum class UrlStatus {
    ALLOWED,    // passes filters — load normally
    BLOCKED,    // matches a block rule — show interstitial
    PENDING     // parent has been asked; waiting for approval
}

// ── UI state ──────────────────────────────────────────────────────────────────

data class WebUiState(
    val initialUrl: String = "",
    val isLoading: Boolean = true,
    val pageTitle: String = "",
    val currentUrl: String = "",
    val urlStatus: UrlStatus = UrlStatus.ALLOWED,
    val blockedUrl: String = "",
    val requestSent: Boolean = false,
    val currentTokens: Int = 10000,
    val dailyTokenBudget: Int = 10000,
    val tokensExhausted: Boolean = false,
    /** Set of domains for which a token has already been consumed this session. */
    val billedDomains: Set<String> = emptySet()
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class WebViewModel(
    application: Application,
    initialUrl: String
) : AndroidViewModel(application) {

    private val repo = DeviceRepository(application)
    private val prefs = application.getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)

    private var filters: List<UrlFilter> = emptyList()

    private val _uiState = MutableStateFlow(WebUiState(initialUrl = initialUrl, currentUrl = initialUrl))
    val uiState: StateFlow<WebUiState> = _uiState.asStateFlow()

    init {
        loadFiltersAndTokens()
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    private fun loadFiltersAndTokens() {
        val deviceId = prefs.getString("device_id", null) ?: return
        viewModelScope.launch {
            runCatching {
                filters = repo.getUrlFilters(deviceId)
                val device = repo.getDevice(deviceId)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentTokens = device.currentTokens,
                        dailyTokenBudget = device.dailyTokenBudget,
                        tokensExhausted = device.currentTokens <= 0
                    )
                }
            }.onFailure {
                Log.e("WebVM", "loadFiltersAndTokens failed: ${it.message}")
                _uiState.update { s -> s.copy(isLoading = false) }
            }
        }
    }

    // ── URL interception ──────────────────────────────────────────────────────

    /**
     * Called by WebViewClient before each navigation.
     * Returns true if the URL should be blocked (WebView must NOT load it),
     * false if it should load normally.
     */
    fun onNavigating(url: String): Boolean {
        val status = evaluate(url)
        return when (status) {
            UrlStatus.ALLOWED -> {
                _uiState.update { it.copy(urlStatus = UrlStatus.ALLOWED, currentUrl = url) }
                consumeTokenForDomain(url)
                false
            }
            UrlStatus.BLOCKED -> {
                _uiState.update {
                    it.copy(
                        urlStatus = UrlStatus.BLOCKED,
                        blockedUrl = url,
                        requestSent = false
                    )
                }
                true
            }
            UrlStatus.PENDING -> {
                // Already requested — just block display until approved
                _uiState.update {
                    it.copy(urlStatus = UrlStatus.PENDING, blockedUrl = url)
                }
                true
            }
        }
    }

    /**
     * Evaluate [url] against the loaded filter list.
     *
     * Rules (applied in order):
     * 1. Explicit allow rule → ALLOWED
     * 2. Explicit block rule → BLOCKED
     * 3. No rules at all → ALLOWED (permissive when no parent rules set)
     * 4. Has any rules but none matched → BLOCKED (deny-by-default when rules exist)
     */
    private fun evaluate(url: String): UrlStatus {
        if (filters.isEmpty()) return UrlStatus.ALLOWED

        val host = hostOf(url).lowercase()
        val allows = filters.filter { it.filterType == "allow" }
        val blocks  = filters.filter { it.filterType == "block" }

        val allowed = allows.any { matchPattern(host, it.urlPattern) }
        val blocked  = blocks.any { matchPattern(host, it.urlPattern) }

        return when {
            allowed -> UrlStatus.ALLOWED
            blocked -> UrlStatus.BLOCKED
            allows.isNotEmpty() -> UrlStatus.BLOCKED  // whitelist mode: not on list → blocked
            else -> UrlStatus.ALLOWED                  // blacklist-only mode: not on list → allowed
        }
    }

    /**
     * Case-insensitive pattern matching.
     * Pattern "youtube.com" matches "youtube.com" and "www.youtube.com".
     * Wildcard prefix "*.youtube.com" is also supported.
     */
    private fun matchPattern(host: String, pattern: String): Boolean {
        val p = pattern.lowercase().trimStart('*', '.')
        return host == p || host.endsWith(".$p")
    }

    private fun hostOf(url: String): String = runCatching { URI(url).host ?: url }.getOrElse { url }

    // ── Token billing ─────────────────────────────────────────────────────────

    /**
     * Deduct tokens for a domain visit.
     * One deduction per unique domain per session (first page load only).
     */
    private fun consumeTokenForDomain(url: String) {
        val domain = hostOf(url)
        if (_uiState.value.billedDomains.contains(domain)) return
        if (_uiState.value.tokensExhausted) return

        val deviceId = prefs.getString("device_id", null) ?: return
        viewModelScope.launch {
            runCatching {
                val result = repo.consumeTokens(
                    deviceId = deviceId,
                    amount = 15,
                    actionType = ActionType.WEB_SESSION.value,
                    appName = "Browser"
                )
                if (result.isSuccess) {
                    val device = repo.getDevice(deviceId)
                    _uiState.update {
                        it.copy(
                            billedDomains = it.billedDomains + domain,
                            currentTokens = device.currentTokens,
                            tokensExhausted = device.currentTokens <= 0
                        )
                    }
                }
            }.onFailure { Log.w("WebVM", "consumeTokenForDomain failed: ${it.message}") }
        }
    }

    // ── Parent approval request ───────────────────────────────────────────────

    fun requestApproval() {
        val state = _uiState.value
        val deviceId = prefs.getString("device_id", null) ?: return
        viewModelScope.launch {
            runCatching {
                repo.submitUrlAccessRequest(
                    deviceId = deviceId,
                    url = state.blockedUrl,
                    pageTitle = state.pageTitle.ifBlank { null }
                )
                _uiState.update { it.copy(requestSent = true) }
            }.onFailure { Log.e("WebVM", "requestApproval failed: ${it.message}") }
        }
    }

    // ── Page metadata ─────────────────────────────────────────────────────────

    fun onPageStarted(url: String) {
        _uiState.update { it.copy(currentUrl = url, isLoading = true) }
    }

    fun onPageFinished(url: String, title: String?) {
        _uiState.update {
            it.copy(
                currentUrl = url,
                pageTitle = title ?: "",
                isLoading = false
            )
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    companion object {
        fun factory(app: Application, initialUrl: String) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WebViewModel(app, initialUrl) as T
            }
    }
}
