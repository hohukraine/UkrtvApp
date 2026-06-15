package ua.ukrtv.app.ui.player

import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentDialog
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ua.ukrtv.app.R
import ua.ukrtv.app.domain.model.StreamResolutionResult
import ua.ukrtv.app.domain.model.Episode
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.model.StreamType
import ua.ukrtv.app.domain.model.WatchProgress

@UnstableApi
@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    private lateinit var playerView: PlayerView
    private lateinit var loadingView: ProgressBar
    private lateinit var decoderInfoView: android.widget.TextView
    private var player: ExoPlayer? = null
    
    private var lastUrl: String? = null
    private var lastLoadTrigger: Long = 0
    
    private var loudnessEnhancer: LoudnessEnhancer? = null
    
    private var saveProgressJob: Job? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.player_view)
        playerView = findViewById(R.id.player_view)
        loadingView = findViewById(R.id.player_loading)
        decoderInfoView = findViewById(R.id.decoder_info)

        val uakinoUrl = intent.getStringExtra("uakino_url") ?: ""
        val contentId = intent.getStringExtra("content_id") ?: ""
        val hlsUrl = intent.getStringExtra("hls_url") ?: ""
        val title = intent.getStringExtra("title") ?: ""
        val episodeTitle = intent.getStringExtra("episode_title") ?: ""
        val season = intent.getIntExtra("season", -1)
        val episode = intent.getIntExtra("episode", -1)
        val referer = intent.getStringExtra("referer") ?: ""
        val poster = intent.getStringExtra("poster") ?: ""
        val pageUrl = intent.getStringExtra("page_url") ?: ""

        val playbackResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("playback_result", StreamResolutionResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("playback_result")
        }

        val seasons = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("seasons", Season::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra("seasons")
        }

        connectivityManager = getSystemService(ConnectivityManager::class.java)
        setupNetworkMonitoring()

        observeViewModel()

        if (savedInstanceState == null) {
            viewModel.initialize(
                contentId = contentId,
                title = title,
                uakinoUrl = uakinoUrl,
                hlsUrl = hlsUrl,
                season = if (season != -1) season else null,
                episode = if (episode != -1) episode else null,
                playbackResult = playbackResult,
                seasons = seasons,
                referer = referer,
                episodeTitle = episodeTitle,
                poster = poster,
                pageUrl = pageUrl
            )
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        handleUiState(state)
                    }
                }
                launch {
                    viewModel.decoderInfo.collect { info ->
                        if (info != null) {
                            decoderInfoView.text = info
                            decoderInfoView.visibility = View.VISIBLE
                        } else {
                            decoderInfoView.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun handleUiState(state: PlayerUiState) {
        when (state) {
            is PlayerUiState.Idle -> {
                loadingView.visibility = View.GONE
            }
            is PlayerUiState.Loading -> {
                loadingView.visibility = View.VISIBLE
            }
            is PlayerUiState.Ready -> {
                loadingView.visibility = View.GONE
                initExoPlayer(state)
            }
            is PlayerUiState.Error -> {
                loadingView.visibility = View.GONE
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                if (!state.isRetryable) finish()
            }
            is PlayerUiState.SeriesSelection -> {
                loadingView.visibility = View.GONE
                showSeriesSelector(state.seasons)
            }
        }
    }

    private fun initExoPlayer(state: PlayerUiState.Ready) {
        if (player != null && lastUrl == state.url && lastLoadTrigger == state.loadTrigger) {
            return
        }
        
        val isNewPlayer = player == null
        if (isNewPlayer) {
            val dataSourceFactory = viewModel.getDataSourceFactory()
            player = viewModel.buildPlayer(this, dataSourceFactory).apply {
                addListener(object : Player.Listener {
                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && audioSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
                            try {
                                loudnessEnhancer?.release()
                                loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                                    setTargetGain(0)
                                    enabled = true
                                }
                            } catch (e: Exception) {
                                Log.e("PlayerActivity", "LoudnessEnhancer error", e)
                            }
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        loadingView.visibility = if (playbackState == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                    }

                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                        if (videoSize.width > 0) {
                            viewModel.updateDecoderInfo(null, null, videoSize)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        viewModel.onPlayerError(error)
                    }
                })
                
                addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                    override fun onVideoDecoderInitialized(
                        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                        decoderName: String,
                        initializedTimestampMs: Long,
                        initializationDurationMs: Long
                    ) {
                        viewModel.updateDecoderInfo(decoderName, null, null)
                    }

                    override fun onVideoInputFormatChanged(
                        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                        format: androidx.media3.common.Format,
                        decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
                    ) {
                        viewModel.updateDecoderInfo(null, format, null)
                    }
                })
            }
            playerView.player = player
            playerView.controllerShowTimeoutMs = 3000
            playerView.controllerHideOnTouch = true
            
            viewModel.startTracking(player!!)
            startProgressSaving()
        }

        val exoPlayer = player!!
        lastUrl = state.url
        lastLoadTrigger = state.loadTrigger

        playerView.findViewById<android.widget.TextView>(R.id.exo_title)?.text = state.title
        playerView.findViewById<android.widget.TextView>(R.id.subtitle_text)?.text = state.subtitle.uppercase()

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(state.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(state.title)
                    .setSubtitle(state.subtitle)
                    .build()
            )

        when (state.streamType) {
            StreamType.HLS -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            StreamType.MPD -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            StreamType.MP4 -> mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP4)
            else -> {
                if (state.url.contains(".m3u8")) mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                else if (state.url.contains(".mpd")) mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            }
        }

        exoPlayer.setMediaItem(mediaItemBuilder.build())
        if (state.positionMs > 0) {
            exoPlayer.seekTo(state.positionMs)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun showSeriesSelector(seasons: List<Season>) {
        val dialog = ComponentDialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PlayerActivity)
            setViewTreeViewModelStoreOwner(this@PlayerActivity)
            setViewTreeSavedStateRegistryOwner(this@PlayerActivity)
            setContent {
                ua.ukrtv.app.ui.theme.UkrtvTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0C0C0D))
                            .padding(72.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.foundation.layout.Column {
                            androidx.compose.material3.Text(
                                text = "Оберіть серію",
                                color = Color(0xFFE1E1E1),
                                fontSize = 32.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Light,
                                modifier = Modifier.padding(bottom = 48.dp)
                            )
                            ua.ukrtv.app.ui.components.SeasonEpisodePicker(
                                seasons = seasons,
                                onEpisodeClick = { s, e ->
                                    dialog.dismiss()
                                    viewModel.onEpisodeSelected(s, e)
                                }
                            )
                        }
                    }
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.show()
    }

    private fun showSyncDialog(remoteProgress: WatchProgress, localProgress: WatchProgress, player: Player) {
        val remoteTime = formatTime(remoteProgress.positionMs)
        val localTime = formatTime(localProgress.positionMs)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Синхронізація прогресу")
            .setMessage(
                "На іншому пристрої ви переглядаєте $remoteTime\n" +
                "Тут ви зупинилися на $localTime\n\n" +
                "Продовжити з іншого пристрою?"
            )
            .setPositiveButton("Так, з іншого") { _, _ ->
                player.seekTo(remoteProgress.positionMs)
            }
            .setNegativeButton("Ні, з цього") { _, _ ->
                player.seekTo(localProgress.positionMs)
            }
            .setCancelable(false)
            .show()
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun startProgressSaving() {
        saveProgressJob?.cancel()
        saveProgressJob = lifecycleScope.launch {
            while (true) {
                delay(10_000)
                player?.let { p ->
                    if (p.isPlaying && p.duration > 0) {
                        viewModel.saveProgress(p.currentPosition, p.duration)
                    }
                }
            }
        }
    }

    private fun setupNetworkMonitoring() {
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                runOnUiThread {
                    if (player?.isPlaying == true) {
                        Toast.makeText(this@PlayerActivity, "Втрачено з'єднання з мережею", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    Toast.makeText(this@PlayerActivity, "З'єднання відновлено", Toast.LENGTH_SHORT).show()
                }
            }
        }
        cm.registerNetworkCallback(request, networkCallback!!)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player?.let {
                    if (it.isPlaying) it.pause() else it.play()
                }
                playerView.showController()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                player?.seekBack()
                playerView.showController()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                player?.seekForward()
                playerView.showController()
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                val state = viewModel.uiState.value
                if (state is PlayerUiState.Ready) {
                    // Try to show series selector if it's series (we could store that in ViewModel)
                }
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (playerView.isControllerFullyVisible) {
                    playerView.hideController()
                } else {
                    finish()
                }
                true
            }
            else -> {
                playerView.showController()
                super.onKeyDown(keyCode, event)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        player?.let { p ->
            if (p.duration > 0) {
                viewModel.saveProgress(p.currentPosition, p.duration)
            }
        }
        releasePlayer()
    }

    private fun releasePlayer() {
        saveProgressJob?.cancel()
        viewModel.stopTracking()
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        player?.stop()
        player?.release()
        player = null
        playerView.player = null
    }

    override fun onDestroy() {
        networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        super.onDestroy()
    }

    companion object {
        fun start(
            context: android.content.Context,
            playbackResult: StreamResolutionResult,
            contentId: String,
            title: String,
            season: Int? = null,
            episode: Int? = null,
            uakinoUrl: String? = null,
            seasons: List<Season>? = null,
            poster: String = "",
            pageUrl: String = ""
        ) {
            val intent = android.content.Intent(context, PlayerActivity::class.java).apply {
                putExtra("content_id", contentId)
                putExtra("title", title)
                putExtra("poster", poster)
                putExtra("page_url", pageUrl)
                if (uakinoUrl != null) putExtra("uakino_url", uakinoUrl)
                if (season != null) putExtra("season", season)
                if (episode != null) putExtra("episode", episode)
                
                putExtra("playback_result", playbackResult)
                putExtra("referer", playbackResult.referer)
                if (seasons != null) {
                    putParcelableArrayListExtra("seasons", ArrayList(seasons))
                }

                if (season != null && episode != null) {
                    putExtra("episode_title", "S${season}E${episode}")
                }
            }
            context.startActivity(intent)
        }
    }
}
