package md.oak.sonark.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import md.oak.sonark.data.model.AlbumType
import md.oak.sonark.data.model.DownloadStatus
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.playback.PlaybackService
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

enum class PlaybackMode {
    LOCAL,     // 全部曲目已全辑下载，使用本地离线文件播放
    STREAMING  // 未全辑下载，以流媒体模式播放整张专辑
}

@UnstableApi
class PlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "PlaybackViewModel"

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController? get() = if (controllerFuture?.isDone == true) controllerFuture?.get() else null

    private val _isPlaying = MutableStateFlow(value = false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSong = MutableStateFlow<SyncSong?>(value = null)
    val currentSong: StateFlow<SyncSong?> = _currentSong.asStateFlow()

    private val _playbackProgress = MutableStateFlow(value = 0L)
    val playbackProgress: StateFlow<Long> = _playbackProgress.asStateFlow()

    private val _duration = MutableStateFlow(value = 0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(value = false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(value = Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _currentPlaybackMode = MutableStateFlow(PlaybackMode.LOCAL)
    val currentPlaybackMode: StateFlow<PlaybackMode> = _currentPlaybackMode.asStateFlow()

    private val _targetAlbumMode = MutableStateFlow<PlaybackMode?>(null)
    val targetAlbumMode: StateFlow<PlaybackMode?> = _targetAlbumMode.asStateFlow()

    private val _queue = MutableStateFlow<List<SyncSong>>(value = emptyList())
    private val _currentAlbumSongs = MutableStateFlow<List<SyncSong>>(emptyList())

    private var progressJob: Job? = null
    private var lastSeekTime = 0L

    init {
        val sessionToken = SessionToken(application, ComponentName(application, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                setupController()
            },
            MoreExecutors.directExecutor(),
        )
    }

    private fun setupController() {
        val controller = this.controller ?: return
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) startProgressUpdate() else stopProgressUpdate()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                handleMediaItemTransition(mediaItem)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) updateDuration(controller)
            }

            override fun onShuffleModeEnabledChanged(enabled: Boolean) { _shuffleEnabled.value = enabled }
            override fun onRepeatModeChanged(mode: Int) { _repeatMode.value = mode }
        })
        
        _isPlaying.value = controller.isPlaying
        _duration.value = controller.duration
        _shuffleEnabled.value = controller.shuffleModeEnabled
        _repeatMode.value = controller.repeatMode
        if (controller.isPlaying) startProgressUpdate()
    }

    private fun handleMediaItemTransition(mediaItem: MediaItem?) {
        val song = _queue.value.find { it.song.id == mediaItem?.mediaId }
        _currentSong.value = song
        song?.let {
            _duration.value = it.song.duration
            _playbackProgress.value = 0L
            lastSeekTime = 0L
        }
    }

    private fun updateDuration(controller: MediaController) {
        val playerDuration = controller.duration
        if (playerDuration <= 0) return
        
        val isCue = _currentSong.value?.song?.type == AlbumType.CUE
        if (!isCue || (_duration.value <= 0)) {
            _duration.value = playerDuration
        }
    }

    fun isAlbumFullyDownloaded(albumSongs: List<SyncSong>): Boolean {
        if (albumSongs.isEmpty()) return false
        return albumSongs.all { song ->
            song.localPath != null &&
            File(song.localPath).exists() &&
            song.downloadStatus == DownloadStatus.COMPLETED
        }
    }

    fun updateCurrentAlbumSongs(allLibrarySongs: List<SyncSong>) {
        val currentAlbumId = _currentSong.value?.albumId ?: return
        val albumSongs = allLibrarySongs.filter { it.albumId == currentAlbumId }
        if (albumSongs.isNotEmpty()) {
            _currentAlbumSongs.value = albumSongs
            val latestMode = if (isAlbumFullyDownloaded(albumSongs)) PlaybackMode.LOCAL else PlaybackMode.STREAMING
            _targetAlbumMode.value = latestMode
        }
    }

    fun playAlbum(albumSongs: List<SyncSong>, startIndex: Int = 0) {
        val controller = this.controller ?: return
        if (albumSongs.isEmpty()) return

        val sortedAlbumSongs = albumSongs.sortedWith(
            compareBy<SyncSong> { it.song.discNumber }.thenBy { it.song.trackNumber }
        )

        val isDownloaded = isAlbumFullyDownloaded(sortedAlbumSongs)
        val mode = if (isDownloaded) PlaybackMode.LOCAL else PlaybackMode.STREAMING

        _currentPlaybackMode.value = mode
        _targetAlbumMode.value = mode
        _currentAlbumSongs.value = sortedAlbumSongs
        _queue.value = sortedAlbumSongs

        val targetSong = albumSongs.getOrNull(startIndex)
        val actualIndex = sortedAlbumSongs.indexOfFirst { it.song.id == targetSong?.song?.id }.coerceAtLeast(0)
        val actualTargetSong = sortedAlbumSongs.getOrNull(actualIndex)

        val mediaItems = sortedAlbumSongs.mapIndexed { index, syncSong ->
            createMediaItemForMode(syncSong, sortedAlbumSongs.getOrNull(index + 1), mode)
        }

        controller.setMediaItems(mediaItems, actualIndex, 0L)
        controller.prepare()
        controller.play()
        _currentSong.value = actualTargetSong
    }

    fun switchPlaybackMode() {
        val targetMode = _targetAlbumMode.value ?: return
        if (targetMode == _currentPlaybackMode.value) return
        val controller = this.controller ?: return
        val albumSongs = _currentAlbumSongs.value
        if (albumSongs.isEmpty()) return

        val currentSongId = _currentSong.value?.song?.id
        val currentIndex = albumSongs.indexOfFirst { it.song.id == currentSongId }.coerceAtLeast(0)
        val currentPosition = controller.currentPosition
        val wasPlaying = controller.isPlaying

        _currentPlaybackMode.value = targetMode
        _queue.value = albumSongs

        val mediaItems = albumSongs.mapIndexed { index, syncSong ->
            createMediaItemForMode(syncSong, albumSongs.getOrNull(index + 1), targetMode)
        }

        controller.setMediaItems(mediaItems, currentIndex, currentPosition)
        controller.prepare()
        if (wasPlaying) controller.play()
    }

    private fun createMediaItemForMode(syncSong: SyncSong, nextSong: SyncSong?, mode: PlaybackMode): MediaItem {
        val uri = if (mode == PlaybackMode.LOCAL && syncSong.localPath != null && File(syncSong.localPath).exists()) {
            Uri.fromFile(File(syncSong.localPath))
        } else {
            syncSong.data.toUri()
        }
        
        val duration = when {
            syncSong.song.duration > 0 -> syncSong.song.duration
            syncSong.song.type == AlbumType.CUE && nextSong?.albumId == syncSong.albumId -> 
                nextSong.startOffset - syncSong.startOffset
            else -> 0L
        }

        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(syncSong.startOffset)
            .apply { if (duration > 0) setEndPositionMs(syncSong.startOffset + duration) }
            .build()

        return MediaItem.Builder()
            .setMediaId(syncSong.song.id)
            .setUri(uri)
            .setClippingConfiguration(clipping)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(syncSong.song.title)
                    .setArtist(syncSong.song.artist)
                    .setAlbumTitle(syncSong.song.album)
                    .setArtworkUri(syncSong.song.imageUrl?.toUri())
                    .build()
            )
            .build()
    }

    fun togglePlayback() {
        val controller = this.controller ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun stopPlayback() {
        val controller = this.controller ?: return
        controller.stop()
        controller.clearMediaItems()
        _currentSong.value = null
        _queue.value = emptyList()
    }

    fun toggleShuffle() {
        val controller = this.controller ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    fun toggleRepeatMode() {
        val controller = this.controller ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = nextMode
    }

    fun seekTo(position: Long) {
        controller?.seekTo(position)
        _playbackProgress.value = position
        lastSeekTime = System.currentTimeMillis()
    }

    fun skipNext() {
        controller?.seekToNext()
    }

    fun skipPrevious() {
        controller?.seekToPrevious()
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                if (now - lastSeekTime > 500) {
                    val pos = controller?.currentPosition ?: 0L
                    _playbackProgress.value = pos
                }
                delay(200.milliseconds)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
    }

    override fun onCleared() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        stopProgressUpdate()
    }
}
