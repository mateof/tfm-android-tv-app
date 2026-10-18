package com.mateof.tfmtv.cast

/**
 * Turns a running byte count, read about once a second, into rates.
 *
 * Kept apart from the monitor so the arithmetic can be tested without clocks
 * or coroutines. Samples come in with their own timestamps because a tick is
 * never exactly a second, least of all on a phone with the screen off.
 */
class Throughput(private val keep: Int = 60) {

    private var lastTotal = -1L
    private var lastAt = 0L
    private val rates = ArrayDeque<Long>()

    /** Bytes per second over the last [keep] samples, oldest first. */
    val history: List<Long> get() = rates.toList()

    /**
     * Takes the total so far at [nowMs]. A total that goes down means the
     * count started again — a new relay — and is taken as a fresh baseline.
     */
    fun add(total: Long, nowMs: Long) {
        if (lastTotal >= 0 && total >= lastTotal && nowMs > lastAt) {
            val rate = (total - lastTotal) * 1_000 / (nowMs - lastAt)
            rates.addLast(rate)
            while (rates.size > keep) rates.removeFirst()
        }
        lastTotal = total
        lastAt = nowMs
    }

    /** Bytes per second over the last [seconds] samples: smooth enough to read, quick to react. */
    fun current(seconds: Int = 3): Long {
        if (rates.isEmpty()) return 0
        val recent = rates.takeLast(seconds)
        return recent.sum() / recent.size
    }

    /** Forgets the baseline but keeps the history, for a relay that is about to restart. */
    fun rebase() {
        lastTotal = -1
    }

    fun reset() {
        rebase()
        rates.clear()
    }
}
