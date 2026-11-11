package kz.alnazar.starremote.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kz.alnazar.starremote.StarApp
import kz.alnazar.starremote.data.CustomButton
import kz.alnazar.starremote.data.Level
import kz.alnazar.starremote.net.WakeOnLan

enum class Route(val level: Int) {
    HOME(0), SETTINGS(1), CONN(2), BUTTONS(2), WAKE(2), TORR(2), WIDGET(2), SPACE(2), FEEDBACK(2), LOG(2), TORRWEB(1)
}

private val autoOffOptions = listOf(0, 1, 2, 4, 8)

@Composable
fun SettingsRoot(app: StarApp, go: (Route) -> Unit, back: () -> Unit) {
    val s by app.settings.flow.collectAsState()
    val log by app.journal.entries.collectAsState()
    Page("Настройки", "Пульт", back) {
        BigTitle("Настройки")
        Section("Mac") {
            row(58.dp) { NavRow(Ic.conn, "Подключение", s.ip.ifBlank { if (s.mac.isBlank()) "не задано" else "по Bonjour" }) { go(Route.CONN) } }
        }
        Section("Управление") {
            row(58.dp) { NavRow(Ic.buttons, "Кнопки", "${2 + s.custom.size}") { go(Route.BUTTONS) } }
            row(58.dp) {
                NavRow(Ic.wake, "Пробуждение и таймер", if (s.autoOffHours == 0) "без таймера" else "выкл. через ${s.autoOffHours} ч") { go(Route.WAKE) }
            }
            row(58.dp) { NavRow(Ic.torr, "TorrServe", ":${s.torrPort}") { go(Route.TORR) } }
        }
        Section("Оформление") {
            row(58.dp) { NavRow(Ic.widget, "Виджет", null) { go(Route.WIDGET) } }
            row(58.dp) { NavRow(Ic.star, "Космос и анимация", null) { go(Route.SPACE) } }
            row(58.dp) {
                val fb = listOfNotNull("звук".takeIf { s.sound }, "вибрация".takeIf { s.haptic }).joinToString(", ").ifBlank { "выкл." }
                NavRow(Ic.sound, "Звук и вибрация", fb) { go(Route.FEEDBACK) }
            }
        }
        Section("Диагностика", "Звёздный пульт ${app.version}") {
            row(58.dp) { NavRow(Ic.log, "Журнал", "${log.size} зап.") { go(Route.LOG) } }
        }
    }
}

