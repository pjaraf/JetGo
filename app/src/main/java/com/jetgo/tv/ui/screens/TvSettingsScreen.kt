package com.jetgo.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jetgo.tv.ui.theme.BackgroundDark
import com.jetgo.tv.ui.theme.FocusOrange
import com.jetgo.tv.ui.theme.SurfaceDark
import kotlinx.coroutines.launch

@Composable
fun TvSettingsScreen(
    settingsInfo: HomeViewModel.SettingsInfo,
    parentalState: HomeViewModel.ParentalState,
    onEnterScreen: () -> Unit,
    onEnableParental: (pin: String) -> Unit,
    onDisableParental: () -> Unit,
    onCheckPin: suspend (String) -> Boolean,
    onClearCache: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    LaunchedEffect(Unit) { onEnterScreen() }

    var showSetPinDialog by remember { mutableStateOf(false) }
    var showAskPinDialog by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var cacheMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(28.dp)
    ) {
        Text("Ajustes", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        SettingsRow("Nombre del cliente", settingsInfo.clientName ?: "Sin nombre")
        SettingsRow("Código del cliente", settingsInfo.accessCode ?: "-")
        SettingsRow("Vencimiento de la cuenta", settingsInfo.expirationDate ?: "No disponible")
        SettingsRow("Conexiones máximas", "${settingsInfo.maxDevices}")
        SettingsRow("Dispositivos conectados", "${settingsInfo.deviceCount} / ${settingsInfo.maxDevices}")

        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Control parental", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (parentalState.enabled) "Activado" else "Desactivado",
                    color = if (parentalState.enabled) FocusOrange else Color.Gray,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = parentalState.enabled,
                onCheckedChange = { turnOn ->
                    if (turnOn) {
                        showSetPinDialog = true
                    } else {
                        showAskPinDialog = { onDisableParental() }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        val clearCacheInteraction = remember { MutableInteractionSource() }
        val clearCacheFocused by clearCacheInteraction.collectIsFocusedAsState()
        Button(
            onClick = {
                onClearCache()
                cacheMessage = "Caché borrada"
            },
            interactionSource = clearCacheInteraction,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (clearCacheFocused) FocusOrange else SurfaceDark
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Borrar caché", color = Color.White)
        }
        cacheMessage?.let {
            Text(it, color = Color(0xFF4FE0B0), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        val logoutInteraction = remember { MutableInteractionSource() }
        val logoutFocused by logoutInteraction.collectIsFocusedAsState()
        OutlinedButton(
            onClick = { showLogoutConfirm = true },
            interactionSource = logoutInteraction,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (logoutFocused) FocusOrange else Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cerrar sesión", color = Color.White)
        }
    }

    if (showSetPinDialog) {
        PinDialog(
            title = "Crear PIN de control parental",
            confirmLabel = "Activar",
            onConfirm = { pin ->
                onEnableParental(pin)
                showSetPinDialog = false
            },
            onDismiss = { showSetPinDialog = false }
        )
    }

    showAskPinDialog?.let { action ->
        PinCheckDialog(
            title = "Ingresa el PIN para desactivar",
            onCheckPin = onCheckPin,
            onSuccess = {
                action()
                showAskPinDialog = null
            },
            onDismiss = { showAskPinDialog = null }
        )
    }

    if (showLogoutConfirm) {
        Dialog(onDismissRequest = { showLogoutConfirm = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Text("¿Cerrar sesión?", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Vas a tener que ingresar tu código de acceso de nuevo la próxima vez.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { showLogoutConfirm = false; onLogout() }, modifier = Modifier.weight(1f)) {
                        Text("Cerrar sesión")
                    }
                    OutlinedButton(onClick = { showLogoutConfirm = false }, modifier = Modifier.weight(1f)) {
                        Text("Cancelar")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PinDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                placeholder = { Text("4 dígitos") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = pin.length == 4,
                    onClick = { onConfirm(pin) },
                    modifier = Modifier.weight(1f)
                ) { Text(confirmLabel) }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar") }
            }
        }
    }
}

@Composable
private fun PinCheckDialog(
    title: String,
    onCheckPin: suspend (String) -> Boolean,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) { pin = it; error = false } },
                placeholder = { Text("PIN") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)
            )
            if (error) {
                Text("PIN incorrecto", color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = pin.length == 4,
                    onClick = {
                        scope.launch {
                            if (onCheckPin(pin)) onSuccess() else error = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Confirmar") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar") }
            }
        }
    }
}
