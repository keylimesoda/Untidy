package com.tidal.wear.ui.search

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import com.tidal.wear.core.api.TidalApiClient
import com.tidal.wear.core.model.TidalTrack
import com.tidal.wear.ui.components.TidalChip
import com.tidal.wear.ui.theme.TidalColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(
    apiClient: TidalApiClient,
    onPlayTrack: (TidalTrack) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sidePadding = (LocalConfiguration.current.screenWidthDp * 0.12f).dp
    var query by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<TidalTrack>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun searchNow() {
        val q = query.trim()
        if (q.isBlank() || loading) return
        submittedQuery = q
        tracks = emptyList()
        error = null
        loading = true
        focusManager.clearFocus()
        keyboardController?.hide()
        scope.launch {
            try {
                tracks = withContext(Dispatchers.IO) { apiClient.search(q).tracks.take(12) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                tracks = emptyList()
                error = "Search unavailable"
            } finally {
                loading = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(TidalColors.Black)) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    text = "Search",
                    color = TidalColors.Cyan,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                )
            }
            if (submittedQuery.isBlank()) {
                item {
                    SearchInput(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = ::searchNow,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = sidePadding),
                    )
                }
                item {
                    Button(
                        onClick = ::searchNow,
                        enabled = query.isNotBlank() && !loading,
                        colors = ButtonDefaults.buttonColors(backgroundColor = TidalColors.Cyan, contentColor = TidalColors.Black),
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                }
                item { StatusText("Type a song, artist, or album") }
            } else {
                item {
                    Text(
                        text = submittedQuery,
                        color = TidalColors.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = sidePadding, vertical = 4.dp),
                    )
                }
                when {
                    loading -> item { SearchingIndicator() }
                    error != null -> item { StatusText(error.orEmpty()) }
                    tracks.isEmpty() -> item { StatusText("No tracks found") }
                    else -> tracks.forEach { track ->
                        item {
                            TidalChip(
                                label = track.title,
                                secondaryLabel = listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                                onClick = { onPlayTrack(track) },
                                icon = null,
                            )
                        }
                    }
                }
                if (!loading) {
                    item {
                        Button(
                            onClick = {
                                submittedQuery = ""
                                tracks = emptyList()
                                error = null
                            },
                            colors = ButtonDefaults.secondaryButtonColors(backgroundColor = TidalColors.Surface, contentColor = TidalColors.White),
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = "New search")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchingIndicator() {
    val pulse by rememberInfiniteTransition(label = "searching").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1200), repeatMode = RepeatMode.Reverse),
        label = "searchingPulse",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 10.dp)) {
        Box(
            Modifier
                .size(7.dp)
                .background(TidalColors.Cyan.copy(alpha = pulse), CircleShape),
        )
        Text(
            text = "Searching…",
            color = TidalColors.OnSurfaceMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(color = TidalColors.White, fontSize = 14.sp, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(TidalColors.Cyan),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { innerTextField ->
            Box(
                modifier = modifier
                    .background(TidalColors.Surface, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (query.isBlank()) {
                    Text("Search TIDAL", color = TidalColors.OnSurfaceMuted, fontSize = 14.sp)
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        color = TidalColors.OnSurfaceMuted,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
    )
}
