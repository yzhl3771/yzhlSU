package me.weishu.kernelsu.ui.util

class KernelTapCounter(private val required: Int) {
    init {
        require(required in 2..99)
    }

    private var count = 0
    private var lastTap = 0L

    fun tap(now: Long): Boolean {
        if (count > 0 && (now < lastTap || now - lastTap > 2_000)) count = 0
        lastTap = now
        count++
        return (count == required).also { if (it) count = 0 }
    }
}
