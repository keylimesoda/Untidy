package com.tidal.wear.ui.library

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.tidal.wear.ui.components.SecondaryChip
import com.tidal.wear.ui.theme.TidalColors

@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val sidePadding = (LocalConfiguration.current.screenWidthDp * 0.13f).dp
    fun coming() = Toast.makeText(context, "Sign in to TIDAL to see your library — coming soon", Toast.LENGTH_SHORT).show()
    Box(Modifier.fillMaxSize().background(TidalColors.Black)) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = sidePadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Library", color = TidalColors.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text(
                "Sign in to TIDAL to see your library — coming soon",
                color = TidalColors.OnSurfaceMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryChip("Playlists", Icons.Filled.LibraryMusic, ::coming)
            SecondaryChip("Albums", Icons.Filled.Album, ::coming)
            SecondaryChip("Tracks", Icons.Filled.Mic, ::coming)
            SecondaryChip("Artists", Icons.Filled.Person, ::coming)
            SecondaryChip("Downloads", Icons.Filled.Download, ::coming)
        }
    }
}
