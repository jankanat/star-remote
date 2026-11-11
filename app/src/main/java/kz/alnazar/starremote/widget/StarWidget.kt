package kz.alnazar.starremote.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kz.alnazar.starremote.MainActivity
import kz.alnazar.starremote.StarApp
import kz.alnazar.starremote.control.Tone
import kz.alnazar.starremote.control.WidgetActionWorker
import kz.alnazar.starremote.data.AppSettings

class StarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StarWidget()
}

private data class WButton(val kind: String, val name: String, val dot: Color)

class StarWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = StarApp.of(context)
        app.controller.refresh()
        provideContent {
            val s by app.settings.flow.collectAsState()
            val st by app.controller.status.collectAsState()
            Content(s, st.text, st.tone, st.busy)
        }
    }

    @Composable
    private fun Content(s: AppSettings, text: String, tone: Tone, busy: Boolean) {
        val size = LocalSize.current
        val light = s.widgetLight / 100f
        val bg = Color.hsl(s.widgetHue.coerceIn(0f, 360f), s.widgetSat / 100f, light, alpha = 1f - s.widgetTransparency / 100f)
        val fg = if (s.widgetLight > 60) Color(0xFF14182E) else Color(0xFFEEF1FF)
        val chip = if (s.widgetLight > 60) Color(0x22000000) else Color(0x1FFFFFFF)
        val toneColor = when (tone) {
            Tone.ON -> Color(0xFF9BE7B0)
            Tone.WAIT -> Color(0xFFFFD98A)
            Tone.ERROR -> Color(0xFFFF8A8A)
            Tone.IDLE -> Color(0xFF8F99C6)
        }

        val all = buildList {
            add(WButton("on", "Включить", Color(0xFF5FD27E)))
            add(WButton("off", "Выключить", Color(0xFFFF6B6B)))
            s.custom.forEach { add(WButton("custom:${it.id}", it.name, Color(0xFFFFDF8F))) }
        }
        val wide = size.width >= 230.dp
        val cols = if (wide) 2 else 1
        val rows = (((size.height.value - 40f) / 50f).toInt()).coerceIn(1, 4)
        val shown = all.take(rows * cols)
        val more = all.size - shown.size

        Column(
            modifier = GlanceModifier.fillMaxSize().cornerRadius(24.dp)
                .background(ColorProvider(bg)).padding(12.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(ColorProvider(toneColor))) {}
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = if (busy) text else if (wide) "iMac · $text" else text,
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(fg), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (more > 0) {
                    Text("+$more", style = TextStyle(color = ColorProvider(fg.copy(alpha = .75f)), fontSize = 11.sp))
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            shown.chunked(cols).forEachIndexed { i, row ->
                if (i > 0) Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    row.forEachIndexed { j, b ->
                        if (j > 0) Spacer(GlanceModifier.width(6.dp))
                        Btn(b, fg, chip, GlanceModifier.defaultWeight())
                    }
                    if (row.size < cols) {
                        Spacer(GlanceModifier.width(6.dp))
                        Spacer(GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }

    @Composable
    private fun Btn(b: WButton, fg: Color, chip: Color, modifier: GlanceModifier) {
        Row(
            modifier = modifier.fillMaxSize().cornerRadius(14.dp).background(ColorProvider(chip))
                .padding(horizontal = 10.dp)
                .clickable(actionRunCallback<WidgetClick>(actionParametersOf(KindKey to b.kind))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(ColorProvider(b.dot))) {}
            Spacer(GlanceModifier.width(8.dp))
            Text(b.name, maxLines = 1, style = TextStyle(color = ColorProvider(fg), fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }
    }

    companion object {
        suspend fun refresh(ctx: Context) {
            runCatching { StarWidget().updateAll(ctx) }
        }
    }
}

val KindKey = ActionParameters.Key<String>("kind")

class WidgetClick : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val kind = parameters[KindKey] ?: return
        WidgetActionWorker.enqueue(context, kind)
    }
}
