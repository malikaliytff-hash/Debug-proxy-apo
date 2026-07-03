package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.rememberAsyncImagePainter

@Composable
fun AppSelectionScreen(
    context: Context,
    onAppSelected: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var installedApps by remember { mutableStateOf<List<TargetApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val selectedApp by AppState.selectedApp.collectAsState()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val pm = context.packageManager
            val apps = withContext(Dispatchers.IO) {
                // Get all apps that can be launched
                val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
                pm.queryIntentActivities(intent, 0).mapNotNull {
                    try {
                        val packageName = it.activityInfo.packageName
                        val name = it.loadLabel(pm).toString()
                        val icon = it.loadIcon(pm)
                        TargetApp(packageName, name, icon)
                    } catch (e: Exception) { null }
                }.sortedBy { it.name }
            }
            installedApps = apps
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Select Authorized App",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )
        
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Ethical Use Policy: You must only select applications that you own or have explicit permission from the developer to test. The VPN securely isolates traffic strictly to your selection.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(installedApps) { app ->
                    val isSelected = app.packageName == selectedApp?.packageName
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = { Text(app.packageName) },
                        leadingContent = {
                            app.icon?.let {
                                Image(
                                    painter = rememberAsyncImagePainter(it),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        },
                        trailingContent = {
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        ),
                        modifier = Modifier.clickable {
                            AppState.setSelectedApp(app)
                            onAppSelected()
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyDashboardScreen(
    context: Context,
    onRequireVpnPermission: (Intent) -> Unit
) {
    val selectedApp by AppState.selectedApp.collectAsState()
    val isVpnRunning by AppState.isVpnRunning.collectAsState()
    val rawLogs by AppState.networkLogs.collectAsState()

    var showConfigHelp by remember { mutableStateOf(false) }
    var showMockManager by remember { mutableStateOf(false) }

    if (selectedApp == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Please select an app first.")
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Status Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isVpnRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    selectedApp?.icon?.let {
                        Image(
                            painter = rememberAsyncImagePainter(it),
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = selectedApp!!.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(text = selectedApp!!.packageName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isVpnRunning) "PROXY ACTIVE" else "PROXY PAUSED",
                        fontWeight = FontWeight.Black,
                        color = if (isVpnRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    
                    Button(onClick = {
                        if (isVpnRunning) {
                            val intent = Intent(context, AppServices.LocalVpnService::class.java)
                            intent.action = "STOP_VPN"
                            context.startService(intent)
                        } else {
                            try {
                                val intent = VpnService.prepare(context)
                                if (intent != null) {
                                    onRequireVpnPermission(intent)
                                } else {
                                    val startIntent = Intent(context, AppServices.LocalVpnService::class.java)
                                    context.startService(startIntent)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                val startIntent = Intent(context, AppServices.LocalVpnService::class.java)
                                context.startService(startIntent)
                            }
                        }
                    }) {
                        Text(if (isVpnRunning) "Pause Proxy" else "Start Proxy")
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Network Traffic", fontWeight = FontWeight.SemiBold)
            Row {
                IconButton(onClick = { showConfigHelp = true }) {
                    Icon(Icons.Default.Info, "Configuration Info")
                }
                IconButton(onClick = { AppState.clearLogs() }) {
                    Icon(Icons.Default.Delete, "Clear Logs")
                }
            }
        }

        // Live Log Output
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            if (rawLogs.isEmpty()) {
                Text(
                    "Waiting for network activity...",
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(rawLogs) { log ->
                        val color = when {
                            log.bodyReplaced -> Color(0xFFE91E63)
                            log.statusCode >= 400 -> Color(0xFFF44336)
                            else -> Color(0xFF4CAF50)
                        }
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "[${log.method}] ${log.statusCode}",
                                    color = color,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if(log.bodyReplaced) "REPLACED MOCK" else "${log.responseSize} bytes",
                                    color = Color.Yellow,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = log.url,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }

    if (showConfigHelp) {
        AlertDialog(
            onDismissRequest = { showConfigHelp = false },
            title = { Text("HTTPS Interception Constraints") },
            text = {
                Text("Because Android versions 7+ enforce strict Network Security Configs by default, applications ignore user-installed root CA certificates.\n\n" +
                     "For this tool's deep packet inspection to decrypt TLS traffic on your authorized app, the app must include:\n\n" +
                     "<network-security-config>\n" +
                     "  <debug-overrides>\n" +
                     "    <trust-anchors>\n" +
                     "      <certificates src=\"user\"/>\n" +
                     "    </trust-anchors>\n" +
                     "  </debug-overrides>\n" +
                     "</network-security-config>\n\n" +
                     "A full local TLS MITM engine requires heavy C++ dependencies. This prototype captures raw IP flow architecture. To decode complete HTTP streams, use with tun2socks.")
            },
            confirmButton = { TextButton(onClick = { showConfigHelp = false }) { Text("Got it") } }
        )
    }
}

@Composable
fun MockManagerDialog(onDismiss: () -> Unit) {
    val rules by AppState.mockRules.collectAsState()
    var urlContains by remember { mutableStateOf("") }
    var mockBody by remember { mutableStateOf("") }
    var mockStatusCode by remember { mutableStateOf("200") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Response Mocks") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 200.dp)) {
                    items(rules) { rule ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(rule.urlContains, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Returns ${rule.mockStatusCode}", fontSize = 12.sp)
                            }
                            IconButton(onClick = { AppState.removeMockRule(rule.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                OutlinedTextField(
                    value = urlContains,
                    onValueChange = { urlContains = it },
                    label = { Text("URL Contains") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mockStatusCode,
                        onValueChange = { mockStatusCode = it },
                        label = { Text("Status") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = mockBody,
                    onValueChange = { mockBody = it },
                    label = { Text("Mock JSON Body") },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
                Button(
                    onClick = {
                        if (urlContains.isNotBlank()) {
                            AppState.addMockRule(MockRule(urlContains = urlContains, mockBody = mockBody, mockStatusCode = mockStatusCode.toIntOrNull() ?: 200))
                            urlContains = ""
                            mockBody = ""
                            mockStatusCode = "200"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Add Rule")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun OverlayManagerScreen(context: Context) {
    val images by AppState.overlayImages.collectAsState()
    val customText by AppState.customOverlayText.collectAsState()
    var textInput by remember { mutableStateOf(customText) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            // In a real app we'd take persistable permission or copy to cache.
            AppState.addOverlayImage(it.toString()) 
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            val intent = Intent(context, AppServices.OverlayService::class.java)
            context.startService(intent)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Visual Overlay Testing", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Position reference images over your app for pixel-perfect UI verification.", style = MaterialTheme.typography.bodyMedium)
        
        val isOverlayRunning by AppState.isOverlayRunning.collectAsState()
        
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("App Tracker Service", fontWeight = FontWeight.Bold)
                Text("To only show the overlay over your selected app, please enable the Accessibility Service for this app in your device Settings.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }) {
                    Text("Enable Tracker")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), 
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Overlay Mode", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = isOverlayRunning,
                onCheckedChange = { checked ->
                    if (checked) {
                        if (Settings.canDrawOverlays(context)) {
                            context.startService(Intent(context, AppServices.OverlayService::class.java))
                        } else {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            permissionLauncher.launch(intent)
                        }
                    } else {
                        context.stopService(Intent(context, AppServices.OverlayService::class.java))
                    }
                }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { 
                    textInput = it
                    AppState.setCustomOverlayText(it)
                },
                label = { Text("Custom Overlay Text") },
                modifier = Modifier.weight(1f)
            )
        }
        Text("Text Color:", modifier = Modifier.padding(top = 8.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Black, Color.White, Color.Magenta)
            colors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(color, shape = androidx.compose.foundation.shape.CircleShape)
                        .clickable { AppState.setCustomOverlayTextColor(color.toArgb()) }
                        .border(1.dp, Color.Gray, androidx.compose.foundation.shape.CircleShape)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Active Overlays (${images.size})", fontWeight = FontWeight.SemiBold)
            Button(onClick = { launcher.launch("image/*") }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Image")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(images) { image ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(image.uriString),
                            contentDescription = null,
                            modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Opacity: ${(image.alpha * 100).toInt()}%", fontSize = 14.sp)
                            Slider(
                                value = image.alpha,
                                onValueChange = { AppState.updateOverlayImage(image.copy(alpha = it)) },
                                valueRange = 0.1f..1f
                            )
                        }
                        Column {
                            IconButton(onClick = { AppState.duplicateOverlayImage(image) }) {
                                Icon(Icons.Default.Add, contentDescription = "Duplicate") // Stub icon for dupe
                            }
                            IconButton(onClick = { AppState.removeOverlayImage(image.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
