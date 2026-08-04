package ua.ukrtv.app.ui.player

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ua.ukrtv.app.util.AppLogger

@Composable
fun ExternalPlayerScreen(
    url: String,
    contentId: String,
    title: String,
    poster: String,
    season: Int?,
    episode: Int?,
    onBack: () -> Unit,
    viewModel: PlayerViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var playerLaunched by remember { mutableStateOf(viewModel.hasPendingExternalPlayerResult()) }
    var resultHandled by remember { mutableStateOf(false) }
    var showCancelButton by remember { mutableStateOf(false) }
    var showAdvanceCountdown by remember { mutableStateOf(false) }
    var advanceCountdown by remember { mutableIntStateOf(5) }

    LaunchedEffect(playerLaunched) {
        if (playerLaunched) {
            delay(5000)
            showCancelButton = true
        }
    }

    LaunchedEffect(showAdvanceCountdown) {
        if (showAdvanceCountdown) {
            AppLogger.d("ExternalPlayer", "Countdown started")
            while (advanceCountdown > 0) {
                delay(1000)
                advanceCountdown--
            }
            AppLogger.d("ExternalPlayer", "Countdown finished, resetting states")
            showAdvanceCountdown = false
            playerLaunched = false
            resultHandled = false
            // Reset ViewModel's resolving state if needed
        }
    }

    val scope = rememberCoroutineScope()

    val externalPlayerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        AppLogger.d("ExternalPlayer", "ActivityResult received: code=${result.resultCode}")
        resultHandled = true
        viewModel.releaseExternalPlayerLaunchLock()
        scope.launch {
            val returnResult = viewModel.handleExternalPlayerResult(result.resultCode, result.data)
            AppLogger.d("ExternalPlayer", "handleExternalPlayerResult returned: $returnResult")
            viewModel.releaseEngine()
            when (returnResult) {
                is ExternalPlayerReturnResult.Advanced -> {
                    AppLogger.d("ExternalPlayer", "Showing advance countdown")
                    showAdvanceCountdown = true
                    advanceCountdown = 5
                }
                else -> {
                    AppLogger.d("ExternalPlayer", "Not advancing, closing screen")
                    playerLaunched = false
                    onBack()
                }
            }
        }
    }

    LaunchedEffect(url, contentId, season, episode) {
        viewModel.initialize(contentId, title, url, season, episode, poster)
    }

    LaunchedEffect(state.status is PlayerStatus.Ready, playerLaunched) {
        val status = state.status
        if (status is PlayerStatus.Ready && !playerLaunched && !resultHandled && viewModel.tryAcquireExternalPlayerLaunchLock()) {
            AppLogger.d("ExternalPlayer", "Ready to launch: ${status.title}, url=${status.url}")
            try {
                withTimeoutOrNull(1500L) {
                    viewModel.deepResolutionCompleted.filter { it }.first()
                }
                viewModel.saveBeforeExternalPlayerLaunch()
                val intent = viewModel.createExternalPlayerIntent()
                if (intent != null) {
                    try {
                        AppLogger.d("ExternalPlayer", "Launching intent for ${status.title}")
                        externalPlayerLauncher.launch(intent)
                        playerLaunched = true
                    } catch (e: android.content.ActivityNotFoundException) {
                        val playerLabel = viewModel.getCurrentExternalPlayerInfo()?.label ?: "плеєр"
                        AppLogger.w("ExternalPlayer", "Player not found: $playerLabel")
                        viewModel.releaseExternalPlayerLaunchLock()
                        onBack()
                        return@LaunchedEffect
                    }
                } else {
                    AppLogger.w("ExternalPlayer", "Failed to create intent for ${status.title}")
                    viewModel.releaseExternalPlayerLaunchLock()
                    onBack()
                }
            } catch (e: Exception) {
                AppLogger.e("ExternalPlayer", "Launch failed", e)
                if (e is kotlinx.coroutines.CancellationException) throw e
                viewModel.releaseExternalPlayerLaunchLock()
                onBack()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!resultHandled) {
                val currentStatus = viewModel.state.value.status
                if (currentStatus is PlayerStatus.Ready) {
                    val pos = currentStatus.positionMs
                    val dur = viewModel.savedStateHandle.get<Long>(PlayerViewModel.KEY_EXTERNAL_DURATION) ?: 0L
                    val effectiveDur = if (dur > 0) dur else pos
                    if (effectiveDur > 0) {
                        viewModel.saveProgress(pos, effectiveDur)
                    }
                }
            }
        }
    }

    val currentStatus = state.status

    val isLoadingVisible = !resultHandled && (
        currentStatus is PlayerStatus.Loading ||
        (currentStatus is PlayerStatus.Ready && playerLaunched)
    )

    BackHandler(enabled = isLoadingVisible || showAdvanceCountdown) {
        if (showAdvanceCountdown) {
            showAdvanceCountdown = false
            onBack()
        } else {
            viewModel.releaseExternalPlayerLaunchLock()
            viewModel.savedStateHandle[PlayerViewModel.KEY_PENDING_RESULT] = false
            onBack()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when {
            currentStatus is PlayerStatus.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(currentStatus.message, color = Color.Red, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF6E85B7), RoundedCornerShape(8.dp))
                            .clickable { onBack() }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text("Назад", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            showAdvanceCountdown -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    EpisodeLoadingOverlay(
                        id = contentId,
                        poster = poster,
                        season = state.currentSeason,
                        episode = state.currentEpisode,
                        visible = true
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Наступна серія через $advanceCountdown",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .clickable {
                                showAdvanceCountdown = false
                                onBack()
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text("Скасувати", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
            else -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EpisodeLoadingOverlay(
                        id = contentId,
                        poster = poster,
                        season = state.currentSeason,
                        episode = state.currentEpisode,
                        visible = isLoadingVisible
                    )
                    
                    if (isLoadingVisible && showCancelButton) {
                        Spacer(Modifier.height(32.dp))
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.releaseExternalPlayerLaunchLock()
                                    viewModel.savedStateHandle[PlayerViewModel.KEY_PENDING_RESULT] = false
                                    onBack()
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text("Скасувати очікування", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
