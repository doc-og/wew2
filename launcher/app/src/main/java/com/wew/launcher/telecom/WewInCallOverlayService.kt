package com.wew.launcher.telecom

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.wew.launcher.ui.screen.WewInCallOverlay
import com.wew.launcher.ui.theme.WewLauncherTheme

/**
 * Foreground-adjacent service that shows [WewInCallOverlay] via [WindowManager]
 * TYPE_APPLICATION_OVERLAY so it floats over the system dialer during a call.
 */
class WewInCallOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private var overlayView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        if (overlayView == null) attachOverlay()
        return START_NOT_STICKY
    }

    private fun attachOverlay() {
        val wm = getSystemService(WindowManager::class.java) ?: return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@WewInCallOverlayService)
            setViewTreeViewModelStoreOwner(this@WewInCallOverlayService)
            setViewTreeSavedStateRegistryOwner(this@WewInCallOverlayService)
            setContent {
                WewLauncherTheme {
                    WewInCallOverlay()
                }
            }
        }
        wm.addView(view, params)
        overlayView = view
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        overlayView?.let { runCatching { getSystemService(WindowManager::class.java)?.removeView(it) } }
        overlayView = null
        store.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
