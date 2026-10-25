package com.example.sinilinkcontrol

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class MusicTheme { OFF, ON1, ON2 }

@SuppressLint("MissingPermission")
fun triggerDoubleTickHaptic(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            if (vibrator.hasVibrator()) {
                val timings = longArrayOf(0, 12, 40, 12)
                val amplitudes = intArrayOf(0, 180, 0, 180)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator.vibrate(effect)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) {
                val timings = longArrayOf(0, 12, 40, 12)
                val amplitudes = intArrayOf(0, 180, 0, 180)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator.vibrate(effect)
            }
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 12, 40, 12), -1)
            }
        }
    } catch (_: Exception) {}
}

fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "bounce"
    )

    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    try {
                        tryAwaitRelease()
                    } catch (_: Exception) {}
                    isPressed = false
                },
                onTap = {
                    triggerDoubleTickHaptic(context)
                    onClick()
                }
            )
        }
}

@Composable
fun AdjacentButtonGroup(
    items: List<Pair<String, () -> Unit>>,
    selectedIndex: Int,
    isDark: Boolean,
    overridePrimary: Color? = null,
    overrideOnPrimary: Color? = null,
    overrideSurface: Color? = null,
    overrideOnSurface: Color? = null,
    modifier: Modifier = Modifier
) {
    var pressedIndex by remember { mutableIntStateOf(-1) }
    val context = LocalContext.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { index, (label, onClick) ->
            val isPressed = (pressedIndex == index)
            val isNeighborPressed = (pressedIndex != -1 && (pressedIndex == index - 1 || pressedIndex == index + 1))

            val targetWeight = when {
                isPressed -> 1.15f
                isNeighborPressed -> 0.925f
                else -> 1.0f
            }
            val animWeight by animateFloatAsState(
                targetValue = targetWeight,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                label = "weight"
            )

            val isSelected = (index == selectedIndex)
            val targetCorner = when {
                isPressed -> 16.dp
                isSelected -> 14.dp
                else -> 24.dp
            }
            val animCorner by animateDpAsState(
                targetValue = targetCorner,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                label = "corner"
            )

            val targetOffset = when {
                isPressed && index == 0 -> 2.dp
                isPressed && index == items.size - 1 -> (-2).dp
                else -> 0.dp
            }
            val animOffset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                label = "offset"
            )

            val primaryColor = overridePrimary ?: MaterialTheme.colorScheme.primary
            val onPrimaryColor = overrideOnPrimary ?: MaterialTheme.colorScheme.onPrimary
            val surfaceColor = overrideSurface ?: MaterialTheme.colorScheme.surface
            val onSurfaceColor = overrideOnSurface ?: MaterialTheme.colorScheme.onSurface

            val bgColor by animateColorAsState(
                targetValue = if (isSelected) primaryColor else surfaceColor,
                animationSpec = tween(400),
                label = "bgColor"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) onPrimaryColor else onSurfaceColor,
                animationSpec = tween(400),
                label = "contentColor"
            )

            Box(
                modifier = Modifier
                    .weight(animWeight)
                    .clip(RoundedCornerShape(animCorner))
                    .background(bgColor)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressedIndex = index
                                try {
                                    tryAwaitRelease()
                                } catch (_: Exception) {}
                                pressedIndex = -1
                            },
                            onTap = {
                                triggerDoubleTickHaptic(context)
                                onClick()
                            }
                        )
                    }
                    .padding(vertical = 14.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    modifier = Modifier.offset(x = animOffset)
                )
            }
        }
    }
}

