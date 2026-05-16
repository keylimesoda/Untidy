package com.tidal.wear.ui.foryou

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

 data class ForYouItem(val title: String, val subtitle: String, val query: String)

class ForYouViewModel : ViewModel() {
    private val _items = MutableStateFlow(
        listOf(
            ForYouItem("Daily Mix", "Editorial radio", "Daft Punk"),
            ForYouItem("Workout", "High energy", "The Weeknd"),
            ForYouItem("Chill", "Late night", "Miles Davis"),
            ForYouItem("Fresh", "New music", "Billie Eilish"),
            ForYouItem("Classics", "Essentials", "Prince"),
        ),
    )
    val items: StateFlow<List<ForYouItem>> = _items
}
