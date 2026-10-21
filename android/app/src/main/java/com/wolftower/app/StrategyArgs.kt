package com.wolftower.app

/**
 * Имена стратегий из profiles.json -> аргументы ByeDPI (ciadpi).
 * Флаги сверены с `ciadpi --help` (коммит ba53229). "+s" = смещение от начала SNI.
 */
object StrategyArgs {
    private val library: Map<String, List<String>> = mapOf(
        "split" to listOf("-s1+s"),
        "disorder" to listOf("-d1+s"),
        "oob" to listOf("-o1+s"),
        "disoob" to listOf("-q1+s"),
        "split+tlsrec" to listOf("-s1+s", "-r1+s"),
        "disorder+tlsrec" to listOf("-d1+s", "-r1+s"),
        "oob+tlsrec" to listOf("-o1+s", "-r1+s"),
        "fake+disorder" to listOf("-f-1", "-t", "8", "-d1+s"),
        "fake+oob" to listOf("-f-1", "-t", "8", "-o1+s"),
        "fake+split" to listOf("-f-1", "-t", "8", "-s1+s"),
        "fake+tlsrec" to listOf("-f-1", "-t", "8", "-r1+s"),
        "fake4+disorder" to listOf("-f-1", "-t", "4", "-d1+s"),
        "fake12+disorder" to listOf("-f-1", "-t", "12", "-d1+s"),
        // Абсолютные смещения (без +s) работают и на трафике без SNI: WhatsApp-чат идёт не по TLS,
        // а по протоколу Noise, и смещение +s на нём пропускается (gen_offset возвращает -1).
        "disorder0" to listOf("-d1"),
        "split0" to listOf("-s1"),
        // Цепочка мелких фрагментов + 1 поддельный UDP-пакет (-a1) для звонков WhatsApp (STUN/UDP).
        "chain" to CHAIN,
        "chain+udp" to CHAIN + "-a1",
        "disorder0+udp" to listOf("-d1", "-a1"),
        "split+udp" to listOf("-s1+s", "-a1"),
        "disorder+udp" to listOf("-d1+s", "-a1"),
    )

    private val CHAIN = listOf("-d1", "-s1+s", "-s3+s", "-s6+s", "-s9+s", "-s12+s", "-s15+s", "-s20+s", "-s30+s")

    /** Своя строка в стиле ByeByeDPI: имя стратегии вида "raw:-d1 -s1+s -a1". */
    const val RAW_PREFIX = "raw:"

    // Разрешены только флаги самого обхода. Фильтры групп, порты, режимы и прочее задаёт приложение.
    private const val RAW_ALLOWED = "sdoqftSnlOQeMrmaYR"

    /** Возвращает список аргументов или null, если строка содержит недопустимые флаги. */
    fun parseRaw(text: String): List<String>? {
        if (text.length > 400) return null
        val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty() || tokens.size > 40) return null
        for (tk in tokens) {
            if (tk.startsWith("--")) return null
            if (tk.startsWith("-") && tk.length >= 2 && !tk[1].isDigit() && tk[1] !in RAW_ALLOWED) return null
        }
        return tokens
    }

    /** "none" и неизвестные имена = без обхода. */
    fun forName(name: String): List<String> =
        if (name.startsWith(RAW_PREFIX)) parseRaw(name.removePrefix(RAW_PREFIX)) ?: emptyList()
        else library[name] ?: emptyList()

    /**
     * Своя стратегия для каждого сервиса: группы опций ByeDPI, разделённые --auto=none.
     * Группа по доменам срабатывает по SNI. Для сервисов с ip_ranges (Telegram подключается по IP
     * без имени сайта) добавляется вторая группа с фильтром по IP-диапазонам: в ByeDPI фильтры
     * внутри одной группы работают по "И", поэтому домены и IP идут отдельными группами.
     * Остальной трафик идёт без изменений.
     */
    fun buildGroups(selection: List<Pair<Profile, String>>): List<String> {
        val out = ArrayList<String>()
        fun group(filterFlag: String, filterValue: String, strategy: String) {
            if (out.isNotEmpty()) out.add("--auto=none")
            out.add(filterFlag)
            out.add(":$filterValue")
            out.addAll(forName(strategy))
        }
        val active = selection.filter { it.second != "none" }
        // Один активный сервис с match_all: фильтр по SNI не ставим, иначе соединения без SNI
        // (чат WhatsApp, Noise) не получают обхода. В туннель и так попадает только трафик выбранных приложений.
        if (active.size == 1 && active[0].first.matchAll) {
            out.addAll(forName(active[0].second))
            return out
        }
        for ((profile, strategy) in selection) {
            if (strategy == "none") continue
            if (profile.domains.isNotEmpty()) group("--hosts", profile.domains.joinToString(" "), strategy)
            if (profile.ipRanges.isNotEmpty()) group("--ipset", profile.ipRanges.joinToString(" "), strategy)
        }
        return out
    }
}
