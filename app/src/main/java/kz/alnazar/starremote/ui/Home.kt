package kz.alnazar.starremote.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.alnazar.starremote.control.Status
import kz.alnazar.starremote.control.Tone
import kz.alnazar.starremote.data.AppSettings
import kz.alnazar.starremote.data.CustomButton

@Composable
fun HomeScreen(
    st: Status,
    s: AppSettings,
    sky: StarSky,
    backdrop: Backdrop,
    onOn: () -> Unit,
    onOff: () -> Unit,
    onCustom: (CustomButton) -> Unit,
    onTorr: () -> Unit,
    onSettings: () -> Unit,
) {
    val dens = LocalDensity.current
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 52.dp)
    ) {
        // верхняя строка
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Звёздный пульт", color = P.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .3.sp)
                Text("iMac 21.5″ · ${st.mac}", color = P.muted, fontSize = 12.5.sp)
            }
            Glass(backdrop, RoundedCornerShape(14.dp), Modifier.size(44.dp), onClick = onSettings) {
                Icon(Ic.sliders, "Настройки", tint = P.text, modifier = Modifier.size(20.dp).align(Alignment.Center))
            }
        }
        Spacer(Modifier.padding(top = 12.dp))
        StatusPill(st, backdrop)

        // свободное место над кнопками: здесь может гореть звезда-индикатор
        Box(
            Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { c ->
                val b = c.boundsInRoot()
                val padX = b.width * .12f
                with(dens) {
                    sky.zone = Rect(b.left + padX, b.top + 24.dp.toPx(), b.right - padX, b.bottom - 36.dp.toPx())
                }
            }
        )

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AnimatedVisibility(
                visible = st.isOn && !st.busy,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 3 },
                exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { it / 3 },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Glass(backdrop, RoundedCornerShape(50), onClick = onTorr) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.external, null, tint = P.text, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Открыть TorrServe", color = P.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (s.custom.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    s.custom.forEach { b ->
                        Glass(backdrop, RoundedCornerShape(16.dp), enabled = !st.busy, onClick = { onCustom(b) }) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(P.accent, CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(b.name, color = P.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }
            }
            MainButton(backdrop, Ic.power, "Включить", "Шорткат «${s.onShortcut}» · caffeinate", warm = true,
                enabled = !st.busy && !st.isOn, sheenOffset = 0, onClick = onOn)
            MainButton(backdrop, Ic.moon, "Выключить", "Шорткат «${s.offShortcut}» · Mac снова спит", warm = false,
                enabled = !st.busy && st.isOn, sheenOffset = 2200, onClick = onOff)
        }
    }
}

@Composable
private fun StatusPill(st: Status, backdrop: Backdrop) {
    val inf = rememberInfiniteTransition(label = "pulse")
    val pulse by inf.animateFloat(1f, .35f, infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "p")
    val (dot, a) = when (st.tone) {
        Tone.ON -> P.ok to 1f
        Tone.WAIT -> P.wait to pulse
        Tone.ERROR -> P.danger to 1f
        Tone.IDLE -> P.idle to 1f
    }
    Glass(backdrop, RoundedCornerShape(50), Modifier.widthIn(max = 460.dp)) {
        Row(Modifier.padding(start = 12.dp, end = 14.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).graphicsLayer { scaleX = if (st.tone == Tone.WAIT) .7f + .3f * a else 1f; scaleY = scaleX }
                .alpha(a).background(dot, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(st.text, color = P.text, fontSize = 13.5.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun MainButton(
    backdrop: Backdrop, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String,
    warm: Boolean, enabled: Boolean, sheenOffset: Int, onClick: () -> Unit,
) {
    Glass(
        backdrop, RoundedCornerShape(28.dp), Modifier.fillMaxWidth().heightIn(min = 94.dp),
        enabled = enabled, sheen = true, sheenOffsetMs = sheenOffset, onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp).align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val glow = if (warm) Color(0xFFFFECAA) else Color(0xFF96AFFF)
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(18.dp))
                    .background(Brush.radialGradient(listOf(glow.copy(alpha = .33f), glow.copy(alpha = .05f))))
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = if (warm) P.flare else Color(0xFFC9D4FF), modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = P.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(sub, color = P.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
