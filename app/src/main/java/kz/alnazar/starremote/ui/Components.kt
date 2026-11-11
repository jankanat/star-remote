package kz.alnazar.starremote.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- иконки из SVG-путей макета ----------

fun svgIcon(vararg paths: String, filled: Boolean = false): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        paths.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                fill = if (filled) SolidColor(Color.White) else null,
                stroke = if (filled) null else SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object Ic {
    val power = svgIcon("M12 3v8", "M6.3 6.8a8 8 0 1 0 11.4 0")
    val moon = svgIcon("M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z")
    val sliders = svgIcon("M4 7h10M18 7h2M4 17h4M12 17h8", "M16 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M10 15a2 2 0 1 0 0 4a2 2 0 1 0 0-4z")
    val back = svgIcon("M15 5l-7 7 7 7")
    val chevron = svgIcon("M9 5l7 7-7 7")
    val external = svgIcon("M14 4h6v6M20 4l-9 9", "M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5")
    val conn = svgIcon("M5 4h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M8 20h8M12 16v4")
    val buttons = svgIcon("M3 3h7v7H3z", "M14 3h7v7h-7z", "M3 14h7v7H3z", "M17.5 14v7M14 17.5h7")
    val wake = svgIcon("M12 5a8 8 0 1 0 0 16a8 8 0 1 0 0-16z", "M12 9v4l3 2M9 2h6")
    val torr = svgIcon("M3 5h18v14H3z", "M10 9.5v5l4.5-2.5z")
    val widget = svgIcon("M3 3h18v10H3z", "M3 16h8v5H3z", "M13 16h8v5h-8z")
    val star = svgIcon("M12 2l1.8 5.5L19 9l-5.2 1.6L12 16l-1.8-5.4L5 9l5.2-1.5z", filled = true)
    val sound = svgIcon("M4 10v4h4l5 4V6L8 10z", "M17 9a4 4 0 0 1 0 6")
    val log = svgIcon("M5 4h14v16H5z", "M9 9h6M9 13h6M9 17h3")
}

// ---------- страница ----------

@Composable
fun Page(
    title: String, backLabel: String, onBack: () -> Unit,
    translucent: Boolean = false, scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Полупрозрачная страница («Космос») показывает звёзды, чтобы изменения были видны сразу
    Column(Modifier.fillMaxSize().background(if (translucent) P.page.copy(alpha = .45f) else P.page)) {
        Box(
            Modifier.fillMaxWidth().background(P.page.copy(alpha = if (translucent) .7f else .94f)).statusBarsPadding().height(52.dp)
        ) {
            Row(
                Modifier.align(Alignment.CenterStart).clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.back, null, tint = P.accent, modifier = Modifier.size(22.dp))
                Text(backLabel, color = P.accent, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Text(title, color = P.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center).widthIn(max = 220.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(.5.dp).background(P.sep))
        }
        if (scroll) Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .navigationBarsPadding().imePadding()
                .padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
            content = content,
        ) else Column(Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(), content = content)
    }
}

@Composable
fun BigTitle(text: String) {
    Text(text, color = P.text, fontSize = 30.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 0.dp).offset(y = 6.dp))
}

// ---------- группы ----------

class GroupBuilder {
    val rows = mutableListOf<Pair<Dp, @Composable () -> Unit>>()
    fun row(inset: Dp = 16.dp, content: @Composable () -> Unit) { rows += inset to content }
}

@Composable
fun Section(header: String? = null, footer: String? = null, build: GroupBuilder.() -> Unit) {
    val b = GroupBuilder().apply(build)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (header != null) Text(header.uppercase(), color = P.muted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = .8.sp, modifier = Modifier.padding(horizontal = 16.dp))
        if (b.rows.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P.group)) {
            b.rows.forEachIndexed { i, (inset, c) ->
                if (i > 0) Box(Modifier.padding(start = inset).fillMaxWidth().height(.6.dp).background(P.sep))
                c()
            }
        }
        if (footer != null) Text(footer, color = P.muted, fontSize = 12.5.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun pressBg(src: MutableInteractionSource): Color {
    val pressed by src.collectIsPressedAsState()
    return if (pressed) P.groupPressed else Color.Transparent
}

@Composable
fun NavRow(icon: ImageVector?, title: String, value: String? = null, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).background(pressBg(src))
            .clickable(src, null, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(P.iconBg), contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = P.accent, modifier = Modifier.size(17.dp)) }
        Text(title, color = P.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = P.muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 170.dp))
        Icon(Ic.chevron, null, tint = Color(0xFF5B6490), modifier = Modifier.size(16.dp))
    }
}

