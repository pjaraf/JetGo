package com.jetgo.tv.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
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
    onLogin: (ServerConfig) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

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
                        text = "Inicia sesión con tu cuenta",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 28.dp)
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Usuario", color = Color.White.copy(alpha = 0.7f)) },
                        singleLine = true,
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

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña", color = Color.White.copy(alpha = 0.7f)) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                            }
                        },
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
                        onClick = { onLogin(ServerConfig("http://redworld.pro:8880", username.trim(), password.trim())) },
                        enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
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
                }
            }
        }
    }
}

