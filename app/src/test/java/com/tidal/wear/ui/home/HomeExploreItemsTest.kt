package com.tidal.wear.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeExploreItemsTest {
    @Test
    fun exploreKeepsBrowseDestinationsWithoutRepeatingPrimarySearch() {
        assertEquals(
            listOf("Library", "Discover", "On this watch", "Settings"),
            homeExploreDestinations(searchIsPrimary = true).map(HomeExploreDestination::label),
        )
        assertFalse(homeExploreDestinations(searchIsPrimary = true).any { it.label == "Search" })
    }

    @Test
    fun exploreRetainsSearchWhenPrimaryActionIsResumeOrNowPlaying() {
        assertEquals(
            listOf("Search", "Library", "Discover", "On this watch", "Settings"),
            homeExploreDestinations(searchIsPrimary = false).map(HomeExploreDestination::label),
        )
    }
}
