package kz.alnazar.starremote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.alnazar.starremote.data.AppSettings
import kz.alnazar.starremote.data.SettingsRepo

private data class PB(val name: String, val dot: Color)

/** Экран «Виджет»: предпросмотр на обоях, карта цветов, светлее/темнее, прозрачность. */
@Composable
fun WidgetDesignerContent(s: AppSettings, repo: SettingsRepo, tap: () -> Unit) {
    var wide by remember { mutableStateOf(s.widgetWide) }

    // обои с предпросмотром
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1B1F4A), Color(0xFF0B1026))))
            .background(Brush.radialGradient(listOf(Color(0xFF4A3A9A), Color.Transparent), center = Offset(0f, 0f), radius = 900f))
            .background(Brush.radialGradient(listOf(Color(0xFFC86B8F).copy(alpha = .9f), Color.Transparent), center = Offset(900f, 60f), radius = 520f))
            .background(Brush.radialGradient(listOf(Color(0xFF16839A), Color.Transparent), center = Offset(1100f, 1000f), radius = 800f))
            .padding(18.dp)
    ) {
        val full = maxWidth
        val cell = (full - 30.dp) / 4
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            WidgetPreview(s, if (wide) full else cell * 2 + 10.dp, cell * 2 + 14.dp)
            for (r in 0 until 2) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(4) { Box(Modifier.size(cell).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .1f))) }
            }
        }
    }

    Section("Размер предпросмотра", "На рабочем столе виджет подстраивается под размер сам: от 4 ячеек в ширину кнопки встают в два столбца.") {
        row {
            ColRow {
                Segmented(listOf("2×2", "4×2"), if (wide) 1 else 0) {
                    wide = it == 1; tap(); repo.update { s -> s.copy(widgetWide = wide) }
                }
            }
        }
    }

    Section("Цвет", "Веди пальцем по карте: слева направо меняется цвет, сверху вниз насыщенность.") {
        row { ColRow { ColorMap(s.widgetHue, s.widgetSat) { h, sat -> repo.update { it.copy(widgetHue = h, widgetSat = sat) } } } }
        row {
            val h = s.widgetHue; val sat = s.widgetSat / 100f
            SliderRow(
                "Светлее / темнее", "${s.widgetLight.toInt()}%", s.widgetLight, 8f..85f,
                { v -> repo.update { it.copy(widgetLight = v) } },
                trackBrush = Brush.horizontalGradient(listOf(Color.hsl(h, sat, .08f), Color.hsl(h, sat, .46f), Color.hsl(h, sat, .85f))),
            )
        }
        row {
            SliderRow("Прозрачность стекла", "${s.widgetTransparency.toInt()}%", s.widgetTransparency, 0f..95f,
                { v -> repo.update { it.copy(widgetTransparency = v) } })
        }
        row {
            ColRow {
                Text("Быстрый выбор", color = P.text, fontSize = 16.sp)
                val presets = listOf(
                    Triple(236f, 55f, 28f), Triple(251f, 60f, 14f), Triple(200f, 70f, 32f), Triple(160f, 45f, 26f),
                    Triple(280f, 50f, 30f), Triple(30f, 70f, 40f), Triple(340f, 55f, 34f), Triple(0f, 0f, 22f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    presets.forEach { (h, sa, l) ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(Color.hsl(h, sa / 100f, (l + 8) / 100f))
                                .border(2.dp, Color.White.copy(alpha = .25f), CircleShape)
                                .clickable { tap(); repo.update { it.copy(widgetHue = h, widgetSat = sa, widgetLight = l) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPreview(s: AppSettings, w: Dp, h: Dp) {
    val light = s.widgetLight / 100f
    val sat = s.widgetSat / 100f
    val a = 1f - s.widgetTransparency / 100f
    val fg = if (s.widgetLight > 60) Color(0xFF14182E) else Color(0xFFEEF1FF)
    val chip = if (s.widgetLight > 60) Color(0x22000000) else Color(0x1FFFFFFF)
    val all = buildList {
        add(PB("Включить", P.green)); add(PB("Выключить", P.red))
        s.custom.forEach { add(PB(it.name, P.accent)) }
    }
    val wide = w > 250.dp
    val cols = if (wide) 2 else 1
    val shown = all.take(2 * cols)
    val more = all.size - shown.size

    Column(
        Modifier.width(w).height(h).clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(
                Color.hsl(s.widgetHue, sat, (light + .12f).coerceAtMost(1f), a), Color.hsl(s.widgetHue, sat, light, a))))
            .border(1.dp, Color.White.copy(alpha = .22f), RoundedCornerShape(24.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(P.ok, CircleShape))
            Text(if (wide) "  iMac · Включено · TorrServe работает" else "  iMac · онлайн", color = fg, fontSize = 12.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (more > 0) Text("+$more", color = fg.copy(alpha = .75f), fontSize = 11.sp)
        }
        shown.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { b ->
                    Row(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(chip).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).background(b.dot, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(b.name, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (row.size < cols) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Карта цветов: по горизонтали оттенок, по вертикали насыщенность. */
@Composable
private fun ColorMap(hue: Float, sat: Float, onPick: (Float, Float) -> Unit) {
    val dens = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(12.dp))) {
        val wPx = with(dens) { maxWidth.toPx() }
        val hPx = with(dens) { maxHeight.toPx() }
        fun pick(p: Offset) {
            val x = p.x.coerceIn(0f, wPx); val y = p.y.coerceIn(0f, hPx)
            onPick((x / wPx * 360f).coerceIn(0f, 360f), (100f - y / hPx * 100f).coerceIn(0f, 100f))
        }
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(wPx, hPx) { detectTapGestures { pick(it) } }
                .pointerInput(wPx, hPx) { detectDragGestures(onDragStart = { pick(it) }) { c, _ -> pick(c.position) } }
        ) {
            drawRect(Brush.horizontalGradient((0..12).map { Color.hsl(it * 30f % 360f, 1f, .5f) }))
            drawRect(Brush.verticalGradient(listOf(Color(0x00808080), Color(0xFF808080))))
        }
        val rx = maxWidth * (hue / 360f) - 12.dp
        val ry = maxHeight * ((100f - sat) / 100f) - 12.dp
        Box(
            Modifier.offset(x = rx, y = ry).size(24.dp).border(3.dp, Color.White, CircleShape)
                .background(Color.hsl(hue.coerceIn(0f, 360f), sat / 100f, .5f), CircleShape)
        )
    }
}

@Suppress("unused")
private fun Modifier.square() = this.aspectRatio(1f)
