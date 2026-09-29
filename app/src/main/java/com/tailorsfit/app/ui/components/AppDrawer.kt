package com.tailorsfit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tailorsfit.app.ui.theme.Brand

enum class DrawerDestination(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    DESIGNS("Blouse designs", Icons.Filled.Star),
    CUSTOMERS("Customers", Icons.Filled.Person),
    GUIDE("How to measure", Icons.Filled.Edit),
    ABOUT("About", Icons.Filled.Info),
}

@Composable
fun AppDrawerSheet(selected: DrawerDestination?, onSelect: (DrawerDestination) -> Unit) {
    ModalDrawerSheet(drawerContainerColor = Brand.Ivory) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Brand.Plum, Brand.Aubergine, Brand.AubergineDeep)))
                .statusBarsPadding()
                .padding(24.dp),
        ) {
            Text("TAILORS FIT", style = MaterialTheme.typography.labelSmall, color = Brand.GoldLight)
            Spacer(Modifier.height(8.dp))
            Text("Cut to measure", style = MaterialTheme.typography.headlineMedium, color = Brand.Ivory)
            Spacer(Modifier.height(10.dp))
            OrnamentDivider(color = Brand.Gold, width = 88)
        }
        TapeMeasure(color = Brand.Gold)
        Spacer(Modifier.height(12.dp))
        DrawerDestination.entries.forEach { d ->
            NavigationDrawerItem(
                label = { Text(d.label, style = MaterialTheme.typography.titleSmall) },
                icon = { Icon(d.icon, contentDescription = null) },
                selected = d == selected,
                onClick = { onSelect(d) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = Brand.Gold,
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Version 0.1 · Made for tailors",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp).navigationBarsPadding(),
        )
    }
}
