package md.oak.sonark.data

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object Utils {
    fun calculateMd5(file: File): String? {
        if (!file.exists()) return null
        return try {
            val digest = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}
