package com.jetgo.tv.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetgo.tv.data.model.ServerConfig

@Composable
fun NetflixLoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    deviceLimitReached: Boolean = false,
    registeredDevices: List<com.jetgo.tv.util.RegisteredDevice> = emptyList(),
    onLogin: (String) -> Unit,
    onRemoveDevice: (String) -> Unit = {},
    onDismissLimit: () -> Unit = {}
) {
    var accessCode by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0C))
    ) {
        // Fondo cinemático estilo collage de posters de Netflix (como la imagen de referencia)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cols = 6
            val rows = 5
            val cellWidth = size.width / cols * 1.3f
            val cellHeight = size.height / rows * 1.3f

            rotate(degrees = -15f, pivot = Offset(size.width / 2f, size.height / 2f)) {
                for (r in 0..rows) {
                    for (c in 0..cols) {
                        val x = (c - 1) * cellWidth
                        val y = (r - 1) * cellHeight
                        
                        val colorIndex = (r * 3 + c * 7) % 5
                        val posterColor = when (colorIndex) {
                            0 -> Color(0xFF1A1A24)
                            1 -> Color(0xFF261215) // Tono rojizo Netflix
                            2 -> Color(0xFF14202B) // Tono azulado cinemático
                            3 -> Color(0xFF1F1B18) // Tono cálido/dorado
                            else -> Color(0xFF0F0F12)
                        }

                        drawRect(
                            color = posterColor,
                            topLeft = Offset(x, y),
                            size = Size(cellWidth - 8f, cellHeight - 8f)
                        )
                    }
                }
            }

            // Capa de oscurecimiento (vignette / gradiente oscuro) superpuesta para legibilidad
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.75f),
                        Color.Black.copy(alpha = 0.55f),
                        Color.Black.copy(alpha = 0.85f)
                    )
                )
            )
        }

        // Tarjeta central de inicio de sesión con recuadro transparente
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.35f) // Recuadro transparente
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "JetGo",
                        color = Color(0xFFE50914), // Rojo Netflix
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Inicia sesión con tu código",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                    )

                    OutlinedTextField(
                        value = accessCode,
                        onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) accessCode = it },
                        label = { Text("Código de 6 dígitos", color = Color.White.copy(alpha = 0.7f)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Black.copy(alpha = 0.25f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.15f),
                            disabledContainerColor = Color.Transparent,
                            errorContainerColor = Color.Transparent,
                            focusedBorderColor = Color(0xFFE50914),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedLabelColor = Color(0xFFE50914),
                            unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = { onLogin(accessCode.trim()) },
                        enabled = !isLoading && accessCode.length == 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE50914),
                            disabledContainerColor = Color(0xFFE50914).copy(alpha = 0.4f)
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                text = "Iniciar sesión",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    errorMessage?.let {
                        Text(
                            text = it,
                            color = Color(0xFFE50914),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    var showSubscribeDialog by remember { mutableStateOf(false) }
                    var isSubCardFocused by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState -> isSubCardFocused = focusState.isFocused }
                            .focusable()
                            .clickable { showSubscribeDialog = true },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSubCardFocused) Color(0xFFE50914).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f)
                        ),
                        border = BorderStroke(
                            width = if (isSubCardFocused) 2.dp else 1.dp,
                            color = if (isSubCardFocused) Color.White else Color(0xFFE50914).copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✨ ¿Aún no tienes JetGo? ¡Suscríbete aquí! 🚀",
                                color = if (isSubCardFocused) Color.White else Color(0xFFFFB703),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (showSubscribeDialog) {
                        androidx.compose.ui.window.Dialog(onDismissRequest = { showSubscribeDialog = false }) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 460.dp)
                                    .background(Color(0xFF141419), RoundedCornerShape(16.dp))
                                    .border(2.dp, Color(0xFFE50914), RoundedCornerShape(16.dp))
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "📺 Suscripción JetGo",
                                    color = Color(0xFFE50914),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "¡Obtén tu acceso inmediato y disfruta de todo el entretenimiento sin límites!",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 14.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "📲 Comunícate vía WhatsApp o llamada:",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "+56 9 5939 6963",
                                            color = Color(0xFF4ADE80),
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                var isDialogBtnFocused by remember { mutableStateOf(false) }
                                Button(
                                    onClick = { showSubscribeDialog = false },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .onFocusChanged { focusState -> isDialogBtnFocused = focusState.isFocused }
                                        .focusable(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDialogBtnFocused) Color.White else Color(0xFFE50914)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Entendido",
                                        color = if (isDialogBtnFocused) Color(0xFFE50914) else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (deviceLimitReached) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { if (!isLoading) onDismissLimit() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .background(Color(0xFF141419), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Text(
                    text = "Límite de dispositivos (3/3)",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ya tienes activos los 3 dispositivos permitidos en tu cuenta. Para conectar este nuevo equipo, selecciona y elimina uno de los dispositivos registrados:",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = Color(0xFFE50914), modifier = Modifier.size(36.dp))
                            Text(
                                text = "Eliminando dispositivo y conectando...",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp
                            )
                        }
                    }
                } else if (registeredDevices.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFE50914))
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        registeredDevices.forEach { device ->
                            var isFocused by remember { mutableStateOf(false) }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focusState -> isFocused = focusState.isFocused }
                                    .focusable()
                                    .onKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                            if (!isLoading) {
                                                onRemoveDevice(device.deviceId)
                                                true
                                            } else false
                                        } else false
                                    }
                                    .clickable(enabled = !isLoading) { onRemoveDevice(device.deviceId) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isFocused) Color(0xFFE50914).copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.6f)
                                ),
                                border = BorderStroke(
                                    width = if (isFocused) 2.dp else 1.dp,
                                    color = if (isFocused) Color.White else Color(0xFFE50914).copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = device.deviceName,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "ID: ${device.deviceId}",
                                            color = Color.White.copy(alpha = if (isFocused) 0.9f else 0.5f),
                                            fontSize = 12.sp
                                        )
                                    }
                                    Button(
                                        onClick = { if (!isLoading) onRemoveDevice(device.deviceId) },
                                        enabled = !isLoading,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isFocused) Color.White else Color(0xFFE50914),
                                            disabledContainerColor = Color(0xFFE50914).copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "Eliminar",
                                            color = if (isFocused) Color(0xFFE50914) else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (!errorMessage.isNullOrBlank() && !isLoading) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFF5252),
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                var isCancelFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = onDismissLimit,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState -> isCancelFocused = focusState.isFocused }
                        .focusable(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCancelFocused) Color(0xFF444444) else Color.DarkGray
                    ),
                    border = if (isCancelFocused) BorderStroke(2.dp, Color.White) else null,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancelar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

