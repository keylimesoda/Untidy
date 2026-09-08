package com.tidal.wear.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tidal.wear.core.model.TidalTrack
import androidx.lifecycle.ViewModel

/** Keeps the player's interactive destination alive while the ambient face is drawn. */
internal class PlayerNavigationState : ViewModel() {
    var showQueue by mutableStateOf(false)
    var showActionsSheet by mutableStateOf(false)
    var actionsSheetDragPx by mutableStateOf(0f)
    var actionsGripArmed by mutableStateOf(false)
    var addToPlaylistTrack by mutableStateOf<TidalTrack?>(null)
    var pendingPlaylistTrackId by mutableStateOf<String?>(null)
    var playlistResult by mutableStateOf<String?>(null)
    var favoriteTrackId: String? = null
    var liked by mutableStateOf(false)
    var likeLoading by mutableStateOf(false)
    var likeError by mutableStateOf<String?>(null)

    fun closeActions() {
        showActionsSheet = false
        actionsSheetDragPx = 0f
        actionsGripArmed = false
    }
}
