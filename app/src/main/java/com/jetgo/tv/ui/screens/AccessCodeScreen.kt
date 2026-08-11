package com.jetgo.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetgo.tv.ui.components.SpaceBackground
import com.jetgo.tv.ui.theme.BackgroundDark
import com.jetgo.tv.ui.theme.FocusOrange
import com.jetgo.tv.ui.theme.SurfaceDark

@Composable
fun AccessCodeScreen(
    isChecking: Boolean,
    errorMessage: String?,
    onSubmitCode: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }

    // Apenas se completan los 6 dígitos, se envía solo (sin botón)
    LaunchedEffect(code) {
        if (code.length == 6) {
            onSubmitCode(code)
        }
    }
    // Si el código falló, se limpia para que puedas volver a escribirlo
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) code = ""
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SpaceBackground(modifier = Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize()) {

        // ---- Cuerpo centrado: código + teclado numérico ----
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Código de acceso",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Ingresa el código de 6 dígitos que te compartieron",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 26.dp)
            )

            // ---- Casillas del código ----
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 0 until 6) {
                    val digit = code.getOrNull(i)?.toString() ?: ""
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceDark)
                            .border(
                                width = 2.dp,
                                color = if (i == code.length) FocusOrange else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(digit, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (isChecking) {
                CircularProgressIndicator(color = FocusOrange)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Verificando...", color = Color.Gray, fontSize = 13.sp)
            } else {
                // ---- Campo de texto, igual estilo que el PIN de control parental ----
                androidx.compose.material3.OutlinedTextField(
                    value = code,
                    onValueChange = { input ->
                        if (input.length <= 6 && input.all { it.isDigit() }) code = input
                    },
                    placeholder = { Text("Código de 6 dígitos") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                    ),
                    singleLine = true,
                    modifier = Modifier.width(260.dp)
                )
            }

            errorMessage?.let {
                Text(
                    text = it,
                    color = Color.Red,
                    modifier = Modifier.padding(top = 18.dp)
                )
            }
        }
        }
    }
}
