package com.jetgo.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetgo.tv.util.UpdateInfo
import com.jetgo.tv.util.UpdateInstaller

@Composable
fun UpdateBanner(updateInfo: UpdateInfo, onUpdateStarted: () -> Unit = {}) {
    val context = LocalContext.current
    var isDownloading by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1F2937))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Nueva versión disponible", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Toca para actualizar JetGo (${updateInfo.versionLabel})", color = Color.Gray, fontSize = 12.sp)
        }
        Button(
            enabled = !isDownloading,
            onClick = {
                if (!UpdateInstaller.canInstallUnknownApps(context)) {
                    UpdateInstaller.requestInstallPermission(context)
                } else {
                    isDownloading = true
                    onUpdateStarted() // el aviso no debe volver a salir después de esto
                    UpdateInstaller.downloadAndInstall(context, updateInfo.apkDownloadUrl) {
                        isDownloading = false
                    }
                }
            }
        ) {
            Text(if (isDownloading) "Descargando..." else "Actualizar")
        }
    }
}
