package com.wolftower.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.service.quicksettings.TileService
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class DpiVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.wolftower.app.START"
        const val ACTION_STOP = "com.wolftower.app.STOP"

        private const val CHANNEL = "protection"
        private const val NOTIF_ID = 1
        private const val HEAL_COOLDOWN_MS = 30L * 60 * 1000

        val running = MutableStateFlow(false)
        val log = MutableStateFlow<List<String>>(emptyList())

        fun appendLog(s: String) {
            log.value = (log.value + s).takeLast(200)
        }

        fun clearLog() {
            log.value = emptyList()
        }
    }

    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private var tun: ParcelFileDescriptor? = null

    @Volatile
    private var byeDpi: ByeDpiProcess? = null
    private var shim: DohSocksShim? = null
    private var executor: ScheduledExecutorService? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    /** Порт, к которому подключается tun2socks (за ним стоит ByeDPI или DoH-прокси). Новый при каждом запуске. */
    private var socksPort = 0
    private var byePort = 0
    private var local: LocalProcess? = null // tpws или sing-box

    /** true: трафик идёт через свой SOCKS5-прокси пользователя, ByeDPI не запускается. */
    @Volatile
    private var external = false

    /** Стратегии, временно подобранные самопроверкой в этом сеансе (в настройки не записываются). */
    private val sessionOverrides = java.util.concurrent.ConcurrentHashMap<String, String>()

    @Volatile
    private var currentFp = "none"
    private val lastHeal = HashMap<String, Long>() // только из потока executor
    private lateinit var tun2socks: Tun2Socks

    override fun onCreate() {
        super.onCreate()
        tun2socks = HevTun2Socks(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAll()
            stopSelf()
            return START_NOT_STICKY
        }
        startAll()
        return START_NOT_STICKY
    }

    // ---------------------------------------------------------------- запуск

    private fun fail(message: String) {
        appendLog(message)
        stopAll()
        stopSelf()
    }

    private fun startAll() {
        if (running.value) return
        if (!LicenseManager.canRun(this)) {
            fail("Нужен лицензионный ключ")
            return
        }
        if (Settings.engine(this) == Settings.ENGINE_SOCKS5 && !Settings.proxyConfigured(this)) {
            fail("Не задан адрес SOCKS5-прокси (Дополнительно → Способ обхода)")
            return
        }
        val engine = Settings.engine(this)
        val socksMode = engine == Settings.ENGINE_SOCKS5
        external = engine != Settings.ENGINE_BYEDPI
        val args = if (external) emptyList<String>() else buildByeArgs()
        if (args == null) {
            fail("Не выбран ни один сервис")
            return
        }
        goForeground()

        val doh = !external && Settings.dohEnabled(this)
        sessionOverrides.clear()
        socksPort = Ports.free()
        byePort = if (doh) Ports.free() else socksPort

        if (!external) {
            val proc = ByeDpiProcess(this)
            if (!proc.start(byePort, args, ::appendLog)) {
                fail("ByeDPI не запустился")
                return
            }
            synchronized(lock) { byeDpi = proc }

            if (doh) {
                val s = DohSocksShim(socksPort, byePort, ::appendLog)
                if (!s.start()) {
                    fail("DoH-прокси не запустился")
                    return
                }
                shim = s
                appendLog("DoH включён: UDP и QUIC отключены")
            }
        }
        if (external && !socksMode && !startOtherEngine(engine)) return

        val config = ProfilesRepository.load(this)
        val chosen = config.profiles.filter { it.id in Settings.enabledIds(this) }
        val builder = Builder()
            .setSession("Wolf Tower")
            .addAddress(HevTun2Socks.TUN_ADDRESS, 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(HevTun2Socks.MAPDNS_ADDRESS)
            .setMtu(HevTun2Socks.MTU)

        // Только выбранные сервисы идут через туннель, остальной трафик напрямую
        for (pkg in chosen.flatMap { it.packages }.distinct()) {
            try {
                builder.addAllowedApplication(pkg)
            } catch (e: Exception) {
                appendLog("Пропущено (не установлено): $pkg")
            }
        }

        val fd = builder.establish()
        if (fd == null) {
            fail("Не удалось создать VPN-интерфейс")
            return
        }
        tun = fd
        val upHost = if (socksMode) Settings.proxyHost(this) else "127.0.0.1"
        val upPort = if (socksMode) Settings.proxyPort(this) else socksPort
        val upUser = if (socksMode) Settings.proxyUser(this) else null
        val upPass = if (socksMode) Settings.proxyPass(this) else null
        if (socksMode) appendLog("Свой SOCKS5-прокси: $upHost:$upPort")
        if (!tun2socks.start(fd, upHost, upPort, ::appendLog, upUser, upPass)) {
            fail("Туннель не запустился")
            return
        }
        setRunning(true)
        appendLog("Запущено. Сервисы: ${chosen.joinToString { it.name }}")

        currentFp = NetId.fingerprint(this)
        val ex = Executors.newSingleThreadScheduledExecutor()
        executor = ex
        ex.scheduleWithFixedDelay(safe { healthCheck() }, 3, 10, TimeUnit.MINUTES)
        ex.scheduleWithFixedDelay(safe { timeCheck() }, 30, 30, TimeUnit.MINUTES)
        registerNetworkWatcher()
    }

    /** Аргументы ByeDPI по текущим выбранным сервисам и стратегиям (учитывают текущую сеть). */
    private fun buildByeArgs(): List<String>? {
        val config = ProfilesRepository.load(this)
        val chosen = config.profiles.filter { it.id in Settings.enabledIds(this) }
        if (chosen.isEmpty()) return null
        val selection = chosen.map {
            it to (sessionOverrides[it.id] ?: Settings.strategyFor(this, it.id, config.defaultStrategy))
        }
        appendLog("Стратегии: " + selection.joinToString { "${it.first.name}=${it.second}" })

        // Запрет UDP (QUIC): по выбору пользователя, а если не задано, то когда его хотят все сервисы
        val blockQuic = Settings.blockQuicPref(this) ?: chosen.all { it.blockQuic }
        val noUdp = if (blockQuic) listOf("-U") else emptyList()
        return noUdp + StrategyArgs.buildGroups(selection)
    }

    private fun setRunning(value: Boolean) {
        running.value = value
        runCatching {
            TileService.requestListeningState(this, ComponentName(this, VpnTileService::class.java))
        }
    }

    // ---------------------------------------------------------------- уведомление

    private fun goForeground() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Защита", NotificationManager.IMPORTANCE_LOW)
                )
            }
            val stop = PendingIntent.getService(
                this, 1,
                Intent(this, DpiVpnService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            val action = Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_tile), "Остановить", stop
            ).build()
            val n = Notification.Builder(this, CHANNEL)
                .setContentTitle("Wolf Tower")
                .setContentText("Защита включена")
                .setSmallIcon(R.drawable.ic_tile)
                .setOngoing(true)
                .setContentIntent(open)
                .addAction(action)
                .build()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            appendLog("Уведомление: ${e.message}")
        }
    }

    // ---------------------------------------------------------------- фоновые проверки

    private fun safe(block: () -> Unit) = Runnable {
        try {
            block()
        } catch (_: InterruptedException) {
        } catch (t: Throwable) {
            appendLog("Ошибка фоновой задачи: ${t.message}")
        }
    }

    /** Сверка часов с сетью: при откате времени ключ деактивируется, защита останавливается. */
    private fun timeCheck() {
        if (LicenseManager.networkCheck(this) == NetCheck.BEHIND) {
            appendLog("Время отстаёт от сетевого, ключ деактивирован")
            main.post {
                stopAll()
                stopSelf()
            }
        }
    }

    private fun probeHealthy(p: Profile): Boolean = p.probeHosts.any { Prober.probe(it, socksPort) }

    /**
     * Самопроверка раз в 10 минут: открываются ли тестовые адреса через работающий туннель. Если сервис не
     * открывается (дважды с паузой), подбирается другая стратегия только для него и ByeDPI перезапускается.
     */
    private fun healthCheck() {
        if (!running.value || external) return
        val config = ProfilesRepository.load(this)
        val chosen = config.profiles.filter { it.id in Settings.enabledIds(this) && it.probeHosts.isNotEmpty() }
        for (p in chosen) {
            if (probeHealthy(p)) continue
            Thread.sleep(15_000)
            if (probeHealthy(p)) continue
            val last = lastHeal[p.id] ?: 0L
            if (System.currentTimeMillis() - last < HEAL_COOLDOWN_MS) continue
            lastHeal[p.id] = System.currentTimeMillis()
            healProfile(config, p)
        }
    }

    private fun healProfile(config: ProfilesConfig, p: Profile) {
        val current = sessionOverrides[p.id] ?: Settings.strategyFor(this, p.id, config.defaultStrategy)
        val candidates = ((if (p.matchAll) emptyList() else listOf("none")) + config.strategies).filter { it != current }
        appendLog("Проблема с ${p.name}, подбираю другую стратегию…")
        val counts = AutoProbe.score(this, listOf(p), candidates, { Thread.currentThread().isInterrupted }) { }
            .getValue(p.id)
        var best: String? = null
        var bestOk = 0
        for (s in candidates) {
            val c = counts[s] ?: 0
            if (c > bestOk) {
                best = s
                bestOk = c
            }
        }
        if (best == null) {
            appendLog("${p.name}: ничего не помогло (возможна блокировка по IP)")
            return
        }
        // Только на время этого сеанса: кратковременный сбой сети не должен навсегда испортить настройки
        sessionOverrides[p.id] = best
        appendLog("${p.name}: временно переключено на $best ($bestOk/${p.probeHosts.size}), в настройках без изменений")
        restartByeDpi()
    }

    /** Перезапускает только ByeDPI (туннель остаётся), с актуальными стратегиями. */
    private fun restartByeDpi(): Boolean {
        val args = buildByeArgs() ?: return false
        synchronized(lock) {
            byeDpi?.stop()
            val proc = ByeDpiProcess(this)
            if (!proc.start(byePort, args, ::appendLog)) {
                byeDpi = null
                return false
            }
            byeDpi = proc
        }
        return true
    }

    // ---------------------------------------------------------------- смена сети

    private fun registerNetworkWatcher() {
        try {
            val cm = getSystemService(ConnectivityManager::class.java)
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = scheduleNetworkChange()
                override fun onLost(network: Network) = scheduleNetworkChange()
            }
            netCallback = cb
            cm.registerDefaultNetworkCallback(cb)
        } catch (e: Exception) {
            appendLog("Слежение за сетью: ${e.message}")
        }
    }

    private fun unregisterNetworkWatcher() {
        val cb = netCallback ?: return
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(cb) }
        netCallback = null
    }

    private fun scheduleNetworkChange() {
        try {
            executor?.schedule(safe { onNetworkChanged() }, 2, TimeUnit.SECONDS)
        } catch (_: Exception) {
        }
    }

    private fun onNetworkChanged() {
        if (!running.value) return
        val fp = NetId.fingerprint(this)
        if (fp == "none" || fp == currentFp) return
        currentFp = fp
        appendLog("Сеть сменилась: ${NetId.describe(this)}")
        if (external) return
        restartByeDpi() // стратегии для новой сети берутся из памяти, если для неё уже подбирали
        executor?.schedule(safe { healthCheck() }, 20, TimeUnit.SECONDS)
    }

    // ---------------------------------------------------------------- остановка

    /** Безопасно вызывать несколько раз: каждый шаг защищён, чтобы остановка не роняла приложение. */
    /** tpws и sing-box: локальный SOCKS5 на socksPort, к нему подключается tun2socks. */
    private fun startOtherEngine(engine: String): Boolean {
        val proc = LocalProcess(if (engine == Settings.ENGINE_TPWS) "tpws" else "sing-box")
        val ok = when (engine) {
            Settings.ENGINE_TPWS -> {
                val bin = NativeBin.file(this, NativeBin.TPWS)
                if (!bin.isFile) {
                    fail("Движок zapret tpws не вошёл в эту сборку")
                    return false
                }
                val args = TpwsArgs.forName(Settings.tpwsStrategy(this))
                appendLog("Запуск tpws на порту $socksPort: ${args.joinToString(" ")}")
                proc.start(
                    listOf(bin.path, "--socks", "--bind-addr=127.0.0.1", "--port=$socksPort", "--maxconn=1024") + args,
                    socksPort,
                    ::appendLog,
                    3000,
                )
            }
            Settings.ENGINE_SB_FRAG, Settings.ENGINE_SB_WARP -> SingBoxRunner.start(this, engine, socksPort, proc, ::appendLog)
            else -> false
        }
        if (!ok) {
            fail("Движок не запустился, подробности в логе")
            return false
        }
        synchronized(lock) { local = proc }
        return true
    }

    private fun stopAll() {
        runCatching { executor?.shutdownNow() }
        executor = null
        unregisterNetworkWatcher()
        runCatching { tun2socks.stop() }
        runCatching { shim?.stop() }
        shim = null
        synchronized(lock) {
            runCatching { byeDpi?.stop() }
            byeDpi = null
            runCatching { local?.stop() }
            local = null
        }
        runCatching { tun?.close() }
        tun = null
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        if (running.value) appendLog("Остановлено")
        setRunning(false)
    }

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopAll()
        super.onRevoke()
    }
}
