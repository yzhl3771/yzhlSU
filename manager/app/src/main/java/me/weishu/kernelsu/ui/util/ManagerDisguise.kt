package me.weishu.kernelsu.ui.util

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import me.weishu.kernelsu.ksuApp

// UI disguise only: kernel authentication and existing root grants stay unchanged.
object ManagerDisguise {
    private const val DIRECTORY = "/data/adb/yzhlsu"
    private const val CONFIG = "$DIRECTORY/manager_disguise"
    private val prefs = ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var enabled by mutableStateOf(prefs.getBoolean("manager_disguise", false))
        private set
    var tapCount by mutableIntStateOf(prefs.getInt("manager_disguise_taps", 7).coerceIn(2, 99))
        private set

    fun reload() {
        val shell = getRootShell()
        if (!shell.isRoot) return
        val result = shell.newJob().add("if [ -e $CONFIG ]; then cat $CONFIG; else echo MISSING; fi")
            .to(ArrayList<String>(), ArrayList<String>()).exec()
        val text = result.out.joinToString("\n").trim()
        if (result.isSuccess && text == "MISSING") {
            persist(ManagerDisguiseConfig(enabled, tapCount))
            return
        }
        val config = if (result.isSuccess) ManagerDisguiseConfig.decode(text) else null
        if (config != null) {
            applyConfig(config)
        } else {
            // Do not expose the manager when a saved configuration is unreadable.
            enabled = true
            Log.e("ManagerDisguise", "Cannot read persistent configuration")
        }
    }

    fun updateEnabled(value: Boolean): Boolean = persist(ManagerDisguiseConfig(value, tapCount))

    fun updateTapCount(value: Int): Boolean = persist(ManagerDisguiseConfig(enabled, value))

    private fun persist(config: ManagerDisguiseConfig): Boolean {
        val shell = getRootShell()
        if (!shell.isRoot) return false
        val result = shell.newJob().add(
            "umask 077; mkdir -p $DIRECTORY && " +
                "[ ! -L $CONFIG ] && [ ! -L $CONFIG.tmp ] && " +
                "printf '%s\\n' '${config.encode()}' > $CONFIG.tmp && " +
                "chmod 600 $CONFIG.tmp && mv -f $CONFIG.tmp $CONFIG"
        ).to(ArrayList<String>(), ArrayList<String>()).exec()
        if (!result.isSuccess) {
            Log.e("ManagerDisguise", "Cannot save persistent configuration: ${result.err}")
            return false
        }
        applyConfig(config)
        return true
    }

    private fun applyConfig(config: ManagerDisguiseConfig) {
        prefs.edit {
            putBoolean("manager_disguise", config.enabled)
            putInt("manager_disguise_taps", config.tapCount)
        }
        tapCount = config.tapCount
        enabled = config.enabled
    }
}
