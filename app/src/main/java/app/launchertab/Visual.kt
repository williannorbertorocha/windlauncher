package app.launchertab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow

/** Camada do papel de parede já desfocada, usada pelo vidro dos widgets. */
val LocalBlur = compositionLocalOf<GraphicsLayer?> { null }

/** Painel de vidro fosco: desfoca o papel de parede atrás e aplica um véu translúcido. */
@Composable
fun GlassBox(
    modifier: Modifier,
    shape: Shape,
    pal: Pal,
    strong: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val layer = LocalBlur.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    val veil = if (strong) pal.sheet else pal.tint
    Box(
        modifier
            .onGloballyPositioned { pos = it.positionInRoot() }
            .clip(shape)
            .drawBehind {
                if (layer != null) {
                    withTransform({ translate(-pos.x, -pos.y) }) { drawLayer(layer) }
                }
                drawRect(veil)
            }
            .border(1.dp, pal.edge, shape),
        content = content
    )
}

/** Texto simples com tamanho em sp (usado nos widgets e nas telas). */
@Composable
fun Text1(
    text: String,
    color: Color,
    sizeSp: Float,
    weight: FontWeight = FontWeight.Normal,
    spacing: Float = 0f,
    maxLines: Int = Int.MAX_VALUE,
    strike: Boolean = false,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color,
            fontSize = sizeSp.sp,
            fontWeight = weight,
            letterSpacing = spacing.sp,
            textDecoration = if (strike) TextDecoration.LineThrough else TextDecoration.None,
        )
    )
}

@Composable
fun VIcon(v: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Image(imageVector = v, contentDescription = null, modifier = modifier, colorFilter = ColorFilter.tint(tint))
}

/** Papel de parede: desenha o fundo e grava uma cópia para o desfoque do vidro. */
@Composable
fun Wallpaper(st: LState, blurLayer: GraphicsLayer) {
    Box(
        Modifier
            .fillMaxSize()
            .drawWithContent {
                blurLayer.record { this@drawWithContent.drawContent() }
                drawContent()
            }
    ) {
        val photo = st.photo
        if (st.wp == "photo" && photo != null) {
            Image(bitmap = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Canvas(Modifier.fillMaxSize()) { drawPreset(if (st.wp == "photo") "hills" else st.wp, st.dark) }
        }
        if (st.dark) Box(Modifier.fillMaxSize().background(Color(0x4D000000)))
    }
}

private fun DrawScope.drawPreset(kind: String, dark: Boolean) {
    val w = size.width
    val h = size.height
    when (kind) {
        "mist" -> {
            drawRect(if (dark) Color(0xFF1A1D20) else Color(0xFFDCDFE1))
            val a = if (dark) 0.38f else 0.75f
            fun blob(cx: Float, cy: Float, r: Float, c: Color) =
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), Offset(cx * w, cy * h), r * w), r * w, Offset(cx * w, cy * h))
            blob(.25f, .75f, .34f, Color(0xFF7ACC5C))
            blob(.58f, .68f, .30f, Color(0xFFFF963C))
            blob(.85f, .12f, .26f, Color(0xFFFF64A0))
            blob(.10f, .08f, .28f, Color(0xFF6EA0F5))
        }
        "aurora" -> {
            val c = if (dark) listOf(Color(0xFF2B2142), Color(0xFF1F2A4A), Color(0xFF183645))
            else listOf(Color(0xFFF6E0D0), Color(0xFFE1D8F2), Color(0xFFCFE6EF))
            drawRect(Brush.linearGradient(c, Offset.Zero, Offset(w, h)))
            val a = if (dark) 0.38f else 0.75f
            fun blob(cx: Float, cy: Float, r: Float, col: Color) =
                drawCircle(Brush.radialGradient(listOf(col.copy(alpha = a), Color.Transparent), Offset(cx * w, cy * h), r * w), r * w, Offset(cx * w, cy * h))
            blob(.12f, .85f, .38f, Color(0xFFFFBE96))
            blob(.70f, .08f, .34f, Color(0xFFBEAAFA))
            blob(.88f, .82f, .32f, Color(0xFF96D7EB))
        }
        else -> {
            val sky = if (dark) listOf(Color(0xFF16213A), Color(0xFF24365A), Color(0xFF364D66))
            else listOf(Color(0xFF9DBFE0), Color(0xFFC3D8EA), Color(0xFFE3EDF0))
            drawRect(Brush.verticalGradient(0f to sky[0], 0.42f to sky[1], 0.62f to sky[2], 1f to sky[2]))
            val sx = w / 1600f
            val sy = h / 1000f
            fun hill(cols: Pair<Color, Color>, build: Path.() -> Unit) {
                val p = Path().apply(build)
                drawPath(p, Brush.verticalGradient(listOf(cols.first, cols.second), startY = 540f * sy, endY = h))
            }
            hill((if (dark) Color(0xFF2A4A3A) else Color(0xFF9FBF9A)) to (if (dark) Color(0xFF1F3A2C) else Color(0xFF6F9667))) {
                moveTo(0f, 640f * sy)
                cubicTo(250f * sx, 560f * sy, 500f * sx, 610f * sy, 800f * sx, 580f * sy)
                cubicTo(1100f * sx, 550f * sy, 1350f * sx, 520f * sy, 1600f * sx, 590f * sy)
                lineTo(w, h); lineTo(0f, h); close()
            }
            hill((if (dark) Color(0xFF213F2C) else Color(0xFF86B062)) to (if (dark) Color(0xFF183222) else Color(0xFF5F8A48))) {
                moveTo(0f, 730f * sy)
                cubicTo(300f * sx, 650f * sy, 600f * sx, 710f * sy, 900f * sx, 668f * sy)
                cubicTo(1200f * sx, 626f * sy, 1400f * sx, 640f * sy, 1600f * sx, 704f * sy)
                lineTo(w, h); lineTo(0f, h); close()
            }
            hill((if (dark) Color(0xFF1B3523) else Color(0xFF78A43F)) to (if (dark) Color(0xFF112617) else Color(0xFF4F7A30))) {
                moveTo(0f, 830f * sy)
                cubicTo(350f * sx, 750f * sy, 650f * sx, 810f * sy, 1000f * sx, 776f * sy)
                cubicTo(1350f * sx, 742f * sy, 1450f * sx, 746f * sy, 1600f * sx, 806f * sy)
                lineTo(w, h); lineTo(0f, h); close()
            }
        }
    }
}
