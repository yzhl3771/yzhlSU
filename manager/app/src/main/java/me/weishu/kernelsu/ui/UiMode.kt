package me.weishu.kernelsu.ui

import androidx.compose.runtime.staticCompositionLocalOf

enum class UiMode(val value: String) {
    Miuix("miuix"),
    Material("material");

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun fromValue(value: String): UiMode = Material

        val DEFAULT_VALUE = Material.value
    }
}

val LocalUiMode = staticCompositionLocalOf { UiMode.Material }
