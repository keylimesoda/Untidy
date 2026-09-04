package com.tidal.wear.ui.home

/**
 * Secondary destinations shown below Home's primary action.
 *
 * Search is omitted when it is already Home's primary action. When a track is
 * available, Home promotes Resume/Now playing and Search stays available here.
 */
enum class HomeExploreDestination(
    val label: String,
    val secondaryLabel: String,
) {
    Search("Search", "Find music on TIDAL"),
    Library("Library", "Your collection"),
    Discover("Discover", "Personalized mixes"),
    OnThisWatch("On this watch", "Downloads & offline playback"),
    Settings("Settings", "Account & playback"),
}

private val browseDestinations: List<HomeExploreDestination> = listOf(
    HomeExploreDestination.Library,
    HomeExploreDestination.Discover,
    HomeExploreDestination.OnThisWatch,
    HomeExploreDestination.Settings,
)

fun homeExploreDestinations(searchIsPrimary: Boolean): List<HomeExploreDestination> =
    if (searchIsPrimary) browseDestinations else listOf(HomeExploreDestination.Search) + browseDestinations
