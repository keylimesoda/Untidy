package com.tidal.wear.ui.foryou

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ScalingLazyColumn
import com.tidal.wear.ui.theme.TidalColors

@Composable
fun ForYouScreen(onPlayQuery: (String) -> Unit) {
    val viewModel = viewModel<ForYouViewModel>()
    val items by viewModel.items.collectAsState()
    Box(Modifier.fillMaxSize().background(TidalColors.Black)) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        "For You",
                        color = TidalColors.Cyan,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                items.forEach { item ->
                    item { ForYouCard(item = item, onPlay = { onPlayQuery(item.query) }) }
                }
            }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ForYouCard(item: ForYouItem, onPlay: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .width(220.dp)
            .height(52.dp)
            .padding(vertical = 3.dp)
            .background(TidalColors.Surface, RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onPlay,
                onLongClick = { Toast.makeText(context, "Options coming soon", Toast.LENGTH_SHORT).show() },
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = TidalColors.Cyan, modifier = Modifier.size(18.dp))
            Column {
                Text(item.title, color = TidalColors.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.subtitle, color = TidalColors.OnSurfaceMuted, fontSize = 12.sp, maxLines = 1)
            }
        }
        Box(
            modifier = Modifier.size(48.dp).combinedClickable(
                onClick = { Toast.makeText(context, "Offline coming soon", Toast.LENGTH_SHORT).show() },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Download,
                contentDescription = "Save offline",
                tint = TidalColors.Cyan,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}





