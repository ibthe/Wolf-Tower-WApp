package com.wolftower.app

import android.content.Context

/** Простое хранилище выбора пользователя. */
object Settings {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("dpi", Context.MODE_PRIVATE)

    fun enabledIds(ctx: Context): Set<String> =
        prefs(ctx).getStringSet("enabled", emptySet()) ?: emptySet()

    fun setEnabledIds(ctx: Context, ids: Set<String>) =
        prefs(ctx).edit().putStringSet("enabled", ids).apply()

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

    /** null = автоматически (по block_quic выбранных сервисов). */
    fun blockQuicPref(ctx: Context): Boolean? {
        val p = prefs(ctx)
        return if (p.contains("block_quic")) p.getBoolean("block_quic", false) else null
    }

    fun setBlockQuic(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean("block_quic", value).apply()

    fun dohEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("doh", false)

    fun setDoh(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean("doh", value).apply()

    fun notifAsked(ctx: Context): Boolean = prefs(ctx).getBoolean("notif_asked", false)

    fun setNotifAsked(ctx: Context) = prefs(ctx).edit().putBoolean("notif_asked", true).apply()
}
