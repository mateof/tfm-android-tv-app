package com.mateof.tfmtv.ui.screens.library

import com.mateof.tfmtv.data.model.LibraryItemDto
import com.mateof.tfmtv.data.model.WatchSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemFiltersTest {

    private val alien = LibraryItemDto(id = "a", title = "Alien", year = 1979, rating = 8.5, genres = listOf("Terror"),
        addedAt = "2026-01-10T10:00:00Z", watch = WatchSummaryDto(completed = true, lastPlayedAt = "2026-02-01T00:00:00Z"))
    private val babel = LibraryItemDto(id = "b", title = "Babel", year = 2006, rating = null, genres = listOf("Drama"),
        addedAt = "2026-03-01T10:00:00Z", watch = WatchSummaryDto(inProgress = true, positionMs = 60_000))
    private val elOrfanato = LibraryItemDto(id = "c", title = "El orfanato", year = null, rating = 7.4, genres = listOf("Terror", "Drama"),
        addedAt = null)
    private val all = listOf(babel, elOrfanato, alien)

    private fun ids(filters: ItemFilters) = filters.apply(all).map { it.id }

    @Test fun `latest added first, undated last`() = assertEquals(listOf("b", "a", "c"), ids(ItemFilters(LibrarySort.ADDED)))

    @Test fun `title ignores case and accents`() = assertEquals(listOf("a", "b", "c"), ids(ItemFilters(LibrarySort.TITLE)))

    @Test fun `year and rating descending with missing values last`() {
        assertEquals(listOf("b", "a", "c"), ids(ItemFilters(LibrarySort.YEAR)))
        assertEquals(listOf("a", "c", "b"), ids(ItemFilters(LibrarySort.RATING)))
    }

    @Test fun `watch filters`() {
        assertEquals(listOf("a"), ids(ItemFilters(watch = WatchFilter.WATCHED)))
        assertEquals(listOf("b"), ids(ItemFilters(watch = WatchFilter.IN_PROGRESS)))
        assertEquals(listOf("c"), ids(ItemFilters(watch = WatchFilter.UNWATCHED)))
    }

    @Test fun `genre filter`() =
        assertEquals(listOf("a", "c"), ids(ItemFilters(LibrarySort.TITLE, genre = "Terror")))
}
