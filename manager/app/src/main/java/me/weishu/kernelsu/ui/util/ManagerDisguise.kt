package me.weishu.kernelsu.ui.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import me.weishu.kernelsu.ksuApp

// UI disguise only: kernel authentication and existing root grants stay unchanged.
object ManagerDisguise {
    private val prefs = ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var enabled by mutableStateOf(prefs.getBoolean("manager_disguise", false))
        private set
    var tapCount by mutableIntStateOf(prefs.getInt("manager_disguise_taps", 7).coerceIn(2, 99))
        private set

    fun updateEnabled(value: Boolean) {
        prefs.edit { putBoolean("manager_disguise", value) }
        enabled = value
    }

    fun updateTapCount(value: Int) {
        require(value in 2..99)
        prefs.edit { putInt("manager_disguise_taps", value) }
        tapCount = value
    }
}
