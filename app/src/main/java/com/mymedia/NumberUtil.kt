package com.mymedia

import java.math.RoundingMode
import java.text.DecimalFormat

object NumberUtil {
    private val format = DecimalFormat("0.##")

    /**
     * 保留两位小数
     */
    fun getTwoDigits(number: Double): String = format.run {
        roundingMode = RoundingMode.FLOOR
        format.format(number)
    }

}