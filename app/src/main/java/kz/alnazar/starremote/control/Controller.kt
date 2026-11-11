package kz.alnazar.starremote.control

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kz.alnazar.starremote.data.AppSettings
import kz.alnazar.starremote.data.CustomButton
import kz.alnazar.starremote.data.Journal
import kz.alnazar.starremote.data.SettingsRepo
import kz.alnazar.starremote.net.Agent
import kz.alnazar.starremote.net.AgentException
import kz.alnazar.starremote.net.Discovery
import kz.alnazar.starremote.net.Net
import kz.alnazar.starremote.net.WakeOnLan
import kz.alnazar.starremote.widget.StarWidget
import kotlin.math.max

enum class Tone { IDLE, WAIT, ON, ERROR }

data class Status(
    val tone: Tone = Tone.WAIT,
    val text: String = "Проверяю Mac…",
    val mac: String = "проверка",
    val online: Boolean = false,
    val isOn: Boolean = false,
    val torrUp: Boolean = false,
    val busy: Boolean = false,
)

/** События для анимации на главном экране. */
sealed interface OpEvent {
    data object WarpStart : OpEvent
    data class WarpEnd(val success: Boolean) : OpEvent
    data object Fade : OpEvent
}

sealed interface Op {
    data object On : Op
    data object Off : Op
    data object OffByTimer : Op
    data class Custom(val id: String) : Op
}

/**
 * Вся работа с Mac: пробуждение, поиск через Bonjour, запуск шорткатов,
 * реальный статус. Операции идут строго по одной.
 */
