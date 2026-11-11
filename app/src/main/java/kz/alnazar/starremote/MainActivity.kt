package kz.alnazar.starremote

import android.content.Intent
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kz.alnazar.starremote.control.OpEvent
import kz.alnazar.starremote.ui.Backdrop
import kz.alnazar.starremote.ui.ButtonsScreen
import kz.alnazar.starremote.ui.Chime
import kz.alnazar.starremote.ui.ConnectionScreen
import kz.alnazar.starremote.ui.FeedbackScreen
import kz.alnazar.starremote.ui.Haptics
import kz.alnazar.starremote.ui.HomeScreen
import kz.alnazar.starremote.ui.IosEasing
import kz.alnazar.starremote.ui.LogScreen
import kz.alnazar.starremote.ui.P
import kz.alnazar.starremote.ui.Route
import kz.alnazar.starremote.ui.SettingsRoot
import kz.alnazar.starremote.ui.SpaceScreen
import kz.alnazar.starremote.ui.StarCanvas
import kz.alnazar.starremote.ui.StarSky
import kz.alnazar.starremote.ui.TorrScreen
import kz.alnazar.starremote.ui.TorrWebScreen
import kz.alnazar.starremote.ui.WakeScreen
import kz.alnazar.starremote.ui.WidgetScreen
import kz.alnazar.starremote.ui.rememberTick

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Chime.prepare()
        val app = StarApp.of(this)
        app.journal.info("Приложение открыто (${app.version})")
        setContent { StarRemote(app) }
    }
}

@Composable
private fun StarRemote(app: StarApp) {
    val ctx = LocalContext.current
    val s by app.settings.flow.collectAsState()
    val st by app.controller.status.collectAsState()

    val sky = remember { StarSky() }
    val reduce = remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    SideEffect {
        sky.starCount = s.density
        sky.drift = s.drift
        sky.warpDur = s.warp
        sky.flareSize = s.flare
        sky.hue = s.spaceHue
        sky.light = s.spaceLight
        sky.reduceMotion = reduce
        sky.onFlare = {
            if (s.sound) Chime.play(s.volume)
            Haptics.flare(ctx, s.haptic, s.hapticStrong)
        }
    }

    val layer = rememberGraphicsLayer()
    val tick = rememberTick()
    val backdrop = remember(layer) { Backdrop(layer, tick) }

    val stack = remember { mutableStateListOf(Route.HOME) }
    val route = stack.last()
    var torrUrl by remember { mutableStateOf("") }
    var pushing by remember { mutableStateOf(true) }
    val go: (Route) -> Unit = { r -> if (stack.last() != r) { pushing = true; stack.add(r) } }
    val back: () -> Unit = { if (stack.size > 1) { pushing = false; stack.removeAt(stack.lastIndex) } }
    BackHandler(enabled = stack.size > 1) { back() }

    // события анимации от контроллера
    LaunchedEffect(Unit) {
        app.controller.events.collect { e ->
            when (e) {
                OpEvent.WarpStart -> sky.startWarp()
                is OpEvent.WarpEnd -> sky.endWarp(e.success)
                OpEvent.Fade -> sky.fade()
            }
        }
    }
    // звезда-индикатор следует за реальным статусом Mac
    LaunchedEffect(st.isOn, st.busy) {
        if (st.busy || sky.warping) return@LaunchedEffect
        if (st.isOn && !sky.hasBeacon) sky.showSteady()
        if (!st.isOn && sky.hasBeacon) sky.fade()
    }
    // реальный статус: при открытии и раз в 15 секунд, пока приложение на экране
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { app.controller.refresh(); delay(15_000) }
        }
    }

    val transition = updateTransition(route, label = "nav")
    val starsRunning = route == Route.HOME || route == Route.SPACE || transition.currentState != transition.targetState

    Box(Modifier.fillMaxSize().background(P.spaceDeep)) {
        StarCanvas(sky, layer, tick, starsRunning, Modifier.fillMaxSize())

        transition.AnimatedContent(
            transitionSpec = {
                val spec = tween<IntOffset>(500, easing = IosEasing)
                // экраны непрозрачные; тот, что уходит назад, плавно темнеет (см. затемнение ниже)
                if (targetState.level > initialState.level) {
                    (slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it * 3 / 10 })
                        .apply { targetContentZIndex = 1f }
                } else {
                    (slideInHorizontally(spec) { -it * 3 / 10 } togetherWith slideOutHorizontally(spec) { it })
                        .apply { targetContentZIndex = -1f }
                }
            },
            contentKey = { it },
        ) { r ->
            val dim by this.transition.animateFloat(
                transitionSpec = { tween(500, easing = IosEasing) }, label = "dim",
            ) { e ->
                when {
                    e == EnterExitState.PostExit && pushing -> .5f
                    e == EnterExitState.PreEnter && !pushing -> .5f
                    else -> 0f
                }
            }
            Box(Modifier.fillMaxSize().drawWithContent {
                drawContent()
                if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
            }) {
            when (r) {
                Route.HOME -> HomeScreen(
                    st = st, s = s, sky = sky, backdrop = backdrop,
                    onOn = { Haptics.tap(ctx, s.haptic, s.hapticStrong); app.controller.turnOn() },
                    onOff = { Haptics.tap(ctx, s.haptic, s.hapticStrong); app.controller.turnOff() },
                    onCustom = { b -> Haptics.tap(ctx, s.haptic, s.hapticStrong); app.controller.runCustom(b) },
                    onTorr = {
                        val url = app.controller.torrUrl()
                        when {
                            url == null -> Toast.makeText(ctx, "Не знаю адрес Mac", Toast.LENGTH_SHORT).show()
                            s.torrInApp -> { torrUrl = url; go(Route.TORRWEB) }
                            else -> runCatching {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                        app.journal.info("Открыть TorrServe: ${url ?: "адрес неизвестен"}")
                    },
                    onSettings = { Haptics.tap(ctx, s.haptic, false); go(Route.SETTINGS) },
                )
                Route.SETTINGS -> SettingsRoot(app, go, back)
                Route.CONN -> ConnectionScreen(app, back)
                Route.BUTTONS -> ButtonsScreen(app, back)
                Route.WAKE -> WakeScreen(app, back)
                Route.TORR -> TorrScreen(app, back)
                Route.WIDGET -> WidgetScreen(app, back)
                Route.SPACE -> SpaceScreen(app, sky, back)
                Route.FEEDBACK -> FeedbackScreen(app, back)
                Route.LOG -> LogScreen(app, back)
                Route.TORRWEB -> TorrWebScreen(torrUrl, back)
            }
            }
        }
    }
}
