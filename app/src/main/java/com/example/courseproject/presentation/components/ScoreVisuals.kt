package com.example.courseproject.presentation.components

import androidx.compose.ui.graphics.Color

/**
 * Цвет индикатора, соответствующий значению качества Q.
 *
 * Локалене-независимая визуальная подсветка: текстовые форматтеры (длина,
 * процент, числовое значение) живут в [com.example.courseproject.presentation.format].
 */
fun scoreColor(value: Double): Color = when {
    value < 0.2 -> Color(0xFFC62828)
    value < 0.4 -> Color(0xFFEF6C00)
    value < 0.6 -> Color(0xFFF9A825)
    value < 0.8 -> Color(0xFF9E9D24)
    else -> Color(0xFF2E7D32)
}
