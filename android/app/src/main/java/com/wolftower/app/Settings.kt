package com.wolftower.app

import android.content.Context

/** Простое хранилище выбора пользователя. */
object Settings {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("dpi", Context.MODE_PRIVATE)

    /** Продукт защищает только WhatsApp: единственный сервис всегда включён, выбора нет. */
    const val SERVICE_ID = "whatsapp"

    @Suppress("UNUSED_PARAMETER")
    fun enabledIds(ctx: Context): Set<String> = setOf(SERVICE_ID)

    /**
     * Стратегия сервиса для ТЕКУЩЕЙ сети: сначала то, что запомнено для этой сети, затем общий выбор.
     * "none" = без обхода.
     */
    fun strategyFor(ctx: Context, profileId: String, default: String): String {
        val p = prefs(ctx)
        val fp = NetId.fingerprint(ctx)
        return p.getString("net_${fp}_$profileId", null)
            ?: p.getString("strategy_$profileId", default)
            ?: default
    }

    /** Запоминает выбор и как общий, и для текущей сети. */
    fun setStrategyFor(ctx: Context, profileId: String, strategy: String) {
        val fp = NetId.fingerprint(ctx)
        prefs(ctx).edit()
            .putString("strategy_$profileId", strategy)
            .putString("net_${fp}_$profileId", strategy)
            .apply()
    }

    /**
     * Блокировка QUIC (UDP) в продукте отключена: она ломает звонки WhatsApp, а в интерфейсе
     * переключателя больше нет. Всегда "авто" (null), старые сохранённые значения игнорируются.
     */
    @Suppress("UNUSED_PARAMETER")
    fun blockQuicPref(ctx: Context): Boolean? = null

    fun dohEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("doh", false)

    fun setDoh(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean("doh", value).apply()

    /** Способ обхода: "byedpi" (встроенный, по умолчанию) или "socks5" (свой SOCKS5-прокси: Xray, sing-box, Tor и т. д.). */
    const val ENGINE_BYEDPI = "byedpi"
    const val ENGINE_SOCKS5 = "socks5"

    /** zapret tpws (режим --socks, без root), sing-box: фрагментация TLS + DoH, sing-box: бесплатный WARP. */
    const val ENGINE_TPWS = "tpws"
    const val ENGINE_SB_FRAG = "singbox_frag"
    const val ENGINE_SB_WARP = "singbox_warp"

    private val engines = setOf(ENGINE_BYEDPI, ENGINE_SOCKS5, ENGINE_TPWS, ENGINE_SB_FRAG, ENGINE_SB_WARP)

    fun engine(ctx: Context): String =
        prefs(ctx).getString("engine", ENGINE_BYEDPI).let { if (it != null && it in engines) it else ENGINE_BYEDPI }

    fun tpwsStrategy(ctx: Context): String = prefs(ctx).getString("tpws_strategy", TpwsArgs.DEFAULT) ?: TpwsArgs.DEFAULT
    fun setTpwsStrategy(ctx: Context, v: String) = prefs(ctx).edit().putString("tpws_strategy", v).apply()

    /** DNS для sing-box: "cloudflare" (DoH, по умолчанию), "google" (DoH) или "system". */
    fun sbDns(ctx: Context): String =
        prefs(ctx).getString("sb_dns", "cloudflare").let { if (it == "google" || it == "system") it else "cloudflare" }

    fun setSbDns(ctx: Context, v: String) = prefs(ctx).edit().putString("sb_dns", v).apply()

    fun warpAccount(ctx: Context): String? = prefs(ctx).getString("warp_account", null)
    fun setWarpAccount(ctx: Context, json: String) = prefs(ctx).edit().putString("warp_account", json).apply()

    fun setEngine(ctx: Context, value: String) = prefs(ctx).edit().putString("engine", value).apply()

    fun proxyHost(ctx: Context): String = prefs(ctx).getString("proxy_host", "") ?: ""
    fun proxyPort(ctx: Context): Int = prefs(ctx).getInt("proxy_port", 0)
    fun proxyUser(ctx: Context): String = prefs(ctx).getString("proxy_user", "") ?: ""
    fun proxyPass(ctx: Context): String = prefs(ctx).getString("proxy_pass", "") ?: ""

    private val hostRegex = Regex("^[A-Za-z0-9._:\\-]{1,253}$")

    /** Проверка адреса: только безопасные символы (значения попадают в конфигурацию туннеля). */
    fun validProxy(host: String, port: Int, user: String, pass: String): Boolean =
        hostRegex.matches(host) && port in 1..65535 &&
            user.length <= 255 && pass.length <= 255 &&
            (user + pass).none { it.isISOControl() }

    fun setProxy(ctx: Context, host: String, port: Int, user: String, pass: String) {
        prefs(ctx).edit()
            .putString("proxy_host", host)
            .putInt("proxy_port", port)
            .putString("proxy_user", user)
            .putString("proxy_pass", pass)
            .apply()
    }

    fun proxyConfigured(ctx: Context): Boolean =
        validProxy(proxyHost(ctx), proxyPort(ctx), proxyUser(ctx), proxyPass(ctx))

    fun notifAsked(ctx: Context): Boolean = prefs(ctx).getBoolean("notif_asked", false)

    fun setNotifAsked(ctx: Context) = prefs(ctx).edit().putBoolean("notif_asked", true).apply()
}
