package app.launchertab

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

private val PT = Locale.forLanguageTag("pt-BR")

@Composable
fun ClockWidget(pal: Pal) {
    val now by produceState(LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(5000) }
    }
    val weekday = now.dayOfWeek.getDisplayName(JTextStyle.FULL, PT).replaceFirstChar { it.uppercase() }
    val date = now.format(DateTimeFormatter.ofPattern("d 'de' MMMM", PT))
    val time = String.format(PT, "%02d:%02d", now.hour, now.minute)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val u = maxWidth.value / 100f
        Column(Modifier.fillMaxSize().padding((8f * u).dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text1(weekday, pal.ink2, 6.6f * u, FontWeight.Medium)
                Text1(date, pal.ink3, 6.6f * u, FontWeight.Medium)
            }
            Text1(time, pal.ink, 27f * u, FontWeight.Medium, spacing = -1.1f * u)
        }
    }
}

@Composable
fun WeatherWidget(pal: Pal) {
    BoxWithConstraints(
        Modifier.fillMaxSize().drawBehind {
            val r = size.width * 0.4f
            val c = Offset(size.width * 0.95f, size.height * 0.02f)
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFFFD98A).copy(alpha = if (pal.dark) 0.5f else 0.85f), Color(0xFFFFB774).copy(alpha = 0.35f), Color.Transparent), c, r),
                r, c
            )
        }
    ) {
        val u = maxWidth.value / 100f
        Column(Modifier.fillMaxSize().padding((8f * u).dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text1("Clima", pal.ink2, 6.6f * u, FontWeight.Medium)
                Text1("Parcialmente nublado", pal.ink3, 6.6f * u, FontWeight.Medium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text1("26°", pal.ink, 27f * u, FontWeight.Medium, spacing = -1.1f * u)
                Column(horizontalAlignment = Alignment.End) {
                    Text1("Máx. 29°", pal.ink2, 5.6f * u, FontWeight.Normal)
                    Text1("Mín. 17°", pal.ink2, 5.6f * u, FontWeight.Normal)
                }
            }
        }
    }
}

private data class Track(val title: String, val artist: String, val dur: Int, val a: Color, val b: Color)

private val TRACKS = listOf(
    Track("Maré Alta", "Cais Azul", 214, Color(0xFF9FC0F5), Color(0xFFFFD2A0)),
    Track("Campo Aberto", "Linha do Horizonte", 187, Color(0xFFBFE6A0), Color(0xFF8FD6A8)),
    Track("Luz de Domingo", "Vento Manso", 241, Color(0xFFF6D0C0), Color(0xFFF5A6C8)),
)

@Composable
fun MediaIcon(kind: String, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun tri(x1: Float, x2: Float) = Path().apply {
            moveTo(w * x1, h * .25f); lineTo(w * x2, h * .5f); lineTo(w * x1, h * .75f); close()
        }
        when (kind) {
            "play" -> drawPath(Path().apply { moveTo(w * .32f, h * .2f); lineTo(w * .8f, h * .5f); lineTo(w * .32f, h * .8f); close() }, color)
            "pause" -> {
                drawRect(color, Offset(w * .28f, h * .2f), Size(w * .16f, h * .6f))
                drawRect(color, Offset(w * .56f, h * .2f), Size(w * .16f, h * .6f))
            }
            "next" -> { drawPath(tri(.2f, .66f), color); drawRect(color, Offset(w * .7f, h * .25f), Size(w * .1f, h * .5f)) }
            else -> {
                drawPath(Path().apply { moveTo(w * .8f, h * .25f); lineTo(w * .34f, h * .5f); lineTo(w * .8f, h * .75f); close() }, color)
                drawRect(color, Offset(w * .2f, h * .25f), Size(w * .1f, h * .5f))
            }
        }
    }
}

