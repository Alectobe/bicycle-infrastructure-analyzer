package com.example.courseproject.presentation.components

import androidx.compose.ui.graphics.Color
import java.util.Locale
import kotlin.math.roundToInt

/** Цвет индикатора, соответствующий значению качества Q. */
fun scoreColor(value: Double): Color = when {
    value < 0.2 -> Color(0xFFC62828)
    value < 0.4 -> Color(0xFFEF6C00)
    value < 0.6 -> Color(0xFFF9A825)
    value < 0.8 -> Color(0xFF9E9D24)
    else -> Color(0xFF2E7D32)
}

/** Форматирование значения [0; 1] с двумя знаками после запятой. */
fun formatScore(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

/** Форматирование доли [0; 1] в виде процента. */
fun formatPercent(value: Double): String = "${(value * 100).roundToInt()}%"
