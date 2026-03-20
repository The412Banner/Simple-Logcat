package com.banner.logs.reader

import com.banner.logs.data.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.coroutines.coroutineContext

object LogcatReader {

    fun stream(): Flow<LogEntry> = flow {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "logcat -v threadtime"))
        val reader = BufferedReader(InputStreamReader(process.inputStream), 16 * 1024)
        var lastEntry: LogEntry? = null
        try {
            while (coroutineContext.isActive) {
                val line = reader.readLine() ?: break
                if (line.isBlank() || line.startsWith("---------")) continue
                val entry = LogEntry.parse(line)
                if (entry != null) {
                    lastEntry = entry
                    emit(entry)
                } else if (lastEntry != null) {
                    // Continuation line (stack trace, multi-line message)
                    val cont = lastEntry.copy(
                        id = LogEntry.nextId(),
                        message = "    $line",
                        raw = line
                    )
                    emit(cont)
                }
            }
        } finally {
            reader.close()
            try { process.destroy() } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getPidMap(): Map<Int, String> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ps -A -o PID,NAME"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val map = mutableMapOf<Int, String>()
            reader.useLines { lines ->
                lines.drop(1).forEach { line ->
                    val parts = line.trim().split(Regex("\\s+"), 2)
                    if (parts.size == 2) {
                        val pid = parts[0].toIntOrNull() ?: return@forEach
                        map[pid] = parts[1]
                    }
                }
            }
            process.waitFor()
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun checkRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val result = process.inputStream.bufferedReader().readLine() ?: ""
            process.waitFor()
            result.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }
}
