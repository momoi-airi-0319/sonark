package md.oak.sonark.ui.utils

object ArtistUtils {
    private val splitRegex = Regex("""(?i)\s+(?:feat|ft|with)\.?\s*|\s+and\s+|[,;&/]\s*""")

    /**
     * Splits an artist string into a list of individual artists.
     * E.g., "Artist A feat. Artist B" -> ["Artist A", "Artist B"]
     */
    fun splitArtists(artistString: String?): List<String> {
        if (artistString.isNullOrBlank()) return emptyList()
        if (artistString.equals("Various Artists", ignoreCase = true)) return listOf(artistString)

        return artistString.split(splitRegex)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /**
     * Normalizes a name by removing spaces and converting full-width characters to half-width.
     */
    fun normalize(name: String): String {
        val sb = StringBuilder()
        for (char in name) {
            val normalizedChar = when {
                char == '\u3000' -> ' ' // Full-width space
                char in '\uFF01'..'\uFF5E' -> (char.code - 0xFEE0).toChar() // Full-width to half-width
                else -> char
            }
            if (normalizedChar != ' ') {
                sb.append(normalizedChar)
            }
        }
        return sb.toString()
    }
}
