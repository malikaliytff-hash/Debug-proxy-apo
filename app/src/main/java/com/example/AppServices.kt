package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class AppServices {

    // ---------------------------------------------------------
    // VPN SERVICE
    // ---------------------------------------------------------
    class LocalVpnService : VpnService() {
        private var vpnInterface: ParcelFileDescriptor? = null
        private var mockLoggingJob: Job? = null

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            if (intent?.action == "STOP_VPN") {
                stopVpn()
                return START_NOT_STICKY
            }

            val targetApp = AppState.selectedApp.value
            if (targetApp == null) {
                stopSelf()
                return START_NOT_STICKY
            }

            // VpnService handles its own lifecycle and system priority when established.
            // We just call establish.
            startVpn(targetApp.packageName)

            return START_STICKY
        }

        private fun startVpn(packageName: String) {
            try {
                val builder = Builder()
                builder.setSession("DebugProxy")
                builder.addAddress("10.0.0.2", 24)
                builder.addRoute("0.0.0.0", 0)
                builder.addDnsServer("8.8.8.8")
                builder.setMtu(1500)
                
                if (packageName != this.packageName) {
                    builder.addAllowedApplication(packageName)
                }
                
                vpnInterface = builder.establish()
                if (vpnInterface != null) {
                    AppState.setVpnRunning(true)
                    startRealPacketCapture()
                } else {
                    stopVpn()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(this, "Failed to start VPN: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                stopVpn()
            }
        }

        private fun startRealPacketCapture() {
            val fd = vpnInterface?.fileDescriptor ?: return
            mockLoggingJob = CoroutineScope(Dispatchers.IO).launch {
                val inputStream = java.io.FileInputStream(fd)
                val buffer = ByteArray(32767)
                while(isActive) {
                    try {
                        val length = inputStream.read(buffer)
                        if (length > 0) {
                            val versionAndIHL = buffer[0].toInt()
                            // Check if it's IPv4 (version 4)
                            if ((versionAndIHL shr 4) == 4 && length >= 20) {
                                val protocol = buffer[9].toInt()
                                val srcIp = "${buffer[12].toUByte()}.${buffer[13].toUByte()}.${buffer[14].toUByte()}.${buffer[15].toUByte()}"
                                val dstIp = "${buffer[16].toUByte()}.${buffer[17].toUByte()}.${buffer[18].toUByte()}.${buffer[19].toUByte()}"
                                
                                val protoName = when(protocol) {
                                    1 -> "ICMP"
                                    6 -> "TCP"
                                    17 -> "UDP"
                                    else -> "Proto $protocol"
                                }
                                
                                AppState.addNetworkLog(
                                    NetworkLog(
                                        method = protoName,
                                        url = "$srcIp -> $dstIp",
                                        statusCode = length, // show size instead
                                        responseSize = length.toLong(),
                                        headers = mapOf("Type" to "IPv4", "Traffic" to protoName),
                                        timestamp = System.currentTimeMillis(),
                                        bodyReplaced = false
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        delay(1000)
                    }
                }
            }
        }

        private fun startMockLogging() {
            mockLoggingJob = CoroutineScope(Dispatchers.IO).launch {
                val methods = listOf("GET", "POST", "PUT", "DELETE")
                val endpoints = listOf("api/v1/users", "api/login", "analytics/track", "images/profile.png")
                while(isActive) {
                    delay((2000..5000).random().toLong())
                    val method = methods.random()
                    val ep = endpoints.random()
                    val url = "https://api.targetapp.internal/$ep"
                    
                    val activeRules = AppState.mockRules.value
                    val matchedRule = activeRules.firstOrNull { url.contains(it.urlContains, ignoreCase = true) }
                    
                    val isReplaced = matchedRule != null
                    val statusCode = matchedRule?.mockStatusCode ?: listOf(200, 201, 400, 404, 500).random()
                    val responseSize = if (isReplaced) matchedRule!!.mockBody.length.toLong() else (100..5000).random().toLong()

                    AppState.addNetworkLog(
                        NetworkLog(
                            method = method,
                            url = url,
                            statusCode = statusCode,
                            responseSize = responseSize,
                            headers = mapOf(
                                "Content-Type" to "application/json",
                                "Authorization" to "Bearer stub_token",
                                "User-Agent" to "AndroidApp/1.0"
                            ),
                            timestamp = System.currentTimeMillis(),
                            bodyReplaced = isReplaced
                        )
                    )
                }
            }
        }

        private fun stopVpn() {
            mockLoggingJob?.cancel()
            vpnInterface?.close()
            vpnInterface = null
            AppState.setVpnRunning(false)
            stopSelf()
        }

        override fun onDestroy() {
            super.onDestroy()
            stopVpn()
        }
    }


    // ---------------------------------------------------------
    // OVERLAY SERVICE
    // ---------------------------------------------------------
    class OverlayService : android.app.Service() {
        private var windowManager: WindowManager? = null
        private var composeView: View? = null
        private var layoutParams: WindowManager.LayoutParams? = null

        override fun onBind(intent: Intent?): IBinder? = null

        override fun onCreate() {
            super.onCreate()
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            AppState.setOverlayRunning(true)
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            if (intent?.action == "STOP_OVERLAY") {
                stopSelf()
                return START_NOT_STICKY
            }

            startForegroundService()
            setupOverlay()
            updateWindowFlags()

            return START_STICKY
        }

        private fun startForegroundService() {
            val channelId = "overlay_channel"
            val channel = NotificationChannel(
                channelId, "Visual Overlay", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

            val stopIntent = Intent(this, OverlayService::class.java).apply { action = "STOP_OVERLAY" }
            val pendingStop = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Visual Overlay Active")
                .setContentText("Tap in app to manage overlays")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(2, notification)
            }
        }

        private fun setupOverlay() {
            if (composeView != null) return
            
            if (!android.provider.Settings.canDrawOverlays(this)) {
                android.widget.Toast.makeText(this, "Overlay permission not granted", android.widget.Toast.LENGTH_SHORT).show()
                stopSelf()
                return
            }

            val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }

            composeView = ComposeView(this).apply {
                val lifecycleOwner = MyLifecycleOwner()
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                lifecycleOwner.start()

                setContent {
                    OverlayContent()
                }
            }

            windowManager?.addView(composeView, layoutParams)
        }

        private class MyLifecycleOwner : androidx.lifecycle.LifecycleOwner, androidx.lifecycle.ViewModelStoreOwner, androidx.savedstate.SavedStateRegistryOwner {
            private val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this)
            private val savedStateRegistryController = androidx.savedstate.SavedStateRegistryController.create(this)
            private val store = androidx.lifecycle.ViewModelStore()

            override val lifecycle: androidx.lifecycle.Lifecycle get() = lifecycleRegistry
            override val savedStateRegistry: androidx.savedstate.SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
            override val viewModelStore: androidx.lifecycle.ViewModelStore get() = store

            fun start() {
                savedStateRegistryController.performAttach()
                savedStateRegistryController.performRestore(null)
                lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_CREATE)
                lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
                lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
            }

            fun stop() {
                lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_PAUSE)
                lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_STOP)
                lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_DESTROY)
                store.clear()
            }
        }

        private fun updateWindowFlags() {
            layoutParams?.let {
                it.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                           WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                           WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                
                if (composeView != null && composeView?.isAttachedToWindow == true) {
                    windowManager?.updateViewLayout(composeView, it)
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            AppState.setOverlayRunning(false)
            if (composeView != null) {
                windowManager?.removeView(composeView)
                composeView = null
            }
        }
    }
}

@Composable
fun OverlayContent() {
        val images by AppState.overlayImages.collectAsState()
        val customText by AppState.customOverlayText.collectAsState()
        val customTextColorRaw by AppState.customOverlayTextColor.collectAsState()
        
        val selectedApp by AppState.selectedApp.collectAsState()
        val currentForegroundApp by AppState.currentForegroundAppPackage.collectAsState()
        val isOurAppForeground by AppState.isOurAppForeground.collectAsState()

        // Only show if the foreground app matches the selected app
        // BUT if no app is selected, do we show it everywhere? 
        // Let's show it ONLY when target app is active, 
        // OR when our app is open (so they can see what it looks like before testing) 
        // Oh wait, the user said "only show when I open the target app that I selected".
        // Let's hide it in our app as well if they chose "only when I open target app".
        val shouldShow = selectedApp != null && currentForegroundApp == selectedApp?.packageName
        
        if (!shouldShow) return

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            if (customText.isNotEmpty()) {
                androidx.compose.material3.Text(
                    text = customText,
                    color = Color(customTextColorRaw),
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier
                        .padding(16.dp)
                        .background(Color.White.copy(alpha = 0.7f))
                        .padding(8.dp)
                        .offset(y = 24.dp) // shift down a bit just in case of status bar
                )
            }

            images.sortedBy { it.zIndex }.forEach { image ->
                val currentOffset = image.offset
                val currentScale = image.scale
                val currentRotation = image.rotation

                Box(
                    modifier = Modifier
                        .offset { IntOffset(currentOffset.x.roundToInt(), currentOffset.y.roundToInt()) }
                        .graphicsLayer(
                            scaleX = currentScale,
                            scaleY = currentScale,
                            rotationZ = currentRotation,
                            alpha = image.alpha
                        )
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = image.uriString),
                        contentDescription = "User defined overlay",
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
