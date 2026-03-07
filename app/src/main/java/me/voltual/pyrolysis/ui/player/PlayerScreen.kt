//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.


package me.voltual.pyrolysis.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import me.voltual.pyrolysis.core.ui.theme.BBQIconButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import master.flame.danmaku.danmaku.parser.android.BiliDanmukuParser
import master.flame.danmaku.ui.widget.DanmakuView
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel = viewModel(), onBack: () -> Unit) { // 添加 onBack 回调
    val uiState by viewModel.playerUiState.collectAsState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when (val state = uiState) {
            is PlayerUiState.Loading -> {
                CircularProgressIndicator()
                Text(text = "加载中...", color = Color.White, modifier = Modifier.align(Alignment.BottomCenter))
            }
            is PlayerUiState.Error -> {
                Text(text = "加载失败: ${state.message}", color = Color.White)
            }
            is PlayerUiState.Success -> {
                PlayerSuccessContent(state = state, viewModel = viewModel, onBack = onBack) 
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSuccessContent(state: PlayerUiState.Success, viewModel: PlayerViewModel, onBack: () -> Unit) { 
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val activity = context as Activity
    val window = activity.window
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val initialBrightness = remember { window.attributes.screenBrightness }
    var currentBrightness by remember { mutableStateOf(if (initialBrightness < 0) 0.5f else initialBrightness) }

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val animatedScale by animateFloatAsState(targetValue = scale, label = "scale")
    val animatedOffsetX by animateFloatAsState(targetValue = offsetX, label = "offsetX")
    val animatedOffsetY by animateFloatAsState(targetValue = offsetY, label = "offsetY")

    val ijkPlayer = remember {
        IjkMediaPlayer.loadLibrariesOnce(null)
        IjkMediaPlayer().apply {
            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36"
            val referer = "https://www.bilibili.com/"
            val headers = "User-Agent: $userAgent\r\nReferer: $referer\r\n"
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "headers", headers)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 4)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1)
            setScreenOnWhilePlaying(true)
        }
    }
    val danmakuView = remember { DanmakuView(context) }
    val danmakuContext = remember {
        DanmakuContext.create().apply {
            setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3f)
            setDuplicateMergingEnabled(false)
            setScrollSpeedFactor(1.2f)
            setScaleTextSize(settings.danmakuSize)
            setMaximumLines(hashMapOf(BaseDanmaku.TYPE_SCROLL_RL to 8))
            preventOverlapping(null)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (ijkPlayer.isPlaying) {
                        ijkPlayer.pause()
                        danmakuView.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isPlaying) {
                        ijkPlayer.start()
                        danmakuView.resume()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            window.attributes = window.attributes.apply { screenBrightness = initialBrightness }
            ijkPlayer.release()
            danmakuView.release()
        }
    }
    
    LaunchedEffect(controlsVisible) {
        if (controlsVisible && drawerState.isClosed) {
            delay(5000)
            controlsVisible = false
        }
    }

    LaunchedEffect(isPlaying) {
        while(isPlaying) {
            currentPosition = ijkPlayer.currentPosition
            delay(500)
        }
    }

    LaunchedEffect(settings.danmakuSize) {
        danmakuContext.setScaleTextSize(settings.danmakuSize)
    }
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            SettingsDrawer(
                settings = settings,
                onScaleModeChange = { viewModel.updateScaleMode(it) },
                onDanmakuSizeChange = { viewModel.updateDanmakuSize(it) },
                currentVolume = currentVolume,
                maxVolume = maxVolume,
                onVolumeChange = { newVolume ->
                    currentVolume = newVolume
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                },
                currentBrightness = currentBrightness,
                onBrightnessChange = { newBrightness ->
                    currentBrightness = newBrightness
                    window.attributes = window.attributes.apply { screenBrightness = newBrightness }
                }
            )
        }
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val screenWidth = this.maxWidth.value
            val screenHeight = this.maxHeight.value

            val videoContainerModifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            val newOffsetX = offsetX + pan.x
                            val newOffsetY = offsetY + pan.y
                            val maxOffsetX = (screenWidth * (scale - 1)) / 2
                            val maxOffsetY = (screenHeight * (scale - 1)) / 2
                            offsetX = newOffsetX.coerceIn(-maxOffsetX, maxOffsetY)
                            offsetY = newOffsetY.coerceIn(-maxOffsetY, maxOffsetY)
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                }

            Box(modifier = videoContainerModifier) {
                val screenRatio = screenWidth / screenHeight
                val videoRatio = if (videoWidth > 0 && videoHeight > 0) {
                    videoWidth.toFloat() / videoHeight.toFloat()
                } else { 16f / 9f }

                val videoLayoutModifier = Modifier
                    .graphicsLayer(
                        scaleX = animatedScale,
                        scaleY = animatedScale,
                        translationX = animatedOffsetX,
                        translationY = animatedOffsetY
                    )
                    .run {
                        when (settings.scaleMode) {
                            VideoScaleMode.FILL -> fillMaxSize()
                            VideoScaleMode.FIT -> {
                                if (videoRatio > screenRatio) fillMaxWidth().aspectRatio(videoRatio)
                                else fillMaxHeight().aspectRatio(videoRatio)
                            }
                            VideoScaleMode.ZOOM -> {
                                if (videoRatio > screenRatio) fillMaxHeight().aspectRatio(videoRatio)
                                else fillMaxWidth().aspectRatio(videoRatio)
                            }
                        }
                    }
                    .align(Alignment.Center)

                AndroidView(
                    factory = {
                        SurfaceView(it).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    ijkPlayer.setSurface(holder.surface)
                                }
                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    ijkPlayer.setSurface(null)
                                }
                            })
                        }
                    },
                    modifier = videoLayoutModifier
                )
            }

            AndroidView(factory = { danmakuView }, modifier = Modifier.fillMaxSize())

            // cc/bbq/xq/bot/ui/player/PlayerScreen.kt

            PlayerControls(
                title = state.title,
                isVisible = controlsVisible,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                currentPosition = currentPosition,
                duration = duration,
                onPlayPauseClicked = {
                    if (isPlaying) {
                        ijkPlayer.pause()
                        danmakuView.pause()
                    } else {
                        ijkPlayer.start()
                        danmakuView.resume()
                    }
                    isPlaying = !isPlaying
                },
                onSeek = { newPosition ->
                    ijkPlayer.seekTo(newPosition)
                    danmakuView.seekTo(newPosition)
                    currentPosition = newPosition
                },
                onBackClicked = onBack, 
                onSettingsClicked = { scope.launch { drawerState.open() } }
            )
        }
    }

    LaunchedEffect(state.videoUrl, state.danmakuData) {
        ijkPlayer.apply {
            dataSource = state.videoUrl
            prepareAsync()
            setOnPreparedListener { mp ->
                duration = mp.duration
                videoWidth = mp.videoWidth
                videoHeight = mp.videoHeight
                isPlaying = true
                isBuffering = false
                mp.start()
                danmakuView.start(mp.currentPosition)
            }
            setOnCompletionListener { isPlaying = false }
            setOnInfoListener { _, what, _ ->
                when (what) {
                    IMediaPlayer.MEDIA_INFO_BUFFERING_START -> isBuffering = true
                    IMediaPlayer.MEDIA_INFO_BUFFERING_END -> isBuffering = false
                }
                true
            }
        }

        val parser = createParser(state.danmakuData)
        danmakuView.setCallback(object : DrawHandler.Callback {
            override fun prepared() { danmakuView.start() }
            override fun updateTimer(timer: DanmakuTimer) { timer.update(ijkPlayer.currentPosition) }
            override fun danmakuShown(danmaku: BaseDanmaku?) {}
            override fun drawingFinished() {}
        })
        danmakuView.prepare(parser, danmakuContext)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDrawer(
    settings: PlayerSettings,
    onScaleModeChange: (VideoScaleMode) -> Unit,
    onDanmakuSizeChange: (Float) -> Unit,
    currentVolume: Int,
    maxVolume: Int,
    onVolumeChange: (Int) -> Unit,
    currentBrightness: Float,
    onBrightnessChange: (Float) -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp)
    ) {
        Text(
            "播放设置",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleLarge
        )
        HorizontalDivider()
        Column(modifier = Modifier.padding(16.dp)) {
            Text("缩放模式", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            
            val scaleOptions = mapOf(
                VideoScaleMode.FIT to "适应屏幕",
                VideoScaleMode.FILL to "拉伸填充",
                VideoScaleMode.ZOOM to "缩放填充"
            )
            scaleOptions.forEach { (mode, text) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (settings.scaleMode == mode),
                            onClick = { onScaleModeChange(mode) }
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (settings.scaleMode == mode),
                        onClick = { onScaleModeChange(mode) }
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text("弹幕字号", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TextFields, contentDescription = "弹幕字号", modifier = Modifier.padding(end = 8.dp))
                Slider(
                    value = settings.danmakuSize,
                    onValueChange = onDanmakuSizeChange,
                    modifier = Modifier.weight(1f),
                    valueRange = 0.5f..2.5f
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text("亮度", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BrightnessMedium, contentDescription = "亮度", modifier = Modifier.padding(end = 8.dp))
                Slider(
                    value = currentBrightness,
                    onValueChange = onBrightnessChange,
                    modifier = Modifier.weight(1f),
                    valueRange = 0.01f..1.0f
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("音量", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "音量", modifier = Modifier.padding(end = 8.dp))
                Slider(
                    value = currentVolume.toFloat(),
                    onValueChange = { onVolumeChange(it.toInt()) },
                    modifier = Modifier.weight(1f),
                    valueRange = 0f..maxVolume.toFloat(),
                    steps = maxVolume - 1
                )
            }
        }
    }
}

@Composable
fun PlayerControls(
    title: String,
    isVisible: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    duration: Long,
    onPlayPauseClicked: () -> Unit,
    onSeek: (Long) -> Unit,
    onBackClicked: () -> Unit,
    onSettingsClicked: () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.4f))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BBQIconButton(onClick = onBackClicked, icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                Text(text = title, color = Color.White, fontSize = 18.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 16.dp).weight(1f))
                BBQIconButton(onClick = onSettingsClicked, icon = Icons.Default.Settings, contentDescription = "设置", tint = Color.White)
            }

            if (isBuffering) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BBQIconButton(onClick = onPlayPauseClicked, icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "播放/暂停", tint = Color.White)
                    Text(text = formatDuration(currentPosition), color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { onSeek(it.toLong()) },
                        modifier = Modifier.weight(1f),
                        valueRange = 0f..duration.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = Color.Gray)
                    )
                    Text(text = formatDuration(duration), color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis)
    val hours = TimeUnit.SECONDS.toHours(seconds)
    val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
    val remainingSeconds = seconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds)
    } else {
        String.format("%02d:%02d", minutes, remainingSeconds)
    }
}

private fun createParser(danmakuData: ByteArray?): BaseDanmakuParser {
    if (danmakuData == null) {
        return object : BaseDanmakuParser() {
            override fun parse() = master.flame.danmaku.danmaku.model.android.Danmakus()
        }
    }
    val loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI)
    loader.load(ByteArrayInputStream(danmakuData))
    val parser = BiliDanmukuParser()
    parser.load(loader.dataSource)
    return parser
}