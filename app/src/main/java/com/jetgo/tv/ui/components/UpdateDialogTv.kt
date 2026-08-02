package com.jetgo.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jetgo.tv.ui.theme.SurfaceDark
import com.jetgo.tv.util.UpdateInfo
import com.jetgo.tv.util.UpdateInstaller

/**
 * Versión para TV del aviso de actualización: en vez de la barra que aparece arriba en
 * teléfonos, en TV se muestra como una ventana centrada (más fácil de ver/navegar con
 * el control remoto).
 */
@Composable
fun UpdateDialogTv(updateInfo: UpdateInfo, onDismiss: () -> Unit, onUpdateStarted: () -> Unit = {}) {
    val context = LocalContext.current
    var isDownloading by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .background(SurfaceDark, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Text("Nueva versión disponible", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "JetGo (${updateInfo.versionLabel}) ya está lista para instalar.",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    enabled = !isDownloading,
                    modifier = Modifier.weight(1f),
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
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Más tarde")
                }
            }
        }
    }
}
