package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.R

sealed class Screen(val route: String, val titleRes: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.home, Icons.Default.Home)
    object Plants : Screen("plants", R.string.plants, Icons.Default.Eco)
    object Garden : Screen("garden", R.string.garden_map, Icons.Default.Map)
    object Reports : Screen("reports", R.string.reports, Icons.Default.BarChart)
    object Settings : Screen("settings", R.string.settings, Icons.Default.Settings)
}
