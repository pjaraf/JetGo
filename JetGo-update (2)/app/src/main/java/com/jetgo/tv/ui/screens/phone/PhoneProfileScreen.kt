package com.jetgo.tv.ui.screens.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Pantalla de Perfil para el cliente: solo muestra el estado de conexión.
 * No incluye forma de cambiar de servidor ni cerrar sesión — el acceso del cliente
 * queda controlado únicamente por su código, desde el panel del administrador.
 */
@Composable
fun PhoneProfileScreen(
    onDisconnect: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0F14))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.padding(top = 48.dp, bottom = 16.dp)
        )
        Text("Mi cuenta", color = Color.White)
        Text(
            "Conectado",
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
