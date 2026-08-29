package com.jetgo.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetgo.tv.ui.theme.FocusOrange
import com.jetgo.tv.util.UpdateInfo
import com.jetgo.tv.util.UpdateInstaller
import kotlinx.coroutines.launch

@Composable
fun UpdateBanner(updateInfo: UpdateInfo, onUpdateStarted: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadProgress by remember { mutableStateOf<Int?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isDownloading = downloadProgress != null && downloadProgress!! < 100

    LaunchedEffect(Unit) {
        if (UpdateInstaller.canInstallUnknownApps(context)) {
            errorMessage = null
            downloadProgress = 0
            scope.launch {
                UpdateInstaller.downloadWithProgress(
                    context = context,
                    apkUrl = updateInfo.apkDownloadUrl,
                    onProgress = { percent -> downloadProgress = percent },
                    onInstallLaunched = { onUpdateStarted() },
                    onError = { message ->
                        errorMessage = message
                        downloadProgress = null
                    }
                )
            }
        } else {
            UpdateInstaller.requestInstallPermission(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1F2937))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Nueva versión disponible", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    if (isDownloading) "Descargando... ${downloadProgress}%"
                    else "Toca para actualizar JetGo (${updateInfo.versionLabel})",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            Button(
                enabled = !isDownloading,
                onClick = {
                    if (!UpdateInstaller.canInstallUnknownApps(context)) {
                        UpdateInstaller.requestInstallPermission(context)
                    } else {
                        errorMessage = null
                        downloadProgress = 0
                        scope.launch {
                            UpdateInstaller.downloadWithProgress(
                                context = context,
                                apkUrl = updateInfo.apkDownloadUrl,
                                onProgress = { percent -> downloadProgress = percent },
                                onInstallLaunched = { onUpdateStarted() },
                                onError = { message ->
                                    errorMessage = message
                                    downloadProgress = null
                                }
                            )
                        }
                    }
                }
            ) {
                Text(if (isDownloading) "${downloadProgress}%" else "Actualizar")
            }
        }
        if (isDownloading) {
            LinearProgressIndicator(
                progress = { downloadProgress!! / 100f },
                color = FocusOrange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
        if (errorMessage != null) {
            Text("No se pudo descargar: $errorMessage", color = Color.Red, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
