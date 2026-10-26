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
fun JarvisBrand(modifier: Modifier = Modifier) {
    Row(modifier.height(44.dp).clearAndSetSemantics { contentDescription = "Jarvis Files" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(R.drawable.jarvis_folder_brand), null, Modifier.size(40.dp))
        Image(painterResource(R.drawable.jarvis_wordmark_brand), null, Modifier.size(110.dp, 41.4.dp))
    }
}