@Composable
fun AdjacentIconButtonGroup(
    items: List<Triple<ImageVector, Boolean, () -> Unit>>,
    isDark: Boolean,
    overridePrimary: Color? = null,
    overrideOnPrimary: Color? = null,
    overrideSurface: Color? = null,
    overrideOnSurface: Color? = null,
    modifier: Modifier = Modifier
) {
    var pressedIndex by remember { mutableIntStateOf(-1) }
    val context = LocalContext.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { index, (icon, isSelected, onClick) ->
            val isPressed = (pressedIndex == index)
            val isNeighborPressed = (pressedIndex != -1 && (pressedIndex == index - 1 || pressedIndex == index + 1))

            val targetWeight = when {
                isPressed -> 1.15f
                isNeighborPressed -> 0.925f
                else -> 1.0f
            }
            val animWeight by animateFloatAsState(
                targetValue = targetWeight,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                label = "weight"
            )

            val targetCorner = when {
                isPressed -> 16.dp
                isSelected -> 16.dp
                else -> 32.dp
            }
            val animCorner by animateDpAsState(
                targetValue = targetCorner,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                label = "corner"
            )

            val targetOffset = when {
                isPressed && index == 0 -> 2.dp
                isPressed && index == items.size - 1 -> (-2).dp
                else -> 0.dp
            }
            val animOffset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                label = "offset"
            )

            val primaryColor = overridePrimary ?: MaterialTheme.colorScheme.primary
            val onPrimaryColor = overrideOnPrimary ?: MaterialTheme.colorScheme.onPrimary
            val surfaceColor = overrideSurface ?: MaterialTheme.colorScheme.surface
            val onSurfaceColor = overrideOnSurface ?: MaterialTheme.colorScheme.onSurface

            val bgColor by animateColorAsState(
                targetValue = if (isSelected) primaryColor else surfaceColor,
                animationSpec = tween(400),
                label = "bgColor"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) onPrimaryColor else onSurfaceColor,
                animationSpec = tween(400),
                label = "contentColor"
            )

            Box(
                modifier = Modifier
                    .weight(animWeight)
                    .clip(RoundedCornerShape(animCorner))
                    .background(bgColor)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressedIndex = index
                                try {
                                    tryAwaitRelease()
                                } catch (_: Exception) {}
                                pressedIndex = -1
                            },
                            onTap = {
                                triggerDoubleTickHaptic(context)
                                onClick()
                            }
                        )
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.offset(x = animOffset)
                )
            }
        }
    }
}

fun cropCenterSquareNoLetterbox(bitmap: Bitmap): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return bitmap
    val cropHeight = (h * 0.72f).toInt().coerceAtLeast(1)
    val startY = (h - cropHeight) / 2
    return try {
        Bitmap.createBitmap(bitmap, 0, startY, w, cropHeight)
    } catch (_: Exception) {
        bitmap
    }
}

fun createDynamicColor(domColor: Color, sysColor: Color): Color {
    val domHsv = FloatArray(3)
    val sysHsv = FloatArray(3)

    android.graphics.Color.colorToHSV(android.graphics.Color.rgb(
        (domColor.red * 255).toInt(),
        (domColor.green * 255).toInt(),
        (domColor.blue * 255).toInt()
    ), domHsv)

    android.graphics.Color.colorToHSV(android.graphics.Color.rgb(
        (sysColor.red * 255).toInt(),
        (sysColor.green * 255).toInt(),
        (sysColor.blue * 255).toInt()
    ), sysHsv)

    val targetHue = domHsv[0]
    val targetSat = sysHsv[1]
    val targetVal = sysHsv[2]

    return Color(android.graphics.Color.HSVToColor(floatArrayOf(targetHue, targetSat, targetVal)))
}

class MainActivity : ComponentActivity() {
    private lateinit var bleManager: SinilinkBleManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (!bleManager.state.value.isConnected) {
            bleManager.startScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleManager = SinilinkBleManager.getInstance(this)

        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
        }
        requestPermissionLauncher.launch(permissions)

        val m3eTypography = Typography()

        setContent {
            val context = LocalContext.current
            val appSettings = remember { AppSettings(context) }

            var themeMode by remember { mutableStateOf(ThemeMode.entries.getOrElse(appSettings.defaultTheme) { ThemeMode.SYSTEM }) }
            var musicTheme by remember { mutableStateOf(MusicTheme.entries.getOrElse(appSettings.defaultColors) { MusicTheme.OFF }) }
            var showLogs by remember { mutableStateOf(appSettings.showLogs) }
            var runInBackground by remember { mutableStateOf(appSettings.runInBackground) }

            bleManager.pendingAutoEq = appSettings.autoApplyEq

            val bleState by bleManager.state.collectAsState()

            // Foreground Service for background connection
            LaunchedEffect(bleState.isConnected, runInBackground) {
                if (runInBackground && bleState.isConnected) {
                    BleForegroundService.start(context)
                } else {
                    BleForegroundService.stop(context)
                }
            }

            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            val activeMedia by MediaNotificationService.mediaState.collectAsState()

            // Extract color from media
            val domColorInt = activeMedia.dominantColor
            val domColor = if (domColorInt != null) Color(domColorInt) else null

            var baseColorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (isDark) darkColorScheme() else lightColorScheme()
            }

