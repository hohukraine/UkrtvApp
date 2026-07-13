package ua.ukrtv.app.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ua.ukrtv.app.util.DeviceClass
import ua.ukrtv.app.util.PerformanceProfile
import ua.ukrtv.app.util.PlayerType
import ua.ukrtv.app.util.resolveDeviceClass
import ua.ukrtv.app.util.getDeviceClass
import ua.ukrtv.app.ui.theme.LocalFormFactor
import ua.ukrtv.app.ui.theme.FormFactor
import ua.ukrtv.app.ui.theme.Background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val formFactor = LocalFormFactor.current
    when (formFactor) {
        FormFactor.TV -> TvSettingsScreen(viewModel, onBack)
        FormFactor.PHONE, FormFactor.TABLET -> PhoneSettingsScreen(viewModel, onBack)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val performanceProfile by viewModel.performanceProfile.collectAsState()
    val playerType by viewModel.playerType.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val context = LocalContext.current
    val hardwareClass = remember { getDeviceClass(context) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(64.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "НАЛАШТУВАННЯ",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Update Section
        Text(
            text = "Оновлення",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Surface(
            onClick = {
                if (updateState is UpdateState.Idle || updateState is UpdateState.UpToDate || updateState is UpdateState.Error) {
                    viewModel.checkForUpdates()
                } else if (updateState is UpdateState.NewVersionAvailable) {
                    viewModel.downloadAndInstallUpdate((updateState as UpdateState.NewVersionAvailable).info)
                } else if (updateState is UpdateState.PermissionRequired) {
                    viewModel.openInstallPermissionSettings()
                } else if (updateState is UpdateState.ReadyToInstall) {
                    viewModel.installUpdate()
                }
            },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF1A1A1A),
                focusedContainerColor = Color(0xFF3B82F6)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Версія додатка",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Поточна версія: ${ua.ukrtv.app.BuildConfig.VERSION_NAME} (${ua.ukrtv.app.BuildConfig.VERSION_CODE})",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }

                    when (val state = updateState) {
                        is UpdateState.Idle -> {
                            Text("Перевірити оновлення", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        }
                        is UpdateState.Checking -> {
                            Text("Перевірка...", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        }
                        is UpdateState.NewVersionAvailable -> {
                            Text("Доступна версія ${state.info.versionName} - Натисніть щоб оновити", color = Color(0xFF8AB4F8), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        is UpdateState.UpToDate -> {
                            Text("Оновлень немає", color = Color.Green.copy(alpha = 0.7f), fontSize = 14.sp)
                        }
                        is UpdateState.Downloading -> {
                            Text("Завантаження: ${(state.progress * 100).toInt()}%", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        }
                        is UpdateState.ReadyToInstall -> {
                            Text("Готово до встановлення", color = Color(0xFF8AB4F8), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        is UpdateState.PermissionRequired -> {
                            Text("Дозвольте встановлення — натисніть щоб відкрити налаштування", color = Color(0xFFFFA726), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        is UpdateState.Error -> {
                            Text(state.message, color = Color.Red.copy(alpha = 0.7f), fontSize = 14.sp)
                        }
                    }
                }

                if (updateState is UpdateState.NewVersionAvailable) {
                    val info = (updateState as UpdateState.NewVersionAvailable).info
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Що нового:\n${info.changelog}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // Device info
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(16.dp)
        ) {
            Column {
                Text("ПРИСТРІЙ", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text("Hardware: ${hardwareClass.name}", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Text("Поточний режим: ${performanceProfile.label}", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Графіка та продуктивність",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PerformanceProfile.entries.forEach { profile ->
                val isSelected = profile == performanceProfile
                val resolvedDevice = resolveDeviceClass(context, profile)

                Surface(
                    onClick = { viewModel.setPerformanceProfile(profile) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) Color(0xFF1E3A5F) else Color(0xFF1A1A1A),
                        focusedContainerColor = Color(0xFF3B82F6),
                        contentColor = if (isSelected) Color(0xFF8AB4F8) else Color(0xFFE1E1E1),
                        focusedContentColor = Color.White
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Preview card
                        PreviewCard(resolvedDevice, isSelected)

                        Spacer(Modifier.width(20.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = profile.label,
                                    fontSize = 16.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Text(
                                        text = "✓",
                                        color = Color(0xFF8AB4F8),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = profile.description,
                                color = if (isSelected) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.3f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // Default player
        Text(
            text = "Плеєр за замовчуванням",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PlayerType.entries.forEach { type ->
                val isSelected = type == playerType
                Surface(
                    onClick = { viewModel.setPlayerType(type) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) Color(0xFF1E3A5F) else Color(0xFF1A1A1A),
                        focusedContainerColor = Color(0xFF3B82F6),
                        contentColor = if (isSelected) Color(0xFF8AB4F8) else Color(0xFFE1E1E1),
                        focusedContentColor = Color.White
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = type.label,
                            fontSize = 16.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Text(
                                text = "✓",
                                color = Color(0xFF8AB4F8),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PhoneSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val performanceProfile by viewModel.performanceProfile.collectAsState()
    val playerType by viewModel.playerType.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val context = LocalContext.current
    val hardwareClass = remember { getDeviceClass(context) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(scrollState)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0A0A))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text("НАЛАШТУВАННЯ", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // Update Section
            Text("Оновлення", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (updateState is UpdateState.Idle || updateState is UpdateState.UpToDate || updateState is UpdateState.Error) {
                            viewModel.checkForUpdates()
                        } else if (updateState is UpdateState.NewVersionAvailable) {
                            viewModel.downloadAndInstallUpdate((updateState as UpdateState.NewVersionAvailable).info)
                        } else if (updateState is UpdateState.PermissionRequired) {
                            viewModel.openInstallPermissionSettings()
                        } else if (updateState is UpdateState.ReadyToInstall) {
                            viewModel.installUpdate()
                        }
                    }
                    .background(Color(0xFF1A1A1D), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Версія додатка", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Поточна версія: ${ua.ukrtv.app.BuildConfig.VERSION_NAME} (${ua.ukrtv.app.BuildConfig.VERSION_CODE})", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                    when (val state = updateState) {
                        is UpdateState.Idle -> Text("Перевірити", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                        is UpdateState.Checking -> Text("Перевірка...", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                        is UpdateState.NewVersionAvailable -> Text("Доступна версія ${state.info.versionName}", color = Color(0xFF8AB4F8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        is UpdateState.UpToDate -> Text("Оновлень немає", color = Color(0xFF4CAF50), fontSize = 13.sp)
                        is UpdateState.Downloading -> Text("Завантаження: ${(state.progress * 100).toInt()}%", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                        is UpdateState.ReadyToInstall -> Text("Готово до встановлення", color = Color(0xFF8AB4F8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        is UpdateState.PermissionRequired -> Text("Дозвольте встановлення", color = Color(0xFFFFA726), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        is UpdateState.Error -> Text(state.message, color = Color(0xFFE53935), fontSize = 13.sp)
                    }
                }
                if (updateState is UpdateState.NewVersionAvailable) {
                    val info = (updateState as UpdateState.NewVersionAvailable).info
                    Spacer(Modifier.height(6.dp))
                    Text("Що нового:\n${info.changelog}", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, lineHeight = 14.sp)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Device info
            Box(
                modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp)).padding(14.dp)
            ) {
                Column {
                    Text("ПРИСТРІЙ", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Hardware: ${hardwareClass.name}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Text("Поточний режим: ${performanceProfile.label}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("Графіка та продуктивність", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PerformanceProfile.entries.forEach { profile ->
                    val isSelected = profile == performanceProfile
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setPerformanceProfile(profile) }
                            .background(if (isSelected) Color(0xFF1E3A5F) else Color(0xFF1A1A1D), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PreviewCardPhone(deviceClass = resolveDeviceClass(context, profile), isActive = isSelected)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(profile.label, color = Color.White, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    if (isSelected) Text("✓", color = Color(0xFF8AB4F8), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(profile.description, color = Color.White.copy(alpha = if (isSelected) 0.5f else 0.3f), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("Плеєр за замовчуванням", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PlayerType.entries.forEach { type ->
                    val isSelected = type == playerType
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setPlayerType(type) }
                            .background(if (isSelected) Color(0xFF1E3A5F) else Color(0xFF1A1A1D), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(type.label, color = Color.White, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            if (isSelected) Text("✓", color = Color(0xFF8AB4F8), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PreviewCardPhone(deviceClass: DeviceClass, isActive: Boolean) {
    val cardSize = when (deviceClass) {
        DeviceClass.LOW -> 40.dp
        DeviceClass.MID -> 48.dp
        DeviceClass.HIGH -> 56.dp
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(cardSize)
                .clip(RoundedCornerShape(4.dp))
                .background(if (deviceClass == DeviceClass.HIGH) Color(0xFF2A2A2A) else Color(0xFF222222))
                .then(if (isActive) Modifier.border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp)) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.fillMaxWidth(0.6f).fillMaxHeight(0.75f).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3D3D3D)))
        }
        Spacer(Modifier.height(4.dp))
        Text(deviceClass.name, color = Color.White.copy(alpha = if (isActive) 0.8f else 0.4f), fontSize = 8.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PreviewCard(deviceClass: DeviceClass, isActive: Boolean) {
    val cardSize = when (deviceClass) {
        DeviceClass.LOW -> 48.dp
        DeviceClass.MID -> 56.dp
        DeviceClass.HIGH -> 64.dp
    }
    val glowAlpha = if (isActive && deviceClass == DeviceClass.HIGH) 0.6f else 0f
    val animatedGlow by animateFloatAsState(
        targetValue = if (isActive && deviceClass == DeviceClass.HIGH) 1f else 0f,
        animationSpec = tween(400),
        label = "previewGlow"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val baseMod = Modifier
            .size(cardSize)
            .clip(RoundedCornerShape(6.dp))
        val bgMod = when (deviceClass) {
            DeviceClass.LOW -> baseMod.background(Color(0xFF2A2A2A))
            DeviceClass.MID -> baseMod.background(Brush.verticalGradient(listOf(Color(0xFF2A2A2A), Color(0xFF1E1E1E))))
            DeviceClass.HIGH -> baseMod.background(Brush.verticalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF1E1E1E))))
        }
        val borderMod = if (isActive) {
            val borderColor = when (deviceClass) {
                DeviceClass.HIGH -> Color.White
                DeviceClass.MID -> Color(0xFF6E85B7)
                DeviceClass.LOW -> Color.White.copy(alpha = 0.6f)
            }
            bgMod.then(Modifier.border(2.dp, borderColor, RoundedCornerShape(6.dp)))
        } else bgMod
        val glowMod = if (animatedGlow > 0.5f && isActive) {
            borderMod.then(Modifier.shadow(12.dp, RoundedCornerShape(6.dp), ambientColor = Color(0xFF6E85B7).copy(alpha = 0.5f)))
        } else borderMod

        Box(
            modifier = glowMod,
            contentAlignment = Alignment.Center
        ) {
            // Mini poster placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .fillMaxHeight(0.8f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF3D3D3D))
            )
            // Provider badge dot
            if (deviceClass == DeviceClass.HIGH) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF6B35))
                        .padding(2.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = deviceClass.name,
            color = Color.White.copy(alpha = if (isActive) 0.9f else 0.5f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        val decodeSize = when (deviceClass) {
            DeviceClass.LOW -> "120×180"
            DeviceClass.MID -> "180×270"
            DeviceClass.HIGH -> "300×450"
        }
        val effects = when (deviceClass) {
            DeviceClass.LOW -> "Без анімацій"
            DeviceClass.MID -> "Базові ефекти"
            DeviceClass.HIGH -> "Повні ефекти"
        }
        val homeItems = when (deviceClass) {
            DeviceClass.LOW -> 8
            DeviceClass.MID -> 15
            DeviceClass.HIGH -> 30
        }
        Text(
            text = "$decodeSize · $homeItems карток\n$effects",
            color = Color.White.copy(alpha = if (isActive) 0.7f else 0.35f),
            fontSize = 7.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 9.sp
        )
    }
}
