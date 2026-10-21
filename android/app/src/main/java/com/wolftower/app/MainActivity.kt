package com.wolftower.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    /** Увеличивается при возврате в приложение: обновляем статусы (батарея, лицензия). */
    private val resumeTick = mutableStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeTick.value = resumeTick.value + 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = ProfilesRepository.load(this)
        setContent { MaterialTheme { Screen(config) } }
    }

    @OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
    @Composable
    private fun Screen(config: ProfilesConfig) {
        val ctx = this
        val powerManager = remember { ctx.getSystemService(PowerManager::class.java) }
        var enabled by remember { mutableStateOf(Settings.enabledIds(ctx)) }
        var strategies by remember {
            mutableStateOf(
                config.profiles.associate { it.id to Settings.strategyFor(ctx, it.id, config.defaultStrategy) }
            )
        }
        var blockQuicPref by remember { mutableStateOf(Settings.blockQuicPref(ctx)) }
        var dohOn by remember { mutableStateOf(Settings.dohEnabled(ctx)) }
        var batteryOk by remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(ctx.packageName)) }
        var netName by remember { mutableStateOf(NetId.describe(ctx)) }
        var crash by remember { mutableStateOf(CrashInfo.read(ctx)) }
        var status by remember { mutableStateOf(LicenseManager.status(ctx)) }
        var keyInput by remember { mutableStateOf("") }
        var licenseMsg by remember { mutableStateOf<String?>(null) }
        var showKeyField by remember { mutableStateOf(status !is LicenseStatus.Licensed) }
        var showLicenses by remember { mutableStateOf(false) }
        var advancedOpen by remember { mutableStateOf(false) }
        var logOpen by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        val logScroll = rememberScrollState()
        val clipboard = LocalClipboardManager.current
        val running by DpiVpnService.running.collectAsState()
        val log by DpiVpnService.log.collectAsState()
        val probe by AutoProbe.state.collectAsState()
        val tick = resumeTick.value
        val canRun = status.canRun
        val deviceId = remember { LicenseManager.deviceId(ctx) }

        val notifPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

        // При открытии: просим разрешение на уведомления (один раз) и сверяем часы с сетью
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33 && !Settings.notifAsked(ctx) &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                Settings.setNotifAsked(ctx)
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            thread(isDaemon = true) {
                LicenseManager.networkCheck(ctx)
                runOnUiThread { status = LicenseManager.status(ctx) }
            }
        }
        // Возврат в приложение и смена состояния защиты: обновляем статусы
        LaunchedEffect(tick, running) {
            status = LicenseManager.status(ctx)
            batteryOk = powerManager.isIgnoringBatteryOptimizations(ctx.packageName)
            netName = NetId.describe(ctx)
            strategies = config.profiles.associate {
                it.id to Settings.strategyFor(ctx, it.id, config.defaultStrategy)
            }
        }
        // После автоподбора подтягиваем выбранные стратегии в интерфейс
        LaunchedEffect(probe.done) {
            if (probe.done) {
                strategies = config.profiles.associate {
                    it.id to Settings.strategyFor(ctx, it.id, config.defaultStrategy)
                }
            }
        }
        // Лог прокручивается к последней строке
        LaunchedEffect(log.size, logOpen) {
            if (logOpen) logScroll.scrollTo(logScroll.maxValue)
        }

        fun startVpn() {
            startService(Intent(ctx, DpiVpnService::class.java).setAction(DpiVpnService.ACTION_START))
        }

        val vpnPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { r -> if (r.resultCode == Activity.RESULT_OK) startVpn() }

        Column(
            Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------- Статус и главная кнопка ----------
            Text("Wolf Tower", style = MaterialTheme.typography.headlineSmall)
            Text(
                (if (running) "Защита включена" else "Защита выключена") + ". Сеть: $netName",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !probe.running && !busy && (running || canRun),
                onClick = {
                    if (running) {
                        startService(
                            Intent(ctx, DpiVpnService::class.java).setAction(DpiVpnService.ACTION_STOP)
                        )
                    } else {
                        busy = true
                        thread(isDaemon = true) {
                            LicenseManager.networkCheck(ctx)
                            runOnUiThread {
                                status = LicenseManager.status(ctx)
                                busy = false
                                if (status.canRun) {
                                    val prepare = VpnService.prepare(ctx)
                                    if (prepare != null) vpnPermission.launch(prepare) else startVpn()
                                }
                            }
                        }
                    }
                },
            ) { Text(if (running) "Остановить" else "Запустить") }
            if (!running && enabled.isEmpty()) {
                Text("Включите хотя бы один сервис ниже", style = MaterialTheme.typography.bodySmall)
            }

            // ---------- Лицензия ----------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Лицензия", style = MaterialTheme.typography.titleMedium)
                    Text(LicenseManager.describe(status), style = MaterialTheme.typography.bodyMedium)
                    if (status is LicenseStatus.Licensed && !showKeyField) {
                        TextButton(onClick = { showKeyField = true }) { Text("Ввести новый ключ") }
                    }
                    if (showKeyField) {
                        Text("ID устройства (нужен для ключа с привязкой):", style = MaterialTheme.typography.bodySmall)
                        SelectionContainer {
                            Text(
                                deviceId,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                clipboard.setText(AnnotatedString(deviceId))
                                licenseMsg = "ID скопирован"
                            }) { Text("Копировать ID") }
                            OutlinedButton(onClick = {
                                val send = Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, "ID устройства Wolf Tower: $deviceId")
                                startActivity(Intent.createChooser(send, "Отправить ID"))
                            }) { Text("Поделиться") }
                        }
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            label = { Text("Лицензионный ключ") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = keyInput.isNotBlank() && !busy,
                                onClick = {
                                    busy = true
                                    val input = keyInput
                                    thread(isDaemon = true) {
                                        val error = LicenseManager.activate(ctx, input)
                                        runOnUiThread {
                                            licenseMsg = error ?: "Ключ принят"
                                            if (error == null) keyInput = ""
                                            status = LicenseManager.status(ctx)
                                            busy = false
                                        }
                                    }
                                },
                            ) { Text(if (busy) "Проверка…" else "Активировать") }
                            OutlinedButton(onClick = {
                                clipboard.getText()?.text?.let { keyInput = it.trim() }
                            }) { Text("Вставить") }
                        }
                        licenseMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            if (crash != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Последний сбой (можно скопировать и прислать)", style = MaterialTheme.typography.titleSmall)
                        SelectionContainer { Text(crash ?: "", style = MaterialTheme.typography.bodySmall) }
                        TextButton(onClick = {
                            CrashInfo.dismiss(ctx)
                            crash = null
                        }) { Text("Скрыть") }
                    }
                }
            }

            // ---------- Сервисы ----------
            config.profiles.forEach { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Switch(
                                checked = p.id in enabled,
                                enabled = !running,
                                onCheckedChange = { on ->
                                    enabled = if (on) enabled + p.id else enabled - p.id
                                    Settings.setEnabledIds(ctx, enabled)
                                },
                            )
                        }
                        if (p.id in enabled) {
                            val current = strategies[p.id] ?: config.defaultStrategy
                            val score = probe.scores[p.id]?.let { " (проверка: $it)" } ?: ""
                            Text(
                                "Стратегия: " + (if (current == "none") "без обхода" else current) + score,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                (listOf("none") + config.strategies).forEach { s ->
                                    FilterChip(
                                        selected = s == current,
                                        enabled = !running,
                                        onClick = {
                                            Settings.setStrategyFor(ctx, p.id, s)
                                            strategies = strategies + (p.id to s)
                                        },
                                        label = { Text(if (s == "none") "без обхода" else s) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !running && !probe.running && enabled.isNotEmpty() && canRun,
                onClick = {
                    AutoProbe.start(
                        ctx.applicationContext,
                        config.profiles.filter { it.id in enabled },
                        config.strategies,
                    )
                },
            ) { Text("Подобрать стратегии автоматически") }
            if (running) {
                Text(
                    "Защита работает: приложение само проверяет доступность и временно меняет стратегию, если сервис перестаёт открываться. Для ручного подбора сначала остановите защиту.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (probe.running) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(probe.progress, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { AutoProbe.cancel() }) { Text("Отмена") }
            }
            if (probe.report.isNotEmpty()) {
                Text("Результат подбора", style = MaterialTheme.typography.titleSmall)
                Text(probe.report, style = MaterialTheme.typography.bodySmall)
            }

            // ---------- Работа в фоне ----------
            if (!batteryOk) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Работа в фоне", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Чтобы система не отключала защиту, разрешите приложению работать без ограничений батареи. " +
                                "На Xiaomi, Huawei, Samsung и других дополнительно включите автозапуск и снимите " +
                                "ограничения фона в настройках приложения.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                try {
                                    startActivity(
                                        Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                            .setData(Uri.parse("package:${ctx.packageName}"))
                                    )
                                } catch (_: Exception) {
                                }
                            }) { Text("Разрешить") }
                            OutlinedButton(onClick = {
                                startActivity(
                                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        .setData(Uri.parse("package:${ctx.packageName}"))
                                )
                            }) { Text("Настройки приложения") }
                        }
                    }
                }
            }

            // ---------- Лог: сворачиваемое окно фиксированной высоты ----------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Лог (${log.size})", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { logOpen = !logOpen }) {
                            Text(if (logOpen) "Свернуть" else "Развернуть")
                        }
                    }
                    if (!logOpen) {
                        Text(
                            log.lastOrNull() ?: "Пока пусто",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(logScroll)) {
                            SelectionContainer {
                                Text(
                                    log.takeLast(100).joinToString("\n"),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                clipboard.setText(AnnotatedString(log.joinToString("\n")))
                            }) { Text("Копировать") }
                            OutlinedButton(onClick = { DpiVpnService.clearLog() }) { Text("Очистить") }
                        }
                    }
                }
            }

            // ---------- Дополнительно ----------
            TextButton(onClick = { advancedOpen = !advancedOpen }) {
                Text(if (advancedOpen) "Дополнительно: скрыть" else "Дополнительно: QUIC, DoH, диагностика")
            }
            if (advancedOpen) {
                val autoQuic = config.profiles.filter { it.id in enabled }.let { sel ->
                    sel.isNotEmpty() && sel.all { it.blockQuic }
                }
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Блокировать QUIC (UDP)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Помогает YouTube и Instagram откатиться на TCP. Ломает звонки WhatsApp.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = dohOn || (blockQuicPref ?: autoQuic),
                            enabled = !running && !dohOn,
                            onCheckedChange = {
                                Settings.setBlockQuic(ctx, it)
                                blockQuicPref = it
                            },
                        )
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Защищённый DNS (DoH), экспериментально", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Определяет адреса сайтов через DoH, если DNS оператора подменяет ответы. " +
                                    "Отключает UDP и QUIC (звонки WhatsApp работать не будут).",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = dohOn,
                            enabled = !running,
                            onCheckedChange = {
                                Settings.setDoh(ctx, it)
                                dohOn = it
                            },
                        )
                    }
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "Wolf Tower: диагностика")
                            .putExtra(Intent.EXTRA_TEXT, DiagReport.build(ctx, config))
                        startActivity(Intent.createChooser(send, "Отправить диагностику"))
                    },
                ) { Text("Отправить диагностику") }
                Text(
                    "В отчёт попадают версия Android, модель, тип сети и оператор, выбранные стратегии и лог. IP-адреса, трафик и сам ключ не включаются.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { showLicenses = !showLicenses }) { Text("Лицензии компонентов") }
                if (showLicenses) {
                    val text = remember {
                        try {
                            ctx.assets.open("third_party_licenses.txt").bufferedReader().use { it.readText() }
                        } catch (_: Exception) {
                            "Не удалось прочитать файл лицензий"
                        }
                    }
                    SelectionContainer { Text(text, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
