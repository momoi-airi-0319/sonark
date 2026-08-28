package md.oak.sonark.data.model

/**
 * Data model representing an Artist in the library.
 */
data class Artist(
    val name: String,
    val albumCount: Int,
    val songCount: Int,
    val imageUrl: String? = null
)
