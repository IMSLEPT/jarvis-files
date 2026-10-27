package com.jarvis.files.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jarvis.files.R
import com.jarvis.files.core.FileCategory
import com.jarvis.files.ui.screens.CAT_STYLES
import com.jarvis.files.ui.theme.J

/** Le stesse categorie hanno gli stessi simboli in Home e nell'elenco File. */
@Composable
fun CategorySymbol(category: FileCategory, modifier: Modifier = Modifier) {
    val asset = when (category) {
        FileCategory.VIDEO -> R.drawable.cat_video
        FileCategory.IMAGE -> R.drawable.cat_image
        FileCategory.AUDIO -> R.drawable.cat_audio
        FileCategory.ARCHIVE -> R.drawable.cat_archive
        else -> null
    }
    Box(modifier.size(40.dp), contentAlignment = Alignment.Center) {
        if (asset != null) Image(painterResource(asset), null, Modifier.fillMaxSize())
        else CAT_STYLES.firstOrNull { it.cat == category }?.let {
            Icon(it.icon, null, tint = it.colors.last(), modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun SearchEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(J.Card)
        .border(1.dp, J.Stroke, RoundedCornerShape(20.dp)).pressable(onClick)
        .semantics(mergeDescendants = true) { contentDescription = "Cerca file e cartelle" }
        .heightIn(min = 56.dp).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Search, null, tint = J.Muted, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text("Cerca file e cartelle", color = J.Muted, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun RowDivider() = HorizontalDivider(Modifier.padding(start = 62.dp, end = 16.dp), color = J.Stroke)

/** Riga compatta, con testo libero di andare a capo quando aumentano i caratteri. */
@Composable
fun FeatureRow(icon: ImageVector, title: String, subtitle: String = "", onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressable(onClick).heightIn(min = 58.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = J.Blue, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = J.Text, style = MaterialTheme.typography.titleSmall)
            if (subtitle.isNotBlank()) Text(subtitle, color = J.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = J.Muted, modifier = Modifier.size(20.dp))
    }
}
