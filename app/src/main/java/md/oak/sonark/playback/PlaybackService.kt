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
import md.oak.sonark.data.Dependencies

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        
        val httpDataSourceFactory = DataSource.Factory {
            val dataSource = DefaultHttpDataSource.Factory()
                .setUserAgent("Sonark")
                .setAllowCrossProtocolRedirects(true)
                .createDataSource()
            
            object : DataSource by dataSource {
                override fun open(dataSpec: DataSpec): Long {
                    // In a multi-provider setup, we might need the song ID to know which headers to use.
                    // But Media3's DataSource doesn't easily give us the MediaItem here.
                    // For now, we'll try Google Drive headers if the URL matches.
                    val url = dataSpec.uri.toString()
                    val headers = if (url.contains("googleapis.com")) {
                        Dependencies.driveProvider.getAuthHeaders()
                    } else {
                        emptyMap()
                    }
                    
                    val authorizedDataSpec = if (headers.isNotEmpty()) {
                        dataSpec.buildUpon()
                            .setHttpRequestHeaders(headers)
                            .build()
                    } else {
                        dataSpec
                    }
                    return dataSource.open(authorizedDataSpec)
                }
            }
        }

        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory))
            .build()
        
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlaybackService", "Player error: ${error.message}", error)
            }
        })
        
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null) {
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
