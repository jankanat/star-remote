package kz.alnazar.starremote.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color

object P {
    val spaceDeep = Color(0xFF02040D)
    val text = Color(0xFFE9EDFF)
    val muted = Color(0xFF8F99C6)
    val flare = Color(0xFFFFF1B8)
    val accent = Color(0xFFFFDF8F)
    val glassEdge = Color(0x38CDDAFF)
    val ok = Color(0xFF9BE7B0)
    val wait = Color(0xFFFFD98A)
    val idle = Color(0xFF7F8BBD)
    val danger = Color(0xFFFF8A8A)
    val page = Color(0xFF060918)
    val group = Color(0xFF10152F)
    val groupPressed = Color(0xFF171D3D)
    val sep = Color(0x1FC8D4FF)
    val iconBg = Color(0xFF222A52)
    val green = Color(0xFF5FD27E)
    val red = Color(0xFFFF6B6B)
}

/** Кривая как у переходов iOS. */
val IosEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
