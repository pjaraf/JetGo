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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jetgo.tv.ui.theme.FocusOrange
import com.jetgo.tv.ui.theme.SurfaceDark
import com.jetgo.tv.util.UpdateInfo
import com.jetgo.tv.util.UpdateInstaller
import kotlinx.coroutines.launch

/**
 * Versión para TV del aviso de actualización: en vez de la barra que aparece arriba en
 * teléfonos, en TV se muestra como una ventana centrada (más fácil de ver/navegar con
 * el control remoto). Muestra el progreso de descarga real (no depende de las notificaciones
 * del sistema, que en muchos TV Box no se ven ni se pueden tocar).
 */
@Composable
fun UpdateDialogTv(updateInfo: UpdateInfo, onDismiss: () -> Unit, onUpdateStarted: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadProgress by remember { mutableStateOf<Int?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isDownloading = downloadProgress != null && downloadProgress!! < 100

    Dialog(onDismissRequest = { if (!isDownloading) onDismiss() }) {
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
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )

            if (downloadProgress != null) {
                LinearProgressIndicator(
                    progress = { downloadProgress!! / 100f },
                    color = FocusOrange,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                Text(
                    text = "Descargando... ${downloadProgress}%",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            if (errorMessage != null) {
                Text(
                    text = "No se pudo descargar: $errorMessage",
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

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
                            errorMessage = null
                            downloadProgress = 0
                            scope.launch {
                                UpdateInstaller.downloadWithProgress(
                                    context = context,
                                    apkUrl = updateInfo.apkDownloadUrl,
                                    onProgress = { percent ->
                                        downloadProgress = percent
                                        if (percent >= 100) {
                                            // Recién ahora (terminó de verdad) se oculta el aviso
                                            onUpdateStarted()
                                        }
                                    },
                                    onError = { message ->
                                        errorMessage = message
                                        downloadProgress = null
                                    }
                                )
                            }
                        }
                    }
                ) {
                    Text(
                        when {
                            isDownloading -> "Descargando... ${downloadProgress}%"
                            downloadProgress == 100 -> "Instalando..."
                            else -> "Actualizar"
                        }
                    )
                }
                if (!isDownloading) {
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
}
