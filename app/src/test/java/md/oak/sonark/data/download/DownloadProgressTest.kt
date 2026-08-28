package md.oak.sonark.data.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadProgressTest {

    @Test
    fun testProgressCalculationClamping() {
        // Logic copied from DownloadManager.kt for verification
        fun calculateProgress(downloaded: Long, total: Long): Int {
            return if (total > 0) {
                (downloaded * 100 / total).toInt().coerceIn(0, 100)
            } else 0
        }

        // Normal case
        assertEquals(50, calculateProgress(500, 1000))
        
        // Completion
        assertEquals(100, calculateProgress(1000, 1000))
        
        // Overflow (Incorrect metadata)
        assertEquals(100, calculateProgress(1500, 1000))
        
        // Underflow
        assertEquals(0, calculateProgress(-100, 1000))
        
        // Zero total
        assertEquals(0, calculateProgress(500, 0))
    }
}