            // ON2: Apply extracted color globally including background, surface, and text
            if (musicTheme == MusicTheme.ON2 && domColor != null) {
                val tintedPrimary = createDynamicColor(domColor, baseColorScheme.primary)
                val tintedOnPrimary = createDynamicColor(domColor, baseColorScheme.onPrimary)
                val tintedButtonSurface = createDynamicColor(domColor, baseColorScheme.surface)
                val tintedCardSurface = createDynamicColor(domColor, baseColorScheme.surfaceVariant)
                val tintedOnSurface = createDynamicColor(domColor, baseColorScheme.onSurface)
                val tintedOnSurfaceVariant = createDynamicColor(domColor, baseColorScheme.onSurfaceVariant)
                val tintedBg = createDynamicColor(domColor, baseColorScheme.background)
                val tintedOnBg = createDynamicColor(domColor, baseColorScheme.onBackground)

                val animPrimary by animateColorAsState(targetValue = tintedPrimary, animationSpec = tween(400), label = "animPrimary")
                val animCardSurface by animateColorAsState(targetValue = tintedCardSurface, animationSpec = tween(400), label = "animCardSurface")
                val animButtonSurface by animateColorAsState(targetValue = tintedButtonSurface, animationSpec = tween(400), label = "animButtonSurface")

                baseColorScheme = baseColorScheme.copy(
                    primary = animPrimary,
                    onPrimary = tintedOnPrimary,
                    surface = animButtonSurface,
                    onSurface = tintedOnSurface,
                    surfaceVariant = animCardSurface,
                    onSurfaceVariant = tintedOnSurfaceVariant,
                    secondaryContainer = animButtonSurface,
                    onSecondaryContainer = tintedOnSurfaceVariant,
                    background = tintedBg,
                    onBackground = tintedOnBg
                )
            }

            var showFullScreenPlayer by remember { mutableStateOf(false) }

            MaterialTheme(colorScheme = baseColorScheme, typography = m3eTypography) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0)
                    ) { innerPadding ->
                        ControllerScreen(
                            bleManager = bleManager,
                            themeMode = themeMode,
                            onThemeModeChange = { themeMode = it },
                            musicTheme = musicTheme,
                            onMusicThemeChange = { newTheme ->
                                if (!MediaNotificationService.isPermissionGranted(context)) {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                } else {
                                    musicTheme = newTheme
                                    appSettings.defaultColors = newTheme.ordinal
                                }
                            },
                            activeMedia = activeMedia,
                            onExpandPlayer = { showFullScreenPlayer = true },
                            isDark = isDark,
                            sectionThemeColor = if (musicTheme == MusicTheme.ON1) domColor else null,
                            appSettings = appSettings,
                            showLogs = showLogs,
                            onShowLogsChange = { showLogs = it },
                            modifier = Modifier.padding(top = innerPadding.calculateTopPadding())
                        )
                    }

                    AnimatedVisibility(
                        visible = showFullScreenPlayer,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        FullScreenPlayer(
                            activeMedia = activeMedia,
                            onClose = { showFullScreenPlayer = false },
                            bleManager = bleManager,
                            isDark = isDark,
                            appSettings = appSettings
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val appSettings = AppSettings(this)
        if (!appSettings.runInBackground) {
            bleManager.close()
        }
    }
}

