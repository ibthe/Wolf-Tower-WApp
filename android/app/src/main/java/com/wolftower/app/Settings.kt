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

    fun notifAsked(ctx: Context): Boolean = prefs(ctx).getBoolean("notif_asked", false)

    fun setNotifAsked(ctx: Context) = prefs(ctx).edit().putBoolean("notif_asked", true).apply()
}
