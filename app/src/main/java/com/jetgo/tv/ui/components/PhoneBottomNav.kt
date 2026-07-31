package com.jetgo.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PhoneMainTab { INICIO, TV, PERFIL }

@Composable
fun PhoneBottomNav(
    selected: PhoneMainTab,
    onSelect: (PhoneMainTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color(0xFF15171D)),
    ) {
        NavItem(
            label = "Inicio",
            icon = Icons.Default.Home,
            selected = selected == PhoneMainTab.INICIO,
            modifier = Modifier.weight(1f)
        ) { onSelect(PhoneMainTab.INICIO) }

        NavItem(
            label = "TV",
            icon = Icons.Default.LiveTv,
            selected = selected == PhoneMainTab.TV,
            modifier = Modifier.weight(1f)
        ) { onSelect(PhoneMainTab.TV) }

        NavItem(
            label = "Perfil",
            icon = Icons.Default.AccountCircle,
            selected = selected == PhoneMainTab.PERFIL,
            modifier = Modifier.weight(1f)
        ) { onSelect(PhoneMainTab.PERFIL) }
    }
}

@Composable
private fun NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val color = if (selected) Color(0xFFFF7A2E) else Color.Gray
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = color)
        Text(text = label, color = color, fontSize = 12.sp)
    }
}
