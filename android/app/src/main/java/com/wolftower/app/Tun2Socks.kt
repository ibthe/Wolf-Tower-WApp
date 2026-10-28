package com.wolftower.app

import android.os.ParcelFileDescriptor

/** Мост TUN -> SOCKS5. */
interface Tun2Socks {
    /** true, если мост запущен. */
    fun start(
        tun: ParcelFileDescriptor,
        socksHost: String,
        socksPort: Int,
        onLog: (String) -> Unit,
        user: String? = null,
        pass: String? = null,
    ): Boolean
    fun stop()
}
