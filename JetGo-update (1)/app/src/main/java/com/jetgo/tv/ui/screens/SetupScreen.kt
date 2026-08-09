package com.jetgo.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jetgo.tv.data.model.ServerConfig

@Composable
fun SetupScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onConnectXtream: (ServerConfig) -> Unit,
    onConnectM3u: (String) -> Unit
) {
    var host by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Conecta tu servicio", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))

        OutlinedTextField(
            value = host, onValueChange = { host = it },
            label = { Text("Host (ej: http://servidor.com:8080)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = user, onValueChange = { user = it },
            label = { Text("Usuario") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = pass, onValueChange = { pass = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Button(
            onClick = { onConnectXtream(ServerConfig(host, user, pass)) },
            enabled = !isLoading && host.isNotBlank() && user.isNotBlank() && pass.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text(if (isLoading) "Conectando..." else "Conectar con Xtream Codes")
        }

        Divider(modifier = Modifier.padding(vertical = 24.dp))

        Text("O usa una lista M3U directa")
        OutlinedTextField(
            value = m3uUrl, onValueChange = { m3uUrl = it },
            label = { Text("URL de la lista M3U") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Button(
            onClick = { onConnectM3u(m3uUrl) },
            enabled = !isLoading && m3uUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text("Cargar lista M3U")
        }

        errorMessage?.let {
            Text(it, color = androidx.compose.ui.graphics.Color.Red, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
