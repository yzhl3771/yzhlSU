package me.weishu.kernelsu.ui.util

data class ManagerDisguiseConfig(val enabled: Boolean, val tapCount: Int) {
    init {
        require(tapCount in 2..99)
    }

    fun encode(): String = "${if (enabled) 1 else 0} $tapCount"

    companion object {
        fun decode(text: String): ManagerDisguiseConfig? {
            val fields = text.trim().split(' ')
            if (fields.size != 2 || fields[0] !in listOf("0", "1")) return null
            val count = fields[1].toIntOrNull()?.takeIf { it in 2..99 } ?: return null
            return ManagerDisguiseConfig(fields[0] == "1", count)
        }
    }
}
