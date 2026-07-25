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
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ua.ukrtv.app.util.AppLogger

@UnstableApi
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
            while (advanceCountdown > 0) {
                delay(1000)
                advanceCountdown--
            }
            showAdvanceCountdown = false
            playerLaunched = false
            resultHandled = false
        }
    }

    val scope = rememberCoroutineScope()

    val externalPlayerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        resultHandled = true
        viewModel.releaseExternalPlayerLaunchLock()
        scope.launch {
            val returnResult = viewModel.handleExternalPlayerResult(result.resultCode, result.data)
            viewModel.releaseEngine()
            when (returnResult) {
                is ExternalPlayerReturnResult.Advanced -> {
                    showAdvanceCountdown = true
                    advanceCountdown = 5
                }
                else -> {
                    playerLaunched = false
                    onBack()
                }
            }
        }
    }

    LaunchedEffect(url, contentId, season, episode) {
        viewModel.initialize(contentId, title, url, season, episode, poster)
    }

    LaunchedEffect(state.status, playerLaunched) {
        val status = state.status
        if (status is PlayerStatus.Ready && !playerLaunched && viewModel.tryAcquireExternalPlayerLaunchLock()) {
            try {
                withTimeoutOrNull(1500L) {
                    viewModel.deepResolutionCompleted.filter { it }.first()
                }
                playerLaunched = true
                viewModel.saveBeforeExternalPlayerLaunch()
                val intent = viewModel.createExternalPlayerIntent()
                if (intent != null) {
                    try {
                        externalPlayerLauncher.launch(intent)
                    } catch (e: android.content.ActivityNotFoundException) {
                        val playerLabel = viewModel.getCurrentExternalPlayerInfo()?.label ?: "плеєр"
                        AppLogger.w("ExternalPlayer", "Player not found: $playerLabel")
                        viewModel.releaseExternalPlayerLaunchLock()
                        onBack()
                    }
                } else {
                    viewModel.releaseExternalPlayerLaunchLock()
                    onBack()
                }
            } catch (_: Exception) {
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
            playerLaunched = false
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
                                playerLaunched = false
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