@Composable
fun FieldRow(
    label: String, value: String, onChange: (String) -> Unit,
    placeholder: String = "", keyboard: KeyboardType = KeyboardType.Text, mono: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, color = P.text, fontSize = 16.sp)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (value.isEmpty()) Text(placeholder, color = Color(0xFF59628C), fontSize = 15.sp, maxLines = 1, textAlign = TextAlign.End)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = P.text, fontSize = 15.sp, textAlign = TextAlign.End,
                    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default),
                cursorBrush = SolidColor(P.accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(remember { MutableInteractionSource() }, null) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = P.text, fontSize = 16.sp)
            if (sub != null) Text(sub, color = P.muted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        IosSwitch(checked)
    }
}

@Composable
fun IosSwitch(checked: Boolean) {
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, tween(250, easing = IosEasing), label = "sw")
    val bg by animateColorAsState(if (checked) P.green else Color(0x5978809F), tween(250), label = "swbg")
    Box(Modifier.size(51.dp, 31.dp).clip(RoundedCornerShape(16.dp)).background(bg).padding(2.dp)) {
        Box(Modifier.offset(x = x).size(27.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape))
    }
}

@Composable
fun ActionRow(text: String, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).background(pressBg(src))
            .clickable(src, null, enabled = enabled, onClick = onClick).padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = (if (danger) P.danger else P.accent).copy(alpha = if (enabled) 1f else .45f),
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ColRow(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderRow(
    label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit, trackBrush: Brush? = null, onDone: () -> Unit = {},
) {
    ColRow {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = P.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = P.accent, fontSize = 15.sp)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Color.White),
            thumb = { Box(Modifier.size(26.dp).shadow(4.dp, CircleShape).background(Color.White, CircleShape)) },
            track = { st ->
                val span = st.valueRange.endInclusive - st.valueRange.start
                val frac = if (span == 0f) 0f else ((st.value - st.valueRange.start) / span).coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxWidth().height(if (trackBrush != null) 8.dp else 4.dp).clip(RoundedCornerShape(50))
                        .background(trackBrush ?: SolidColor(Color.White.copy(alpha = .14f)))
                ) {
                    if (trackBrush == null) Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(P.accent))
                }
            },
            modifier = Modifier.fillMaxWidth().height(30.dp),
        )
    }
}

/** Сегменты с подложкой, которая плавно переезжает к выбранному варианту. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Color(0x2E7680B4)).padding(2.dp)
    ) {
        val segW = maxWidth / options.size
        val x by animateDpAsState(segW * selected.coerceAtLeast(0), tween(380, easing = IosEasing), label = "seg")
        Box(Modifier.offset(x = x).width(segW).height(34.dp).shadow(2.dp, RoundedCornerShape(7.dp))
            .background(Color(0xFF3A4272), RoundedCornerShape(7.dp)))
        Row(Modifier.fillMaxWidth().height(34.dp)) {
            options.forEachIndexed { i, o ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(o, color = if (i == selected) Color.White else Color(0xFFC9D0F0), fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun StepperRow(title: String, value: Int, onChange: (Int) -> Unit, min: Int, max: Int) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = P.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Row(Modifier.clip(RoundedCornerShape(9.dp)).background(Color(0x2E7680B4)), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp, 32.dp).clickable { if (value > min) onChange(value - 1) }, contentAlignment = Alignment.Center) {
                Text("−", color = P.text, fontSize = 20.sp)
            }
            Text("$value", color = P.text, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.width(34.dp))
            Box(Modifier.size(44.dp, 32.dp).clickable { if (value < max) onChange(value + 1) }, contentAlignment = Alignment.Center) {
                Text("+", color = P.text, fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun MonoBox(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Color(0xFFB9C3EA), fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x4D000000)).padding(10.dp))
}
