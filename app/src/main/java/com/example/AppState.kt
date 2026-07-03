package com.example

import android.graphics.drawable.Drawable
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

// Data Models
data class TargetApp(
    val packageName: String,
    val name: String,
    val icon: Drawable? = null
)

data class NetworkLog(
    val id: String = UUID.randomUUID().toString(),
    val method: String,
    val url: String,
    val statusCode: Int,
    val responseSize: Long,
    val headers: Map<String, String>,
    val timestamp: Long,
    val bodyReplaced: Boolean = false
)

data class OverlayImage(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    var offset: Offset = Offset.Zero,
    var scale: Float = 1f,
    var rotation: Float = 0f,
    var alpha: Float = 0.5f,
    var zIndex: Float = 0f
)

data class MockRule(
    val id: String = UUID.randomUUID().toString(),
    val urlContains: String,
    val mockBody: String,
    val mockStatusCode: Int
)

// Global State Store for cross-component access (singleton for simplicity in this project scope)
object AppState {
    
    private val _mockRules = MutableStateFlow<List<MockRule>>(emptyList())
    val mockRules: StateFlow<List<MockRule>> = _mockRules.asStateFlow()

    private val _selectedApp = MutableStateFlow<TargetApp?>(null)
    val selectedApp: StateFlow<TargetApp?> = _selectedApp.asStateFlow()

    private val _isVpnRunning = MutableStateFlow(false)
    val isVpnRunning: StateFlow<Boolean> = _isVpnRunning.asStateFlow()

    private val _networkLogs = MutableStateFlow<List<NetworkLog>>(emptyList())
    val networkLogs: StateFlow<List<NetworkLog>> = _networkLogs.asStateFlow()

    private val _overlayImages = MutableStateFlow<List<OverlayImage>>(emptyList())
    val overlayImages: StateFlow<List<OverlayImage>> = _overlayImages.asStateFlow()

    private val _isOurAppForeground = MutableStateFlow(true)
    val isOurAppForeground: StateFlow<Boolean> = _isOurAppForeground.asStateFlow()

    private val _currentForegroundAppPackage = MutableStateFlow<String?>(null)
    val currentForegroundAppPackage: StateFlow<String?> = _currentForegroundAppPackage.asStateFlow()

    private val _isOverlayRunning = MutableStateFlow(false)
    val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

    private val _customOverlayText = MutableStateFlow("")
    val customOverlayText: StateFlow<String> = _customOverlayText.asStateFlow()

    private val _customOverlayTextColor = MutableStateFlow(0xFFFF0000.toInt()) // Red by default
    val customOverlayTextColor: StateFlow<Int> = _customOverlayTextColor.asStateFlow()

    fun setSelectedApp(app: TargetApp?) { _selectedApp.value = app }
    fun setVpnRunning(running: Boolean) { _isVpnRunning.value = running }
    
    fun setCustomOverlayText(text: String) { _customOverlayText.value = text }
    fun setCustomOverlayTextColor(color: Int) { _customOverlayTextColor.value = color }
    fun setOverlayRunning(running: Boolean) { _isOverlayRunning.value = running }
    fun setOurAppForeground(foreground: Boolean) { _isOurAppForeground.value = foreground }
    fun setCurrentForegroundApp(packageName: String?) { _currentForegroundAppPackage.value = packageName }
    
    fun clearLogs() { _networkLogs.value = emptyList() }
    
    fun addMockRule(rule: MockRule) {
        _mockRules.update { current -> current + rule }
    }
    
    fun removeMockRule(id: String) {
        _mockRules.update { current -> current.filter { it.id != id } }
    }
    
    fun addNetworkLog(log: NetworkLog) {
        _networkLogs.update { current -> 
            (listOf(log) + current).take(200) // Keep latest 200 logs
        }
    }

    fun addOverlayImage(uri: String) {
        _overlayImages.update { current ->
            val maxZ = current.maxOfOrNull { it.zIndex } ?: 0f
            current + OverlayImage(uriString = uri, zIndex = maxZ + 1f)
        }
    }

    fun updateOverlayImage(image: OverlayImage) {
        _overlayImages.update { current ->
            current.map { if (it.id == image.id) image else it }
        }
    }

    fun removeOverlayImage(id: String) {
        _overlayImages.update { current ->
            current.filter { it.id != id }
        }
    }
    
    fun duplicateOverlayImage(image: OverlayImage) {
        _overlayImages.update { current ->
            val maxZ = current.maxOfOrNull { it.zIndex } ?: 0f
            current + image.copy(
                id = UUID.randomUUID().toString(),
                offset = image.offset + Offset(50f, 50f),
                zIndex = maxZ + 1f
            )
        }
    }
}