class Controller(private val ctx: Context, private val settings: SettingsRepo, private val log: Journal) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()
    private val _events = MutableSharedFlow<OpEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<OpEvent> = _events.asSharedFlow()

    @Volatile private var discoveredHost: String? = null

    private val s: AppSettings get() = settings.value
    private fun host(): String? = discoveredHost ?: s.ip.trim().ifBlank { null }
    private fun agent(h: String) = Agent(ctx, h, s.agentPort, s.token)

    fun torrUrl(): String? = host()?.let { "http://$it:${s.torrPort}" }

    // ---------- запуск из интерфейса ----------

    fun turnOn() = launch(Op.On)
    fun turnOff() = launch(Op.Off)
    fun runCustom(b: CustomButton) = launch(Op.Custom(b.id))

    private fun launch(op: Op) {
        if (_status.value.busy) return
        scope.launch { perform(op) }
    }

    /** Выполняет операцию до конца. Используется и интерфейсом, и виджетом, и таймером. */
    suspend fun perform(op: Op) = mutex.withLock {
        set { it.copy(busy = true) }
        try {
            when (op) {
                Op.On -> doTurnOn()
                Op.Off -> doTurnOff(byTimer = false)
                Op.OffByTimer -> doTurnOff(byTimer = true)
                is Op.Custom -> s.custom.firstOrNull { it.id == op.id }?.let { doCustom(it) }
                    ?: log.warn("Кнопка не найдена (её удалили?)")
            }
        } catch (e: AgentException) {
            fail(e.message ?: "Ошибка связи с Mac")
            if (op == Op.On) _events.tryEmit(OpEvent.WarpEnd(false))
        } catch (e: Exception) {
            fail("Неожиданная ошибка: ${e.javaClass.simpleName}: ${e.message}")
            if (op == Op.On) _events.tryEmit(OpEvent.WarpEnd(false))
        } finally {
            set { it.copy(busy = false) }
        }
    }

    /** Реальный статус Mac. Не мешает идущей операции. */
    fun refresh() {
        if (!mutex.tryLock()) return
        scope.launch {
            try { doRefresh() } catch (e: Exception) { fail(e.message ?: "Ошибка") } finally { mutex.unlock() }
        }
    }

    /** Поиск Mac через Bonjour для экрана настроек. */
    suspend fun discover(): String? {
        log.info("Bonjour: ищу _starremote._tcp")
        val f = Discovery.find(ctx, 6000)
        if (f == null) { log.warn("Bonjour: Mac не найден (${Discovery.lastError ?: "нет ответа за 6 с"})"); return null }
        discoveredHost = f.host
        log.ok("Bonjour: найден «${f.name}» → ${f.host}:${f.port}")
        return f.host
    }

    /** Проверка соединения для экрана настроек. */
    suspend fun testConnection(): String {
        val h = host() ?: return "Сначала укажи IP или найди Mac через Bonjour"
        val a = agent(h)
        val t0 = System.currentTimeMillis()
        val name = a.ping(2000) ?: run {
            val why = a.pingError(2000)
            return "Mac по адресу $h не отвечает: ${why ?: "нет ответа"}".also { log.warn(it) }
        }
        val ms = System.currentTimeMillis() - t0
        return try {
            val st = a.status()
            "«$name» отвечает · $ms мс · ключ верный · TorrServe ${if (st.torrserve) "работает" else "не запущен"}".also { log.ok(it) }
        } catch (e: AgentException) {
            "«$name» отвечает · $ms мс, но: ${e.message}".also { log.warn(it) }
        }
    }

    // ---------- операции ----------

    private suspend fun doTurnOn() {
        val st = s
        log.info("Нажата «Включить»")
        if (!st.configured) {
            fail("Заполни подключение в настройках: IP или MAC и ключ доступа")
            _events.tryEmit(OpEvent.WarpEnd(false)); return
        }
        _events.tryEmit(OpEvent.WarpStart)
        val h = ensureAwake() ?: run { _events.tryEmit(OpEvent.WarpEnd(false)); return }
        val a = agent(h)

        set { it.copy(tone = Tone.WAIT, text = "Запускаю шорткат «${st.onShortcut}»…") }
        log.info("Запуск шортката «${st.onShortcut}»")
        val r = a.run(st.onShortcut)
        if (!r.ok) {
            if (r.output.isNotBlank()) log.err("Mac: ${r.output.take(400)}")
            fail("Шорткат «${st.onShortcut}» не выполнился (код ${r.code})")
            _events.tryEmit(OpEvent.WarpEnd(false)); return
        }
        log.ok("Шорткат выполнен за ${r.sec} с")

        if (st.autoOffHours > 0) {
            if (st.macGuard) {
                runCatching { a.guard(st.autoOffHours * 3600, st.offShortcut) }
                    .onSuccess { log.info("Страховка на Mac: выключится сам через ${st.autoOffHours} ч") }
                    .onFailure { log.warn("Страховку на Mac поставить не удалось: ${it.message}") }
            }
            AutoOff.schedule(ctx, st.autoOffHours)
            log.info("Автовыключение через ${st.autoOffHours} ч")
        }

        var ms = a.status()
        if (st.torrCheck && !ms.torrserve) {
            set { it.copy(text = "Жду TorrServe…") }
            for (i in 0 until 10) {
                delay(1000)
                ms = a.status()
                if (ms.torrserve) break
            }
        }
        val torrText = when {
            !st.torrCheck -> "Включено"
            ms.torrserve -> "Включено · TorrServe работает"
            else -> "Включено · TorrServe пока не отвечает"
        }
        if (st.torrCheck && !ms.torrserve) log.warn("TorrServe не отвечает на порту ${st.torrPort}") else log.ok(torrText)
        set { it.copy(tone = Tone.ON, text = torrText, mac = "онлайн", online = true, isOn = true, torrUp = ms.torrserve) }
        _events.tryEmit(OpEvent.WarpEnd(true))
    }

    private suspend fun doTurnOff(byTimer: Boolean) {
        val st = s
        log.info(if (byTimer) "Автовыключение по таймеру" else "Нажата «Выключить»")
        val h = host()
        if (h == null || agent(h).ping(1200) == null) {
            log.info("Mac не отвечает, считаю его выключенным")
            AutoOff.cancel(ctx)
            set { it.copy(tone = Tone.IDLE, text = "Mac спит", mac = "спит", online = false, isOn = false, torrUp = false) }
            _events.tryEmit(OpEvent.Fade); return
        }
        val a = agent(h)
        set { it.copy(tone = Tone.WAIT, text = "Запускаю шорткат «${st.offShortcut}»…") }
        log.info("Запуск шортката «${st.offShortcut}»")
        val r = a.run(st.offShortcut)
        if (!r.ok) {
            if (r.output.isNotBlank()) log.err("Mac: ${r.output.take(400)}")
            fail("Шорткат «${st.offShortcut}» не выполнился (код ${r.code})"); return
        }
        runCatching { a.guardStop() }
        AutoOff.cancel(ctx)
        log.ok("Выключено за ${r.sec} с")
        set { it.copy(tone = Tone.IDLE, text = "Выключено · Mac может уснуть", mac = "онлайн", online = true, isOn = false, torrUp = false) }
        _events.tryEmit(OpEvent.Fade)
    }

    private suspend fun doCustom(b: CustomButton) {
        log.info("Нажата «${b.name}»")
        if (!s.configured) { fail("Заполни подключение в настройках"); return }
        val h = ensureAwake() ?: return
        set { it.copy(tone = Tone.WAIT, text = "Запускаю «${b.shortcut}»…") }
        val r = agent(h).run(b.shortcut)
        if (!r.ok) {
            if (r.output.isNotBlank()) log.err("Mac: ${r.output.take(400)}")
            fail("«${b.name}» не выполнилось (код ${r.code})"); return
        }
        log.ok("«${b.name}» выполнено за ${r.sec} с")
        val on = _status.value.isOn
        set { it.copy(tone = if (on) Tone.ON else Tone.IDLE, text = "«${b.name}» выполнено") }
    }

    private suspend fun doRefresh() {
        val st = s
        if (!st.configured) {
            set { it.copy(tone = Tone.IDLE, text = "Настрой подключение к Mac", mac = "не настроен", online = false, isOn = false) }
            return
        }
        var h = host()
        var name = h?.let { agent(it).ping(1200) }
        if (name == null && st.ip.isBlank()) {
            Discovery.find(ctx, 2500)?.let { discoveredHost = it.host; h = it.host; name = agent(it.host).ping(1200) }
        }
        if (h == null || name == null) {
            set { it.copy(tone = Tone.IDLE, text = "Mac спит", mac = "спит", online = false, isOn = false, torrUp = false) }
            return
        }
        val ms = agent(h!!).status()
        val on = ms.torrProc || ms.torrserve
        val text = when {
            on && ms.torrserve -> "Включено · TorrServe работает"
            on -> "Включено"
            else -> "Mac не спит · выключено"
        }
        set { it.copy(tone = if (on) Tone.ON else Tone.IDLE, text = text, mac = "онлайн", online = true, isOn = on, torrUp = ms.torrserve) }
    }

    /** Будит Mac и ждёт, пока служба ответит. Возвращает адрес или null. */
    private suspend fun ensureAwake(): String? {
        val st = s
        set { it.copy(tone = Tone.WAIT, text = "Проверяю Mac…") }
        host()?.let { h ->
            if (agent(h).ping(900) != null) {
                log.info("Mac уже не спит ($h)")
                set { it.copy(mac = "онлайн", online = true) }
                return h
            }
        }
        if (!Net.onWifi(ctx)) { fail("Телефон не подключён к Wi-Fi"); return null }
        if (!WakeOnLan.isValidMac(st.mac)) {
            fail(if (st.mac.isBlank()) "Mac не отвечает, а MAC-адрес для пробуждения не указан" else "MAC-адрес указан неверно")
            return null
        }

        set { it.copy(tone = Tone.WAIT, text = "Отправляю сигнал пробуждения…", mac = "просыпается") }
        val retries = st.wolRetries.coerceIn(1, 10)
        val waitMs = st.wakeWaitSec.coerceIn(10, 300) * 1000L
        val interval = max(2000L, waitMs / retries)
        val t0 = System.currentTimeMillis()
        var sent = 0
        var nextWol = 0L
        var nextDiscovery = 4000L

        while (true) {
            val elapsed = System.currentTimeMillis() - t0
            if (elapsed > waitMs) break
            if (sent < retries && elapsed >= nextWol) {
                val n = runCatching { WakeOnLan.send(ctx, st.mac, host()) }
                    .getOrElse { log.err("Сигнал не отправлен: ${it.message}"); 0 }
                sent++
                nextWol += interval
                log.info("Сигнал пробуждения $sent/$retries (пакетов: $n)")
                if (sent == 1) set { it.copy(text = "Mac просыпается…") }
            }
            val h = host()
            if (h != null && agent(h).ping(900) != null) {
                log.ok("Mac проснулся за ${"%.1f".format((System.currentTimeMillis() - t0) / 1000.0)} с ($h)")
                set { it.copy(mac = "онлайн", online = true) }
                return h
            }
            if (elapsed >= nextDiscovery) {
                nextDiscovery += 6000
                Discovery.find(ctx, 2000)?.let { f ->
                    if (f.host != discoveredHost) log.info("Bonjour: Mac на ${f.host}")
                    discoveredHost = f.host
                }
            }
            delay(500)
        }
        fail("Mac не проснулся за ${st.wakeWaitSec} с")
        set { it.copy(mac = "спит") }
        return null
    }

    private fun fail(msg: String) {
        log.err(msg)
        set { it.copy(tone = Tone.ERROR, text = msg) }
    }

    private fun set(block: (Status) -> Status) {
        val before = _status.value
        _status.update(block)
        if (before != _status.value) scope.launch { StarWidget.refresh(ctx) }
    }
}
