package com.example.rxaide.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Air
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.vectorResource
import com.example.rxaide.R

/**
 * Returns an icon appropriate for the given medication form (e.g. Tablet, Capsule, Syrup…).
 */
@Composable
fun medicationFormIcon(form: String): ImageVector {
    return when (form.lowercase().trim()) {
        "tablet"       -> ImageVector.vectorResource(id = R.drawable.ic_tablet_pill)
        "capsule"      -> ImageVector.vectorResource(id = R.drawable.ic_capsule)
        "syrup"        -> Icons.Default.LocalDrink
        "injection"    -> Icons.Default.Vaccines
        "drops"        -> Icons.Default.Opacity
        "cream"        -> ImageVector.vectorResource(id = R.drawable.ic_cream_tube)
        "inhaler"      -> Icons.Default.Air
        "nasal spray"  -> ImageVector.vectorResource(id = R.drawable.ic_nasal_spray)
        "spray"        -> ImageVector.vectorResource(id = R.drawable.ic_nasal_spray)
        else           -> Icons.Default.Medication
    }
}
