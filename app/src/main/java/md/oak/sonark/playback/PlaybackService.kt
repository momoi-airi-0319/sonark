package md.oak.sonark.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import md.oak.sonark.MainActivity
import md.oak.sonark.data.Dependencies

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = createPlayer()
        mediaSession = createMediaSession(player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        if ((!player.playWhenReady) || (player.mediaItemCount == 0)) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun createPlayer(): ExoPlayer {
        val dataSourceFactory = DefaultDataSource.Factory(
            this,
            SonarkDataSourceFactory(),
        )

        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory))
            .build().apply {
                addListener(PlayerListener())
            }
    }

    private fun createMediaSession(player: Player): MediaSession {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_SHOW_PLAYER
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    private class PlayerListener : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Player error [${error.errorCodeName}]: ${error.message}", error)
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        const val ACTION_SHOW_PLAYER = "md.oak.sonark.ACTION_SHOW_PLAYER"
    }
}

@UnstableApi
private class SonarkDataSourceFactory : DataSource.Factory {
    override fun createDataSource(): DataSource {
        val httpDataSource = DefaultHttpDataSource.Factory()
            .setUserAgent("Sonark")
            .setAllowCrossProtocolRedirects(true)
            .createDataSource()
        return SonarkDataSource(httpDataSource)
    }
}

@UnstableApi
private class SonarkDataSource(
    private val baseDataSource: DataSource,
) : DataSource by baseDataSource {
    override fun open(dataSpec: DataSpec): Long {
        val url = dataSpec.uri.toString()
        val headers = getHeadersForUrl(url)

        val authorizedDataSpec = if (headers.isNotEmpty()) {
            dataSpec.buildUpon()
                .setHttpRequestHeaders(headers)
                .build()
        } else {
            dataSpec
        }
        return baseDataSource.open(authorizedDataSpec)
    }

    private fun getHeadersForUrl(url: String): Map<String, String> {
        return when {
            url.contains("googleapis.com") -> Dependencies.driveProvider.getAuthHeaders()
            else -> emptyMap()
        }
    }
}
