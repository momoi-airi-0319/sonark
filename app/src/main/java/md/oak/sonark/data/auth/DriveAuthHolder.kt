package md.oak.sonark.data.auth

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential

/**
 * A simple singleton to hold the Google Drive credentials for access from the PlaybackService.
 * In a real app, this should be handled by a dependency injection framework or a more secure storage.
 */
object DriveAuthHolder {
    var credential: GoogleAccountCredential? = null
}
