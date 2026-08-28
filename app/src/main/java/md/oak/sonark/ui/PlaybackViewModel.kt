package md.oak.sonark.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
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
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.data.model.AlbumType
import md.oak.sonark.playback.PlaybackService
import androidx.core.net.toUri
import kotlin.time.Duration.Companion.milliseconds

@UnstableApi
class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController? get() = if (controllerFuture?.isDone == true) controllerFuture?.get() else null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSong = MutableStateFlow<SyncSong?>(null)
    val currentSong: StateFlow<SyncSong?> = _currentSong.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0L)
    val playbackProgress: StateFlow<Long> = _playbackProgress.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _queue = MutableStateFlow<List<SyncSong>>(emptyList())
    val queue: StateFlow<List<SyncSong>> = _queue.asStateFlow()

    private var progressJob: Job? = null
    private var lastSeekTime = 0L

    init {
        val sessionToken = SessionToken(application, ComponentName(application, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
        controllerFuture?.addListener({
            setupController()
        }, MoreExecutors.directExecutor())
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
        if (!isCue || _duration.value <= 0) {
            _duration.value = playerDuration
        }
    }

    fun playQueue(songs: List<SyncSong>, startIndex: Int = 0) {
        val controller = this.controller ?: return
        val playableSongs = songs.filter { it.localPath != null && java.io.File(it.localPath).exists() }
        if (playableSongs.isEmpty()) return

        val targetSong = songs.getOrNull(startIndex)
        val newStartIndex = playableSongs.indexOfFirst { it.song.id == targetSong?.song?.id }.coerceAtLeast(0)
        val actualTargetSong = playableSongs.getOrNull(newStartIndex)

        if (isSameQueue(playableSongs) && actualTargetSong?.song?.id == _currentSong.value?.song?.id) {
            if (!controller.isPlaying) controller.play()
            return
        }

        _queue.value = playableSongs
        val mediaItems = playableSongs.mapIndexed { index, syncSong -> 
            createMediaItem(syncSong, playableSongs.getOrNull(index + 1)) 
        }

        val startPosition = if (actualTargetSong?.song?.id == _currentSong.value?.song?.id) controller.currentPosition else 0L
        controller.setMediaItems(mediaItems, newStartIndex, startPosition)
        controller.prepare()
        controller.play()
        _currentSong.value = actualTargetSong
    }

    private fun isSameQueue(newQueue: List<SyncSong>): Boolean {
        return _queue.value.size == newQueue.size && 
               _queue.value.zip(newQueue).all { it.first.song.id == it.second.song.id }
    }

    private fun createMediaItem(syncSong: SyncSong, nextSong: SyncSong?): MediaItem {
        val uri = Uri.fromFile(java.io.File(syncSong.localPath!!))
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
