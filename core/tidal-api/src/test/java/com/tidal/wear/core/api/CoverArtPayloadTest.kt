package com.tidal.wear.core.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

// 2026-11 TIDAL image migration: v2 payloads no longer carry inline imageId/cover
// values; artwork lives behind a coverArt relationship to an `artworks` resource
// whose files[] hold pre-built size-qualified CDN URLs.
class CoverArtPayloadTest {
    private val track = buildJsonObject {
        put("type", "tracks")
        put("id", "1274572")
        put(
            "attributes",
            buildJsonObject {
                put("title", "Standing Outside a Broken Phone Booth")
                put("duration", 232)
            },
        )
        put(
            "relationships",
            buildJsonObject {
                put("albums", relationshipArray("albums", "246075966"))
            },
        )
    }

    private val album = buildJsonObject {
        put("type", "albums")
        put("id", "246075966")
        put(
            "attributes",
            buildJsonObject {
                put("title", "Fire in the Bone")
            },
        )
        put(
            "relationships",
            buildJsonObject {
                put("coverArt", relationshipArray("artworks", "AmX9grPBhCq3eDPHfi1"))
            },
        )
    }

    private val artworks = buildJsonObject {
        put("type", "artworks")
        put("id", "AmX9grPBhCq3eDPHfi1")
        put(
            "attributes",
            buildJsonObject {
                put("mediaType", "IMAGE")
                put("files", artworkFiles("b124a279/100a/493b/b27f/a13ea1a7a761"))
            },
        )
    }

    private val included = mapOf(
        "albums:246075966" to album,
        "artworks:AmX9grPBhCq3eDPHfi1" to artworks,
    )

    @Test
    fun trackResolvesArtworkThroughAlbumCoverArtRelationship() {
        val resolved = track.toTrack(included)

        assertEquals(
            "https://resources.tidal.com/images/b124a279/100a/493b/b27f/a13ea1a7a761/320x320.jpg",
            resolved?.artworkUrl,
        )
    }

    @Test
    fun playlistResolvesArtworkFromOwnCoverArtRelationship() {
        val playlist = buildJsonObject {
            put("type", "playlists")
            put("id", "ca4b0111-9951-4ad5-b2b1-0692c2370d1a")
            put(
                "attributes",
                buildJsonObject {
                    put("name", "1994! Songs of the Year")
                },
            )
            put(
                "relationships",
                buildJsonObject {
                    put(
                        "coverArt",
                        relationshipArray("artworks", "2b7Vtuqr18QSIXFVYWk7TuLHdcEtUsVBmSal92xFmWkl2CbAKdgMwEU65"),
                    )
                },
            )
        }
        val playlistArtworks = buildJsonObject {
            put("type", "artworks")
            put("id", "2b7Vtuqr18QSIXFVYWk7TuLHdcEtUsVBmSal92xFmWkl2CbAKdgMwEU65")
            put(
                "attributes",
                buildJsonObject {
                    put("files", artworkFiles("f8ef2b2f/6308/425b/9bfb/a067e4661d7d"))
                },
            )
        }

        val resolved = playlist.toPlaylist(
            mapOf(
                "artworks:2b7Vtuqr18QSIXFVYWk7TuLHdcEtUsVBmSal92xFmWkl2CbAKdgMwEU65" to playlistArtworks,
            ),
        )

        assertEquals(
            "https://resources.tidal.com/images/f8ef2b2f/6308/425b/9bfb/a067e4661d7d/320x320.jpg",
            resolved?.artworkUrl,
        )
    }

    @Test
    fun albumResolvesArtworkFromOwnCoverArtRelationship() {
        val resolved = album.toAlbum(included)

        assertEquals(
            "https://resources.tidal.com/images/b124a279/100a/493b/b27f/a13ea1a7a761/320x320.jpg",
            resolved?.artworkUrl,
        )
    }

    @Test
    fun artworkFileSelectionPrefersRequestedSizeThenLargest() {
        val files = buildJsonArray {
            add(
                buildJsonObject {
                    put("href", "https://resources.tidal.com/images/x/1280x1280.jpg")
                    put("meta", buildJsonObject { put("width", 1280) })
                },
            )
            add(
                buildJsonObject {
                    put("href", "https://resources.tidal.com/images/x/160x160.jpg")
                    put("meta", buildJsonObject { put("width", 160) })
                },
            )
        }

        assertEquals(
            "https://resources.tidal.com/images/x/160x160.jpg",
            (artworksWith(files)).artworkFileUrl(size = 80),
        )
        assertEquals(
            "https://resources.tidal.com/images/x/1280x1280.jpg",
            (artworksWith(files)).artworkFileUrl(size = 320),
        )
    }

    @Test
    fun legacyInlineCoverIdentityStillBuildsSizeQualifiedUrl() {
        val legacyAlbum = buildJsonObject {
            put("type", "albums")
            put("id", "246075966")
            put(
                "attributes",
                buildJsonObject {
                    put("title", "Fire in the Bone")
                    put("cover", "b124a279-100a-493b-b27f-a13ea1a7a761")
                },
            )
        }

        assertEquals(
            "https://resources.tidal.com/images/b124a279/100a/493b/b27f/a13ea1a7a761/320x320.jpg",
            legacyAlbum.toAlbum(emptyMap())?.artworkUrl,
        )
    }

    private fun relationshipArray(type: String, id: String): JsonObject = buildJsonObject {
        put(
            "data",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", type)
                        put("id", id)
                    },
                )
            },
        )
    }

    private fun artworkFiles(path: String): JsonArray = buildJsonArray {
        for (size in listOf(1280, 1080, 640, 320, 160, 80, 40)) {
            add(
                buildJsonObject {
                    put("href", "https://resources.tidal.com/images/$path/${size}x$size.jpg")
                    put("type", "image/jpeg")
                    put(
                        "meta",
                        buildJsonObject {
                            put("width", size)
                            put("height", size)
                        },
                    )
                },
            )
        }
    }

    private fun artworksWith(files: JsonArray): JsonObject = buildJsonObject {
        put("type", "artworks")
        put("id", "files")
        put("attributes", buildJsonObject { put("files", files) })
    }
}
