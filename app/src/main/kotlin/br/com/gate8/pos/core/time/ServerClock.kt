package br.com.gate8.pos.core.time

import java.time.Instant

class ServerClock {
    private var offsetMs: Long = 0

    fun updateFromServerTime(iso: String) {
        runCatching {
            val server = Instant.parse(iso).toEpochMilli()
            offsetMs = server - System.currentTimeMillis()
        }
    }

    fun nowMillis(): Long = System.currentTimeMillis() + offsetMs
}
