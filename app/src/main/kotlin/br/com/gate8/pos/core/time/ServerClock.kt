package br.com.gate8.pos.core.time

import java.time.Instant
import java.time.OffsetDateTime

class ServerClock {
    private var offsetMs: Long = 0

    fun updateFromServerTime(iso: String) {
        val server = runCatching { Instant.parse(iso).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
            .getOrNull() ?: return
        offsetMs = server - System.currentTimeMillis()
    }

    fun nowMillis(): Long = System.currentTimeMillis() + offsetMs

    fun now(): Instant = Instant.ofEpochMilli(nowMillis())
}
