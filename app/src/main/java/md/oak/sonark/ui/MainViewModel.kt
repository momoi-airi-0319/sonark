package md.oak.sonark.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import md.oak.sonark.data.model.Album
import md.oak.sonark.data.model.AlbumType
import md.oak.sonark.data.model.Artist
import md.oak.sonark.data.model.DownloadStatus
import md.oak.sonark.data.model.SyncSong
import md.oak.sonark.data.repository.AccountRepository
import md.oak.sonark.data.repository.MusicRepository
import md.oak.sonark.data.repository.SettingsRepository
import md.oak.sonark.data.repository.UserAccount
import md.oak.sonark.ui.model.AlbumDownloadItem
import md.oak.sonark.ui.utils.ArtistUtils

enum class SortOrder {
    TITLE, ARTIST
}

enum class UIState {
    LOADING, SUCCESS, EMPTY, ERROR, UNAUTHENTICATED
}

class MainViewModel(
    private val repository: MusicRepository,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UIState.LOADING)
    val uiState: StateFlow<UIState> = _uiState.asStateFlow()

    val accounts = accountRepository.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val storageQuota = accountRepository.storageQuota.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeAccount: StateFlow<UserAccount?> = combine(
        accountRepository.accounts,
        settingsRepository.googleAccountName
    ) { accounts, activeEmail ->
        if (activeEmail == null) null
        else accounts.find { it.email.equals(activeEmail, ignoreCase = true) }
            ?: UserAccount(
                name = activeEmail.split("@").firstOrNull() ?: "User",
                email = activeEmail,
            )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val otherAccounts: StateFlow<List<UserAccount>> = combine(
        accountRepository.accounts,
        activeAccount
    ) { accounts, active ->
        accounts.filter { it.email != active?.email }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    val songs: StateFlow<List<SyncSong>> = repository.getSyncSongsFlow()
        .combine(_sortOrder) { songs, sort ->
            songs.sortedBy { 
                if (sort == SortOrder.TITLE) it.song.title.lowercase() else it.song.artist.lowercase()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<Album>> = repository.getAlbumsFlow()
        .map { albums ->
            albums.sortedBy { it.title.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<Artist>> = combine(songs, albums) { allSongs, allAlbums ->
        allSongs.flatMap { syncSong ->
            ArtistUtils.splitArtists(syncSong.song.artist).map { it to syncSong }
        }
        .groupBy { ArtistUtils.normalize(it.first) }
        .map { (normalizedTarget, normalizedGroup) ->
            val variations = normalizedGroup.groupBy { it.first }
            
            val bestName = variations.keys.asSequence()
                .sortedWith(
                compareByDescending<String> { name ->
                    allAlbums.count { album -> album.artist == name }
                }.thenByDescending { name ->
                    variations[name]?.size ?: 0
                }.thenByDescending { it }
            ).first()

            val allArtistSongs = normalizedGroup.map { it.second }
            Artist(
                name = bestName,
                albumCount = allAlbums.count { ArtistUtils.normalize(it.artist) == normalizedTarget },
                songCount = allArtistSongs.size,
                imageUrl = null
            )
        }
        .sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadQueue: StateFlow<List<AlbumDownloadItem>> = songs
        .map { allSyncSongs ->
            allSyncSongs.groupBy { it.albumId }
                .mapNotNull { (albumId, albumSyncSongs) -> createAlbumDownloadItem(albumId, albumSyncSongs) }
                .sortedBy { it.title.lowercase() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun createAlbumDownloadItem(albumId: String, albumSyncSongs: List<SyncSong>): AlbumDownloadItem? {
        val activeSyncSongs = albumSyncSongs.filter {
            (it.downloadStatus != DownloadStatus.COMPLETED) && (it.downloadStatus != DownloadStatus.NONE)
        }
        if (activeSyncSongs.isEmpty()) return null
        
        val first = albumSyncSongs.first()
        val uniqueFiles = albumSyncSongs.distinctBy { it.data }
        
        val totalProgress = if (uniqueFiles.isNotEmpty()) {
            val completedSum = uniqueFiles.sumOf { 
                if (it.downloadStatus == DownloadStatus.COMPLETED) 100 else it.downloadProgress
            }
            completedSum.toFloat() / (uniqueFiles.size * 100f)
        } else 0f

        val downloading = albumSyncSongs.filter { it.downloadStatus == DownloadStatus.DOWNLOADING }
        val uniqueDownloading = downloading.distinctBy { it.data }
        
        val pending = albumSyncSongs.filter { it.downloadStatus == DownloadStatus.PENDING }
        val error = albumSyncSongs.filter { it.downloadStatus == DownloadStatus.ERROR }

        return if (first.song.type == AlbumType.CUE) {
            AlbumDownloadItem.Cue(
                albumId = albumId,
                title = first.song.album,
                artist = first.song.artist,
                imageUrl = first.song.imageUrl,
                progress = totalProgress,
                totalSongs = albumSyncSongs.size,
                downloadingSongs = uniqueDownloading,
                pendingSongsCount = pending.size,
                errorSongsCount = error.size,
                isDownloading = downloading.isNotEmpty()
            )
        } else {
            AlbumDownloadItem.Normal(
                albumId = albumId,
                title = first.song.album,
                artist = first.song.artist,
                imageUrl = first.song.imageUrl,
                progress = totalProgress,
                totalSongs = albumSyncSongs.size,
                downloadingSongs = uniqueDownloading,
                pendingSongsCount = pending.size,
                errorSongsCount = error.size,
                isDownloading = downloading.isNotEmpty()
            )
        }
    }

    init {
        loadSongs()
        viewModelScope.launch {
            albums.collect { 
                if (it.isNotEmpty() && (_uiState.value == UIState.LOADING || _uiState.value == UIState.EMPTY)) {
                    _uiState.value = UIState.SUCCESS
                }
            }
        }
    }

    fun loadSongs() {
        viewModelScope.launch {
            if (albums.value.isEmpty()) {
                _uiState.value = UIState.LOADING
            }
            refreshAccountInfo()
            
            try {
                repository.syncAll()
                _uiState.value = UIState.SUCCESS
                updateActiveAccountError(false)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Sync failed", e)
                updateActiveAccountError(true)
                if (albums.value.isEmpty()) {
                    _uiState.value = UIState.ERROR
                }
            }
        }
    }

    private suspend fun updateActiveAccountError(hasError: Boolean) {
        val currentAccount = activeAccount.value ?: return
        if (currentAccount.hasConnectionError != hasError) {
            settingsRepository.addOrUpdateAccount(currentAccount.copy(hasConnectionError = hasError))
        }
    }

    fun refreshAccountInfo() {
        viewModelScope.launch {
            try {
                accountRepository.refreshQuota()
                updateActiveAccountError(false)
            } catch (e: Exception) {
                updateActiveAccountError(true)
            }
        }
    }

    fun setUnauthenticated() {
        _uiState.value = UIState.UNAUTHENTICATED
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun getSongsForAlbum(albumTitle: String): List<SyncSong> {
        return songs.value.filter { it.song.album == albumTitle }
    }

    fun fetchMetadataForSongs(songsToProcess: List<SyncSong>) {
        viewModelScope.launch {
            songsToProcess.filter { it.localPath != null && it.song.artist == "Unknown Artist" }
                .forEach { repository.fetchMetadata(it.song.id) }
        }
    }

    fun downloadSongs(songs: List<SyncSong>) {
        viewModelScope.launch {
            songs.forEach { song ->
                if (song.localPath == null) {
                    repository.resumeDownload(song.song.id)
                }
            }
        }
    }

    fun pauseDownload(id: String) {
        viewModelScope.launch {
            repository.pauseAlbumDownload(id)
            repository.pauseDownload(id)
        }
    }

    fun resumeDownload(id: String) {
        viewModelScope.launch {
            repository.resumeAlbumDownload(id)
            repository.resumeDownload(id)
        }
    }

    fun pauseAllDownloads() {
        viewModelScope.launch {
            repository.pauseAllDownloads()
        }
    }

    fun resumeAllDownloads() {
        viewModelScope.launch {
            repository.resumeAllDownloads()
        }
    }
}
