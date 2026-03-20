package com.banner.logs.data

import java.util.concurrent.atomic.AtomicLong

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val pid: Int,
    val tid: Int,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val raw: String
) {
    companion object {
        private val idCounter = AtomicLong(0)
        private val PATTERN = Regex(
            """^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+)\s+(\d+)\s+(\d+)\s+([VDIWEFA])\s+(.*?):\s+(.*)$"""
        )

        fun nextId(): Long = idCounter.incrementAndGet()

        fun parse(line: String): LogEntry? {
            val match = PATTERN.matchEntire(line.trimEnd()) ?: return null
            val (ts, pid, tid, lvl, tag, msg) = match.destructured
            return LogEntry(
                id = nextId(),
                timestamp = ts,
                pid = pid.trim().toIntOrNull() ?: 0,
                tid = tid.trim().toIntOrNull() ?: 0,
                level = LogLevel.fromChar(lvl[0]),
                tag = tag.trim(),
                message = msg,
                raw = line
            )
        }
    }
}
