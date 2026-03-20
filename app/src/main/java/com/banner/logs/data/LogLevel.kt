package com.banner.logs.data

import androidx.compose.ui.graphics.Color

enum class LogLevel(val char: Char, val displayName: String) {
    VERBOSE('V', "Verbose"),
    DEBUG('D', "Debug"),
    INFO('I', "Info"),
    WARN('W', "Warn"),
    ERROR('E', "Error"),
    FATAL('F', "Fatal"),
    ASSERT('A', "Assert"),
    UNKNOWN('?', "Unknown");

    companion object {
        fun fromChar(c: Char): LogLevel = entries.find { it.char == c } ?: UNKNOWN
    }
}

fun LogLevel.color(): Color = when (this) {
    LogLevel.VERBOSE -> Color(0xFF9E9E9E)
    LogLevel.DEBUG   -> Color(0xFF64B5F6)
    LogLevel.INFO    -> Color(0xFF81C784)
    LogLevel.WARN    -> Color(0xFFFFB74D)
    LogLevel.ERROR   -> Color(0xFFEF5350)
    LogLevel.FATAL   -> Color(0xFFCE93D8)
    LogLevel.ASSERT  -> Color(0xFFCE93D8)
    LogLevel.UNKNOWN -> Color(0xFF757575)
}

fun LogLevel.rowBackground(): Color = when (this) {
    LogLevel.ERROR  -> Color(0x18EF5350)
    LogLevel.FATAL  -> Color(0x18CE93D8)
    LogLevel.ASSERT -> Color(0x18CE93D8)
    LogLevel.WARN   -> Color(0x10FFB74D)
    else            -> Color.Transparent
}