@Composable
fun ConnectionScreen(app: StarApp, back: () -> Unit) {
    val s by app.settings.flow.collectAsState()
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf("Проверка будит не Mac, а только спрашивает службу на нём, отвечает ли она.") }
    var working by remember { mutableStateOf(false) }
    val upd = app.settings::update
    Page("Подключение", "Настройки", back) {
        Section("Сеть", "IP лучше закрепить за iMac в роутере. Если IP оставить пустым, приложение найдёт Mac через Bonjour.") {
            row { FieldRow("IP-адрес", s.ip, { v -> upd { it.copy(ip = v.trim()) } }, "192.168.0.243", KeyboardType.Decimal) }
            row { FieldRow("MAC Wi-Fi", s.mac, { v -> upd { it.copy(mac = v.trim()) } }, "7c:c3:a1:ac:f6:7b", mono = true) }
            row {
                ActionRow(if (working) "Ищу…" else "Найти Mac через Bonjour", enabled = !working) {
                    working = true
                    scope.launch {
                        val h = app.controller.discover()
                        result = if (h != null) { upd { it.copy(ip = h) }; "Найден Mac: $h" } else "Mac не найден. Он спит или служба не установлена"
                        working = false
                    }
                }
            }
        }
        if (s.mac.isNotBlank() && !WakeOnLan.isValidMac(s.mac)) {
            Text("MAC-адрес выглядит неверно: нужно 6 пар символов, например 7c:c3:a1:ac:f6:7b", color = P.danger, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
        Section("Служба на Mac", "Ключ доступа выводит установщик службы на Mac. Он хранится на телефоне в зашифрованном виде и не попадает в журнал.") {
            row { FieldRow("Порт", s.agentPort.toString(), { v -> v.toIntOrNull()?.let { p -> upd { it.copy(agentPort = p) } } }, "47823", KeyboardType.Number) }
            row { FieldRow("Ключ доступа", s.token, { v -> upd { it.copy(token = v.trim()) } }, "из установщика", mono = true) }
        }
        Section(footer = result) {
            row {
                ActionRow(if (working) "Проверяю…" else "Проверить соединение", enabled = !working) {
                    working = true
                    scope.launch { result = app.controller.testConnection(); working = false }
                }
            }
        }
    }
}

@Composable
fun ButtonsScreen(app: StarApp, back: () -> Unit) {
    val s by app.settings.flow.collectAsState()
    val upd = app.settings::update
    var newName by remember { mutableStateOf("") }
    var newSc by remember { mutableStateOf("") }
    Page("Кнопки", "Настройки", back) {
        Section("Основные", "Справа точное название шортката, как в приложении «Команды» на Mac.") {
            row { FieldRow("«Включить»", s.onShortcut, { v -> upd { it.copy(onShortcut = v) } }, "Название шортката") }
            row { FieldRow("«Выключить»", s.offShortcut, { v -> upd { it.copy(offShortcut = v) } }, "Название шортката") }
        }
        Section("Свои кнопки") {
            if (s.custom.isEmpty()) row {
                Text("Пока нет своих кнопок", color = P.muted, fontSize = 16.sp, modifier = Modifier.padding(16.dp))
            }
            s.custom.forEach { b ->
                row {
                    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(22.dp).background(Color(0xFFE5484D), CircleShape)
                                .clickable { upd { st -> st.copy(custom = st.custom.filterNot { it.id == b.id }) }; app.journal.info("Кнопка «${b.name}» удалена") },
                            contentAlignment = Alignment.Center,
                        ) { Box(Modifier.size(10.dp, 2.dp).background(Color.White)) }
                        Column(Modifier.weight(1f)) {
                            Text(b.name, color = P.text, fontSize = 16.sp)
                            Text("Шорткат «${b.shortcut}»", color = P.muted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        Section("Новая кнопка", "Новая кнопка появится на главном экране над основными и на виджете.") {
            row { FieldRow("Название", newName, { newName = it }, "Например, Усыпить") }
            row { FieldRow("Шорткат", newSc, { newSc = it }, "Название в «Командах»") }
            row {
                ActionRow("Добавить кнопку", enabled = newName.isNotBlank() && newSc.isNotBlank()) {
                    val b = CustomButton(name = newName.trim(), shortcut = newSc.trim())
                    upd { it.copy(custom = it.custom + b) }
                    app.journal.info("Добавлена кнопка «${b.name}» → «${b.shortcut}»")
                    newName = ""; newSc = ""
                }
            }
        }
    }
}

@Composable
fun WakeScreen(app: StarApp, back: () -> Unit) {
    val s by app.settings.flow.collectAsState()
    val upd = app.settings::update
    Page("Пробуждение", "Настройки", back) {
        Section("Сигнал пробуждения", "По Wi-Fi старый iMac иногда пропускает сигнал, поэтому приложение повторяет его и ждёт, пока Mac ответит.") {
            row { StepperRow("Повторов сигнала", s.wolRetries, { v -> upd { it.copy(wolRetries = v) } }, 1, 10) }
            row {
                SliderRow("Ждать пробуждения", "${s.wakeWaitSec} с", s.wakeWaitSec.toFloat(), 15f..180f,
                    { v -> upd { it.copy(wakeWaitSec = (v / 5).toInt() * 5) } })
            }
        }
        Section("Автовыключение", "Даже если телефон сядет или уйдёт из сети, Mac всё равно перестанет бодрствовать в срок.") {
            row {
                ColRow {
                    Segmented(listOf("Выкл", "1 ч", "2 ч", "4 ч", "8 ч"), autoOffOptions.indexOf(s.autoOffHours).coerceAtLeast(0)) { i ->
                        upd { it.copy(autoOffHours = autoOffOptions[i]) }
                    }
                }
            }
            row { SwitchRow("Страховка на Mac", s.macGuard, "Mac сам запустит выключение в срок") { v -> upd { it.copy(macGuard = v) } } }
        }
    }
}

@Composable
fun TorrScreen(app: StarApp, back: () -> Unit) {
    val s by app.settings.flow.collectAsState()
    val upd = app.settings::update
    Page("TorrServe", "Настройки", back) {
        Section {
            row { FieldRow("Порт", s.torrPort.toString(), { v -> v.toIntOrNull()?.let { p -> upd { it.copy(torrPort = p) } } }, "8090", KeyboardType.Number) }
            row { SwitchRow("Проверять, что сервер запущен", s.torrCheck, "Статус «TorrServe работает» на главном") { v -> upd { it.copy(torrCheck = v) } } }
        }
        Section("Кнопка «Открыть TorrServe»", "Адрес: ${app.controller.torrUrl() ?: "http://IP-адрес:${s.torrPort}"}") {
            row {
                ColRow {
                    Segmented(listOf("Браузер", "В приложении"), if (s.torrInApp) 1 else 0) { i -> upd { it.copy(torrInApp = i == 1) } }
                }
            }
        }
    }
}

@Composable
fun WidgetScreen(app: StarApp, back: () -> Unit) {
    val s by app.settings.flow.collectAsState()
    val ctx = LocalContext.current
    Page("Виджет", "Настройки", back) {
        WidgetDesignerContent(s, app.settings) { Haptics.tap(ctx, s.haptic, false) }
    }
}

@Composable
fun SpaceScreen(app: StarApp, sky: StarSky, back: () -> Unit) {
    val s by app.settings.flow.collectAsState()
    val upd = app.settings::update
    val scope = rememberCoroutineScope()
    Page("Космос", "Настройки", back, translucent = true) {
        Section("Звёзды") {
            row { SliderRow("Плотность", "${s.density}", s.density.toFloat(), 150f..1400f, { v -> upd { it.copy(density = (v / 10).toInt() * 10) } }) }
            row { SliderRow("Скорость дрейфа", "%.1f".format(s.drift), s.drift, 0f..3f, { v -> upd { it.copy(drift = v) } }) }
        }
        Section("Анимация включения") {
            row { SliderRow("Длительность полёта, с", "%.1f".format(s.warp), s.warp, 1.2f..5f, { v -> upd { it.copy(warp = v) } }) }
            row { SliderRow("Размер вспышки", "%.2f".format(s.flare), s.flare, .5f..1.8f, { v -> upd { it.copy(flare = v) } }) }
            row {
                ActionRow("Показать полёт и вспышку", enabled = !sky.warping) {
                    sky.fade()
                    scope.launch { delay(400); sky.startWarp(); delay((s.warp * 1000).toLong()); sky.endWarp(true) }
                }
            }
        }
        Section("Цвет космоса") {
            row {
                SliderRow("Оттенок", "${s.spaceHue.toInt()}°", s.spaceHue, 200f..265f, { v -> upd { it.copy(spaceHue = v) } },
                    trackBrush = Brush.horizontalGradient(listOf(Color.hsl(200f, .7f, .4f), Color.hsl(232f, .7f, .4f), Color.hsl(265f, .7f, .4f))))
            }
            row { SliderRow("Яркость", "%.1f%%".format(s.spaceLight), s.spaceLight, 2f..16f, { v -> upd { it.copy(spaceLight = v) } }) }
        }
        Section {
            row { ActionRow("Сбросить к исходным", danger = true) { app.settings.resetSpace() } }
        }
    }
}

@Composable
fun FeedbackScreen(app: StarApp, back: () -> Unit) {
    val s by app.settings.flow.collectAsState()
    val upd = app.settings::update
    val ctx = LocalContext.current
    Page("Звук и вибрация", "Настройки", back) {
        Section("Звук вспышки", "Мягкий фоновый аккорд: плавно нарастает и долго затихает. Громкость зависит и от громкости медиа на телефоне.") {
            row { SwitchRow("Звук", s.sound) { v -> upd { it.copy(sound = v) } } }
            row { SliderRow("Громкость", "${s.volume}%", s.volume.toFloat(), 0f..100f, { v -> upd { it.copy(volume = v.toInt()) } }) }
            row { ActionRow("Прослушать") { Chime.play(s.volume) } }
        }
        Section("Вибрация") {
            row { SwitchRow("Вибрация при нажатии", s.haptic) { v -> upd { it.copy(haptic = v) }; Haptics.tap(ctx, v, s.hapticStrong) } }
            row {
                ColRow {
                    Segmented(listOf("Мягкая", "Средняя"), if (s.hapticStrong) 1 else 0) { i ->
                        upd { it.copy(hapticStrong = i == 1) }; Haptics.tap(ctx, s.haptic, i == 1)
                    }
                }
            }
        }
    }
}

@Composable
fun LogScreen(app: StarApp, back: () -> Unit) {
    val entries by app.journal.entries.collectAsState()
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    Page("Журнал", "Настройки", back) {
        Section(footer = "Пароли и ключи в журнал не попадают. Скопируй и пришли Claude, если что-то не работает.") {
            row {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    if (entries.isEmpty()) Text("Журнал пуст", color = P.muted, fontSize = 13.sp)
                    entries.takeLast(200).forEach { e ->
                        val c = when (e.level) { Level.OK -> P.ok; Level.WARN -> P.wait; Level.ERR -> P.danger; Level.INFO -> Color(0xFFC3CBEE) }
                        Text(e.line(), color = c, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp)
                    }
                }
            }
        }
        Section {
            row {
                ActionRow(if (copied) "Скопировано" else "Скопировать журнал") {
                    clip.setText(AnnotatedString(app.journal.export(app.version))); copied = true
                }
            }
            row {
                ActionRow("Поделиться") {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, app.journal.export(app.version))
                    ctx.startActivity(Intent.createChooser(i, "Журнал Звёздного пульта"))
                }
            }
            row { ActionRow("Очистить", danger = true) { app.journal.clear(); copied = false } }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TorrWebScreen(url: String, back: () -> Unit) {
    Page("TorrServe", "Пульт", back, scroll = false) {
        AndroidView(
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    setBackgroundColor(android.graphics.Color.BLACK)
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
