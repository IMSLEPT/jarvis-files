package com.jarvis.files.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.jarvis.files.R

/** Approved Jarvis / FILES lockup. Text is outlined, so the logo remains sharp at every density. */
@Composable
fun JarvisBrand(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier.height(44.dp).clearAndSetSemantics { contentDescription = "Jarvis Files" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        Image(painterResource(R.drawable.jarvis_folder_brand), null, Modifier.size(if (compact) 36.dp else 40.dp))
        Image(painterResource(R.drawable.jarvis_wordmark_brand), null, Modifier.size(if (compact) 100.dp else 110.dp, if (compact) 37.6.dp else 41.4.dp))
    }
}