@Composable
fun ControllerScreen(
    bleManager: SinilinkBleManager,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    musicTheme: MusicTheme,
    onMusicThemeChange: (MusicTheme) -> Unit,
    activeMedia: ActiveMedia,
    onExpandPlayer: () -> Unit,
    isDark: Boolean,
    sectionThemeColor: Color?,
    appSettings: AppSettings,
    showLogs: Boolean,
    onShowLogsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by bleManager.state.collectAsState()
    var sliderValue by remember(state.volume) { mutableFloatStateOf(state.volume.toFloat()) }
    var savedVolume by remember { mutableFloatStateOf(15f) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isInputCollapsed by remember { mutableStateOf(appSettings.isInputCollapsed) }
    val listState = rememberLazyListState()

    var isEqVisible by remember { mutableStateOf(appSettings.isEqVisible) }

    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            listState.animateScrollToItem(state.logs.size - 1)
        }
    }

    // Auto dismiss warnings after 10 seconds
    LaunchedEffect(state.connectionKilled, state.scanWarnings, state.pcAudioBug) {
        if (state.connectionKilled || state.scanWarnings || state.pcAudioBug) {
            delay(10000)
            bleManager.clearWarnings()
        }
    }

    // Section 2 Colors - Match album art hue for ON1 partial dynamic
    val sysPrimary = MaterialTheme.colorScheme.primary
    val sysSurface = MaterialTheme.colorScheme.surfaceVariant

    val rawSec2Primary = if (sectionThemeColor != null) {
        createDynamicColor(sectionThemeColor, sysPrimary)
    } else {
        sysPrimary
    }

    val rawSec2OnPrimary = if (sectionThemeColor != null) {
        createDynamicColor(sectionThemeColor, MaterialTheme.colorScheme.onPrimary)
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    val rawSec2Surface = if (sectionThemeColor != null) {
        createDynamicColor(sectionThemeColor, sysSurface)
    } else {
        sysSurface
    }

    val rawSec2OnSurface = if (sectionThemeColor != null) {
        createDynamicColor(sectionThemeColor, MaterialTheme.colorScheme.onSurface)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val brightAlbumTint = if (sectionThemeColor != null) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(android.graphics.Color.rgb(
            (sectionThemeColor.red * 255).toInt(),
            (sectionThemeColor.green * 255).toInt(),
            (sectionThemeColor.blue * 255).toInt()
        ), hsv)
        hsv[1] = 0.18f // low desaturated pastel
        hsv[2] = 0.98f // max brightness
        Color(android.graphics.Color.HSVToColor(hsv))
    } else {
        Color.White
    }

    val sec2Surface by animateColorAsState(targetValue = rawSec2Surface, animationSpec = tween(400), label = "sec2Surface")
    val sec2Primary by animateColorAsState(targetValue = rawSec2Primary, animationSpec = tween(400), label = "sec2Primary")
    val sec2OnPrimary by animateColorAsState(targetValue = rawSec2OnPrimary, animationSpec = tween(400), label = "sec2OnPrimary")
    val sec2OnSurface by animateColorAsState(targetValue = rawSec2OnSurface, animationSpec = tween(400), label = "sec2OnSurface")

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(2.dp))

        // Top Panel: Header with Big Inner Icon (92.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_logo),
                    contentDescription = "Logo",
                    modifier = Modifier.size(92.dp),
                    tint = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        "SiniRemote",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        state.statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Music Theme Toggle
                IconButton(
                    onClick = {
                        onMusicThemeChange(
                            when (musicTheme) {
                                MusicTheme.OFF -> MusicTheme.ON1
                                MusicTheme.ON1 -> MusicTheme.ON2
                                MusicTheme.ON2 -> MusicTheme.OFF
                            }
                        )
                    },
                    modifier = Modifier.bounceClick {  }
                ) {
                    Icon(
                        imageVector = when(musicTheme) {
                            MusicTheme.OFF -> Icons.Default.Palette
                            MusicTheme.ON1 -> Icons.Default.LibraryMusic
                            MusicTheme.ON2 -> Icons.Default.Audiotrack
                        },
                        contentDescription = "Music Theme",
                        tint = if (musicTheme != MusicTheme.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                    )
                }

                // Settings Toggle Popup
                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.bounceClick { }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        if (showSettingsDialog) {
            SettingsDialog(
                appSettings = appSettings,
                onDismiss = { showSettingsDialog = false },
                onSettingsChanged = {
                    onThemeModeChange(ThemeMode.entries.getOrElse(appSettings.defaultTheme) { ThemeMode.SYSTEM })
                    onMusicThemeChange(MusicTheme.entries.getOrElse(appSettings.defaultColors) { MusicTheme.OFF })
                    onShowLogsChange(appSettings.showLogs)
                    bleManager.pendingAutoEq = appSettings.autoApplyEq
                }
            )
        }

        // Warnings Panel
        if (state.connectionKilled || state.pcAudioBug || state.scanWarnings) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().animateContentSize()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        if (state.connectionKilled) {
                            Text(
                                "Connection killed. To reconnect: reboot amplifier or triple-click the volume knob.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        if (state.scanWarnings) {
                            Text(
                                "Multiple scan failures. Try rebooting the Sinilink device.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        if (state.pcAudioBug) {
                            Text(
                                "PC Audio → BT bug: If no sound, restart the amplifier.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // SECTION 1: CONNECTION & INPUT SOURCE (COLLAPSIBLE)
        // ==========================================
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AnimatedVisibility(visible = !isInputCollapsed) {
                // Connection Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                ) {
                    val connIndex = if (!state.isConnected) 0 else -1
                    AdjacentButtonGroup(
                        items = listOf(
                            "SCAN" to { bleManager.startScan() },
                            "KILL CONNECTION" to { bleManager.disconnect() }
                        ),
                        selectedIndex = connIndex,
                        isDark = isDark,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Input Sources Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(topStart = if(isInputCollapsed) 24.dp else 4.dp, topEnd = if(isInputCollapsed) 24.dp else 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp).animateContentSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                isInputCollapsed = !isInputCollapsed 
                                appSettings.isInputCollapsed = isInputCollapsed
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("INPUT SOURCE", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Icon(
                            imageVector = if (isInputCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = "Toggle Input Collapsed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (!isInputCollapsed) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val inputs = listOf(
                            "Bluetooth" to byteArrayOf(0x7E, 0x05, 0x14, 0x00),
                            "AUX" to byteArrayOf(0x7E, 0x05, 0x16, 0x00),
                            "USB" to byteArrayOf(0x7E, 0x05, 0x04, 0x00),
                            "TF Card" to byteArrayOf(0x7E, 0x05, 0x03, 0x00),
                            "PC Audio" to byteArrayOf(0x7E, 0x05, 0x15, 0x00)
                        )
                        inputs.chunked(3).forEach { rowItems ->
                            val selectedIdx = rowItems.indexOfFirst { it.first == state.mode }
                            AdjacentButtonGroup(
                                items = rowItems.map { (name, cmd) -> name to { bleManager.sendCommand(cmd) } },
                                selectedIndex = selectedIdx,
                                isDark = isDark,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // SECTION 2: EQ & MEDIA CONTROLS
        // ==========================================
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Equalizer Card (With SYNC and TONE buttons)
            var showToneSettings by remember { mutableStateOf(false) }

            Card(
                colors = CardDefaults.cardColors(containerColor = sec2Surface),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp).animateContentSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                isEqVisible = !isEqVisible 
                                appSettings.isEqVisible = isEqVisible
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("EQUALIZER", color = sec2Primary, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Sync Button
                            Box(
                                modifier = Modifier
                                    .bounceClick { bleManager.syncAndPlay() }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(sec2Primary.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Sync, contentDescription = "Sync", modifier = Modifier.size(12.dp), tint = sec2Primary)
                                    Text("SYNC", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = sec2Primary)
                                }
                            }

                            // Tone Button Popup
                            Box {
                                Box(
                                    modifier = Modifier
                                        .bounceClick { showToneSettings = true }
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(sec2Primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("TONE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = sec2Primary)
                                }
                                DropdownMenu(
                                    expanded = showToneSettings,
                                    onDismissRequest = { showToneSettings = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Tone ON") },
                                        onClick = {
                                            bleManager.sendCommand(byteArrayOf(0x7E, 0x05, 0x18, 0x00))
                                            bleManager.sendCommand(byteArrayOf(0x7E, 0x05, 78, 0x00))
                                            showToneSettings = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Tone OFF") },
                                        onClick = {
                                            bleManager.sendCommand(byteArrayOf(0x7E, 0x05, 0x18, 0x00))
                                            bleManager.sendCommand(byteArrayOf(0x7E, 0x05, 77, 0x00))
                                            showToneSettings = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (isEqVisible) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val eqs = listOf(
                            "Normal" to byteArrayOf(0x7E, 0x05, 0x09, 0x00),
                            "Rock" to byteArrayOf(0x7E, 0x05, 0x0A, 0x00),
                            "Pop" to byteArrayOf(0x7E, 0x05, 0x0B, 0x00),
                            "Classic" to byteArrayOf(0x7E, 0x05, 0x0C, 0x00),
                            "Jazz" to byteArrayOf(0x7E, 0x05, 0x0D, 0x00),
                            "Country" to byteArrayOf(0x7E, 0x05, 0x0E, 0x00)
                        )
                        eqs.chunked(3).forEach { rowItems ->
                            val selectedIdx = rowItems.indexOfFirst { it.first == state.eq }
                            AdjacentButtonGroup(
                                items = rowItems.map { (name, cmd) -> name to { bleManager.sendCommand(cmd) } },
                                selectedIndex = selectedIdx,
                                isDark = isDark,
                                overridePrimary = sec2Primary,
                                overrideOnPrimary = sec2OnPrimary,
                                overrideSurface = MaterialTheme.colorScheme.surface,
                                overrideOnSurface = sec2OnSurface,
                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Media & Volume Controls Card
            Card(
                colors = CardDefaults.cardColors(containerColor = sec2Surface),
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            ) {
                var isMediaExpanded by remember { mutableStateOf(appSettings.isMediaExpanded) }
                val rotationAngle by animateFloatAsState(targetValue = if (isMediaExpanded) 90f else 0f, label = "chevronRotate")

                if (musicTheme != MusicTheme.OFF && activeMedia.albumArt != null) {
                    AnimatedContent(targetState = isMediaExpanded, label = "MediaLayout") { expanded ->
                        if (expanded) {
                            // Expanded Layout (Pixel style notification layout with low opacity shade)
                            Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                                Image(
                                    bitmap = activeMedia.albumArt.asImageBitmap(),
                                    contentDescription = "Album Art",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Low opacity album color shade layer
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background((sectionThemeColor ?: Color.Black).copy(alpha = 0.45f))
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp).align(Alignment.TopCenter),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .bounceClick { 
                                                isMediaExpanded = false
                                                appSettings.isMediaExpanded = false
                                            }
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(brightAlbumTint.copy(alpha = 0.25f))
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Now Playing", color = brightAlbumTint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Icon(
                                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = "Collapse",
                                                tint = brightAlbumTint,
                                                modifier = Modifier.size(14.dp).rotate(rotationAngle)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = onExpandPlayer,
                                        modifier = Modifier.bounceClick { onExpandPlayer() }
                                    ) {
                                        Icon(Icons.Default.Fullscreen, contentDescription = "Expand", tint = brightAlbumTint)
                                    }
                                }
                                Column(
                                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                                ) {
                                    Text(activeMedia.title, color = brightAlbumTint, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                                    Text(activeMedia.artist, color = brightAlbumTint.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        } else {
                            // Collapsed Widget Style Layout (16:9 Widescreen image)
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .bounceClick { 
                                                isMediaExpanded = true
                                                appSettings.isMediaExpanded = true
                                            }
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(sec2Primary.copy(alpha = 0.12f))
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Now Playing", color = sec2Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Icon(
                                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = "Expand",
                                                tint = sec2Primary,
                                                modifier = Modifier.size(14.dp).rotate(rotationAngle)
                                            )
                                        }
                                    }
                                    IconButton(onClick = onExpandPlayer) {
                                        Icon(Icons.Default.Fullscreen, contentDescription = "Expand", tint = sec2OnSurface)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Image(
                                    bitmap = activeMedia.albumArt.asImageBitmap(),
                                    contentDescription = "Album Art Widescreen",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(16.dp))
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(activeMedia.title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                                Text(activeMedia.artist, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    // Playback controls (Adjacent Physics Icon Buttons)
                    AdjacentIconButtonGroup(
                        items = listOf(
                            Triple(Icons.Default.SkipPrevious, false) {
                                if (state.isConnected) bleManager.sendCommand(byteArrayOf(0x7E, 0x05, 0x07, 0x00))
                                else activeMedia.controller?.transportControls?.skipToPrevious()
                            },
                            Triple(if (state.isPlaying || activeMedia.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, state.isPlaying || activeMedia.isPlaying) {
                                if (state.isConnected) bleManager.sendCommand(byteArrayOf(0x7E, 0x05, 0x01, 0x00))
                                else {
                                    if (activeMedia.isPlaying) activeMedia.controller?.transportControls?.pause()
                                    else activeMedia.controller?.transportControls?.play()
                                }
                            },
                            Triple(Icons.Default.SkipNext, false) {
                                if (state.isConnected) bleManager.sendCommand(byteArrayOf(0x7E, 0x05, 0x08, 0x00))
                                else activeMedia.controller?.transportControls?.skipToNext()
                            }
                        ),
                        isDark = isDark,
                        overridePrimary = sec2Primary,
                        overrideOnPrimary = sec2OnPrimary,
                        overrideSurface = MaterialTheme.colorScheme.surface,
                        overrideOnSurface = sec2OnSurface
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Volume Row: [Box Volume Number] --- Slider --- [Box Mute Icon]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(sec2Primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${sliderValue.toInt()}",
                                fontWeight = FontWeight.Black,
                                color = sec2Primary,
                                fontSize = 14.sp
                            )
                        }

                        Slider(
                            value = sliderValue,
                            onValueChange = { newVal -> sliderValue = newVal },
                            onValueChangeFinished = {
                                val vol = sliderValue.toInt()
                                val cmd = byteArrayOf(0x7E, 0x0F, 0x1D, vol.toByte()) + ByteArray(10)
                                bleManager.sendCommand(cmd)
                            },
                            valueRange = 0f..30f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = sec2Primary,
                                activeTrackColor = sec2Primary,
                                inactiveTrackColor = sec2Primary.copy(alpha = 0.24f)
                            )
                        )

                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 36.dp)
                                .bounceClick {
                                    if (sliderValue > 0f) {
                                        savedVolume = sliderValue
                                        sliderValue = 0f
                                        val cmd = byteArrayOf(0x7E, 0x0F, 0x1D, 0) + ByteArray(10)
                                        bleManager.sendCommand(cmd)
                                    } else {
                                        val restore = if (savedVolume > 0f) savedVolume else 15f
                                        sliderValue = restore
                                        val cmd = byteArrayOf(0x7E, 0x0F, 0x1D, restore.toInt().toByte()) + ByteArray(10)
                                        bleManager.sendCommand(cmd)
                                    }
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(sec2Primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (sliderValue > 0f) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = "Mute Toggle",
                                tint = sec2Primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Expandable Live BLE Console Log Box (at the very bottom)
        if (showLogs) {
            val context = LocalContext.current
            Card(
                modifier = Modifier.fillMaxWidth().height(200.dp).animateContentSize(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("DEBUG LOGS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "COPY",
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.bounceClick {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("logs", state.logs.joinToString("\n"))
                                    clipboard.setPrimaryClip(clip)
                                }.padding(4.dp)
                            )
                            Text(
                                "CLEAR",
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.bounceClick {
                                    bleManager.clearLogs()
                                }.padding(4.dp)
                            )
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(state.logs) { log ->
                            Text(
                                text = log,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    appSettings: AppSettings,
    onDismiss: () -> Unit,
    onSettingsChanged: () -> Unit
) {
    var autoEq by remember { mutableStateOf(appSettings.autoApplyEq) }
    var themeIdx by remember { mutableIntStateOf(appSettings.defaultTheme) }
    var colorIdx by remember { mutableIntStateOf(appSettings.defaultColors) }
    var dynTimeout by remember { mutableFloatStateOf(appSettings.timeoutDynamicTheme.toFloat()) }
    var showLogs by remember { mutableStateOf(appSettings.showLogs) }
    var bgRun by remember { mutableStateOf(appSettings.runInBackground) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SiniRemote Settings", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Auto Apply EQ
                Text("Auto Apply EQ on Connect:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                val eqOptions = listOf("Disable", "Normal", "Rock", "Pop", "Classic", "Jazz", "Country")
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        eqOptions.take(4).forEach { option ->
                            FilterChip(
                                selected = autoEq == option,
                                onClick = {
                                    autoEq = option
                                    appSettings.autoApplyEq = option
                                    onSettingsChanged()
                                },
                                label = { Text(option, fontSize = 10.sp) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        eqOptions.drop(4).forEach { option ->
                            FilterChip(
                                selected = autoEq == option,
                                onClick = {
                                    autoEq = option
                                    appSettings.autoApplyEq = option
                                    onSettingsChanged()
                                },
                                label = { Text(option, fontSize = 10.sp) }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Default Theme
                Text("Default Theme:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("System", "Light", "Dark").forEachIndexed { index, name ->
                        FilterChip(
                            selected = themeIdx == index,
                            onClick = {
                                themeIdx = index
                                appSettings.defaultTheme = index
                                onSettingsChanged()
                            },
                            label = { Text(name, fontSize = 11.sp) }
                        )
                    }
                }

                HorizontalDivider()

                // Default Colors
                Text("Default Colors:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Material", "Dynamic", "Dynamic Full").forEachIndexed { index, name ->
                        FilterChip(
                            selected = colorIdx == index,
                            onClick = {
                                colorIdx = index
                                appSettings.defaultColors = index
                                onSettingsChanged()
                            },
                            label = { Text(name, fontSize = 10.sp) }
                        )
                    }
                }

                HorizontalDivider()

                // Dynamic Theme Timeout
                Text("Dynamic Theme Timeout: ${dynTimeout.toInt()}s (0 = Off)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = dynTimeout,
                    onValueChange = {
                        dynTimeout = it
                        appSettings.timeoutDynamicTheme = it.toInt()
                        onSettingsChanged()
                    },
                    valueRange = 0f..30f
                )

                HorizontalDivider()

                // Background Execution Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Run in Background:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Keeps BLE active when app is minimized (stops when disconnected).", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = bgRun,
                        onCheckedChange = {
                            bgRun = it
                            appSettings.runInBackground = it
                            onSettingsChanged()
                        }
                    )
                }

                HorizontalDivider()

                // Show Logs Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Debug Console Logs:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = showLogs,
                        onCheckedChange = {
                            showLogs = it
                            appSettings.showLogs = it
                            onSettingsChanged()
                        }
                    )
                }

                HorizontalDivider()

                // About & Developer GitHub
                val context = LocalContext.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/kxrthikc"))
                            context.startActivity(intent)
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("GitHub", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("github.com/kxrthikc", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open GitHub",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun FullScreenPlayer(
    activeMedia: ActiveMedia,
    onClose: () -> Unit,
    bleManager: SinilinkBleManager,
    isDark: Boolean,
    appSettings: AppSettings
) {
    var bgMode by remember { mutableIntStateOf(appSettings.defaultBackground) } // 0 = color, 1 = blur
    var showEqInFullScreen by remember { mutableStateOf(appSettings.showEqInFullScreen) }
    val domColor = activeMedia.dominantColor?.let { Color(it) } ?: Color.DarkGray
    val state by bleManager.state.collectAsState()

    BackHandler { onClose() }

    // Edge to edge transparent status bar setup
    val context = LocalContext.current as Activity
    LaunchedEffect(Unit) {
        val window = context.window
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        delay(3000)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(Unit) {
        onDispose {
            val window = context.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { bgMode = (bgMode + 1) % 2 })
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    if (dragAmount.y > 50f) {
                        onClose()
                    }
                }
            }
    ) {
        // Full screen background without feathering artifacts covering entire screen
        if (bgMode == 0) {
            Box(modifier = Modifier.fillMaxSize().background(domColor))
        } else {
            if (activeMedia.albumArt != null) {
                val croppedArt = remember(activeMedia.albumArt) { cropCenterSquareNoLetterbox(activeMedia.albumArt) }
                Image(
                    bitmap = croppedArt.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.25f)
                        .blur(50.dp)
                )
                Box(modifier = Modifier.fillMaxSize().background(domColor.copy(alpha = 0.5f)))
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }
        }

        // Two Center-Aligned Component Groups (Neutral Colors in Full Screen)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // COMPONENT 1: Album Art + Title/Artist + Semi-Transparent EQ
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (activeMedia.albumArt != null) {
                    val croppedArt = remember(activeMedia.albumArt) { cropCenterSquareNoLetterbox(activeMedia.albumArt) }
                    Image(
                        bitmap = croppedArt.asImageBitmap(),
                        contentDescription = "Album Art Square",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        showEqInFullScreen = !showEqInFullScreen
                                        appSettings.showEqInFullScreen = showEqInFullScreen
                                    }
                                )
                            }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    activeMedia.title,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.92f)
                )
                Text(
                    activeMedia.artist,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.92f)
                )

                // Neutral Semi-Transparent EQ Buttons
                AnimatedVisibility(visible = showEqInFullScreen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val eqs = listOf(
                            "Normal" to byteArrayOf(0x7E, 0x05, 0x09, 0x00),
                            "Rock" to byteArrayOf(0x7E, 0x05, 0x0A, 0x00),
                            "Pop" to byteArrayOf(0x7E, 0x05, 0x0B, 0x00),
                            "Classic" to byteArrayOf(0x7E, 0x05, 0x0C, 0x00),
                            "Jazz" to byteArrayOf(0x7E, 0x05, 0x0D, 0x00),
                            "Country" to byteArrayOf(0x7E, 0x05, 0x0E, 0x00)
                        )

                        eqs.chunked(3).forEach { rowItems ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowItems.forEach { (name, cmd) ->
                                    val isSelected = state.eq == name
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .bounceClick { bleManager.sendCommand(cmd) }
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isSelected) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.15f))
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // COMPONENT 2: Neutral Media Controls
            Row(
                modifier = Modifier.fillMaxWidth(0.92f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { activeMedia.controller?.transportControls?.skipToPrevious() },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .bounceClick { 
                            if (activeMedia.isPlaying) activeMedia.controller?.transportControls?.pause()
                            else activeMedia.controller?.transportControls?.play()
                        }
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (activeMedia.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(
                    onClick = { activeMedia.controller?.transportControls?.skipToNext() },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}