@Composable
fun MusicWidget(pal: Pal) {
    var idx by remember { mutableIntStateOf(0) }
    var pos by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    val t = TRACKS[idx]
    LaunchedEffect(playing, idx) {
        while (playing) {
            delay(1000)
            pos += 1
            if (pos >= t.dur) { idx = (idx + 1) % TRACKS.size; pos = 0 }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val u = maxWidth.value / 100f
        Column(Modifier.fillMaxSize().padding((8f * u).dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(
                Modifier.size((30f * u).dp).clip(RoundedCornerShape((7f * u).dp))
                    .background(Brush.linearGradient(listOf(t.a, t.b)))
            )
            Column {
                Text1(t.title, pal.ink, 7f * u, FontWeight.Bold)
                Text1(t.artist, pal.ink2, 5.6f * u, FontWeight.Normal)
            }
            Box(Modifier.fillMaxWidth().height((1.4f * u).dp).clip(CircleShape).background(pal.ink3.copy(alpha = .3f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((pos / t.dur.toFloat()).coerceIn(0f, 1f)).background(pal.ink))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                MediaIcon("prev", pal.ink, Modifier.size((15f * u).dp).clickable { if (pos > 3) pos = 0 else { idx = (idx + TRACKS.size - 1) % TRACKS.size; pos = 0 } })
                Box(
                    Modifier.size((17f * u).dp).clip(CircleShape).background(pal.ink).clickable { playing = !playing },
                    contentAlignment = Alignment.Center
                ) { MediaIcon(if (playing) "pause" else "play", pal.inv, Modifier.size((11f * u).dp)) }
                MediaIcon("next", pal.ink, Modifier.size((15f * u).dp).clickable { idx = (idx + 1) % TRACKS.size; pos = 0 })
            }
        }
    }
}

@Composable
fun NotesWidget(pal: Pal, st: LState) {
    var txt by remember { mutableStateOf("") }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val u = maxWidth.value / 100f
        Column(Modifier.fillMaxSize().padding((8f * u).dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text1("Notas", pal.ink2, 6.6f * u, FontWeight.Medium)
            Column(verticalArrangement = Arrangement.spacedBy((2.4f * u).dp)) {
                st.notes.take(3).forEachIndexed { i, n ->
                    Row(
                        Modifier.fillMaxWidth().clickable { st.notes[i] = n.copy(done = !n.done); st.save() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy((3.4f * u).dp)
                    ) {
                        Box(
                            Modifier.size((5.4f * u).dp).clip(CircleShape)
                                .background(if (n.done) pal.ink else Color.Transparent)
                                .border((0.5f * u).dp, if (n.done) pal.ink else pal.ink3, CircleShape)
                        )
                        Text1(n.text, if (n.done) pal.ink3 else pal.ink, 6f * u, FontWeight.Medium, maxLines = 1, strike = n.done)
                    }
                }
            }
            BasicTextField(
                value = txt,
                onValueChange = { txt = it },
                singleLine = true,
                textStyle = TextStyle(color = pal.ink, fontSize = (6f * u).sp),
                cursorBrush = SolidColor(pal.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (txt.isNotBlank()) { st.notes.add(0, NoteItem(txt.trim(), false)); st.save(); txt = "" }
                }),
                decorationBox = { inner ->
                    if (txt.isEmpty()) Text1("+ Nova nota", pal.ink3, 6f * u, FontWeight.Normal)
                    inner()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Mostra um widget real de outro app instalado no tablet. */
@Composable
fun AndroidWidgetView(id: Int, host: AppWidgetHost, mgr: AppWidgetManager, modifier: Modifier) {
    val info = remember(id) { mgr.getAppWidgetInfo(id) } ?: return
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    AndroidView(
        modifier = modifier.onSizeChanged { size = it },
        factory = { c -> host.createView(c, id, info) },
        update = { v ->
            if (size.width > 0) {
                val wd = (size.width / density).toInt()
                val hd = (size.height / density).toInt()
                v.updateAppWidgetSize(Bundle(), wd, hd, wd, hd)
            }
        }
    )
}
