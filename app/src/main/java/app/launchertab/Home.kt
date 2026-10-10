package app.launchertab

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Ligações do launcher com o sistema Android. */
class Host(
    val widgetHost: AppWidgetHost,
    val widgetMgr: AppWidgetManager,
    val pickPhoto: () -> Unit,
    val addWidget: (AppWidgetProviderInfo) -> Unit,
    val openUrl: (String) -> Unit,
)

private class Hit(val key: String, val fromDock: Boolean, val rect: Rect)

private class DragS {
    var key by mutableStateOf<String?>(null)
    var fromDock by mutableStateOf(false)
    var pos by mutableStateOf(Offset.Zero)
    var grab by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(Size.Zero)
    var removeHot by mutableStateOf(false)
    var dockSlot by mutableIntStateOf(-1)
    var tgtApp by mutableStateOf<String?>(null)
    var edge by mutableIntStateOf(0)
}

private class Geo(
    val wp: Float, val hp: Float, val dp: Float, val topInset: Float, val botInset: Float,
    val cols: Int, val rows: Int, val cell: Float, val ox: Float, val oy: Float,
    val dockLeft: Float, val dockTop: Float, val dockW: Float, val dockH: Float, val dt: Float, val n: Int,
    val pg: Int, val pages: Int, val placed: List<Placed>, val tx: () -> Float,
) {
    val g get() = cell * .06f
    fun rectOf(p: Placed): Rect {
        val x = tx() + p.page * wp + ox + p.c * cell
        val y = oy + p.r * cell
        return if (p.key.startsWith("a:")) Rect(x, y, x + cell, y + cell)
        else Rect(x + g, y + g, x + p.w * cell - g, y + p.h * cell - g)
    }
    fun iconRect(i: Int): Rect {
        val x = dockLeft + 14 * dp + i * (dt + 14 * dp)
        val y = dockTop + (dockH - dt) / 2
        return Rect(x, y, x + dt, y + dt)
    }
    fun dockSlotAt(p: Offset): Int {
        if (n == 0) return -1
        if (p.y < dockTop - 14 * dp || p.y > dockTop + dockH + 14 * dp) return -1
        if (p.x < dockLeft - 14 * dp || p.x > dockLeft + dockW + 14 * dp) return -1
        var best = 0; var bd = Float.MAX_VALUE
        for (i in 0 until n) { val d = abs(iconRect(i).center.x - p.x); if (d < bd) { bd = d; best = i } }
        return best
    }
    fun cellAt(p: Offset): Pair<Int, Int> =
        ((p.x - ox) / cell).toInt().coerceIn(0, cols - 1) to ((p.y - oy) / cell).toInt().coerceIn(0, rows - 1)
}

@Composable
fun HomeScreen(st: LState, apps: List<AppInfo>, host: Host) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pal = remember(st.dark) { Pal(st.dark) }
    val appMap = remember(apps) { apps.associateBy { it.pkg } }
    val blurLayer: GraphicsLayer = rememberGraphicsLayer()
    SideEffect { blurLayer.renderEffect = BlurEffect(64f, 64f, TileMode.Clamp) }

    var settingsOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    var ringAt by remember { mutableStateOf<Offset?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var swipe by remember { mutableFloatStateOf(0f) }
    val trackX = remember { Animatable(0f) }
    val drag = remember { DragS() }
    val ringP = remember { Animatable(0f) }

    LaunchedEffect(apps, st.dockCount, st.widgets.size) {
        st.fixDock(apps); st.fixOrder(apps); st.save()
    }
    LaunchedEffect(ringAt) {
        ringP.snapTo(0f)
        if (ringAt != null) { delay(300); ringP.animateTo(1f, tween(1700, easing = LinearEasing)) }
    }
    BackHandler(enabled = settingsOpen || searchOpen || menuAt != null) {
        when { searchOpen -> searchOpen = false; settingsOpen -> settingsOpen = false; else -> menuAt = null }
    }

    fun exists(k: String): Boolean = when {
        k.startsWith("a:") -> appMap.containsKey(k.removePrefix("a:"))
        k.startsWith("x:") -> k in st.widgets && host.widgetMgr.getAppWidgetInfo(k.removePrefix("x:").toInt()) != null
        else -> k in st.widgets
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wp = constraints.maxWidth.toFloat()
        val hp = constraints.maxHeight.toFloat()
        val dp = density.density
        val land = wp > hp
        val topInset = WindowInsets.systemBars.getTop(density).toFloat()
        val botInset = WindowInsets.systemBars.getBottom(density).toFloat()
        val cols = if (land) st.colsL else st.colsP
        val rows = if (land) st.rowsL else st.rowsP
        val padX = (if (land) 72 else 48) * dp
        val dockH = 96 * dp
        val dockB = botInset + 22 * dp
        val gridTop = topInset + (if (st.search) 96 else 24) * dp
        val gridBottom = hp - dockB - dockH - 46 * dp
        val areaW = wp - 2 * padX
        val areaH = gridBottom - gridTop
        val cell = max(40f, floor(min(areaW / cols, areaH / rows)))
        val ox = (wp - cell * cols) / 2
        val oy = gridTop + (areaH - cell * rows) / 2
        val n = st.dock.size
        val dt = min(72 * dp, (wp - 80 * dp - 28 * dp - max(n - 1, 0) * 14 * dp) / max(n, 1))
        val dockW = n * dt + max(n - 1, 0) * 14 * dp + 28 * dp
        val dockLeft = (wp - dockW) / 2
        val dockTop = hp - dockB - dockH

        val placed = pack(st.order.toList(), cols, rows, st::spanOf, ::exists)
        val pages = (placed.maxOfOrNull { it.page } ?: 0) + 1
        val pg = page.coerceIn(0, pages - 1)
        val geo = Geo(wp, hp, dp, topInset, botInset, cols, rows, cell, ox, oy, dockLeft, dockTop, dockW, dockH, dt, n, pg, pages, placed) { trackX.value + swipe }
        val geoS = rememberUpdatedState(geo)

        fun goTo(p: Int) {
            page = p.coerceIn(0, geoS.value.pages - 1)
            scope.launch { trackX.animateTo(-page * geoS.value.wp, tween(550, easing = FastOutSlowInEasing)) }
        }
        LaunchedEffect(wp) { trackX.snapTo(-pg * wp) }
        LaunchedEffect(drag.edge) {
            if (drag.edge != 0) { delay(650); goTo(geoS.value.pg + drag.edge); drag.edge = 0 }
        }

        fun removeWidget(k: String) {
            if (k.startsWith("x:")) runCatching { host.widgetHost.deleteAppWidgetId(k.removePrefix("x:").toInt()) }
            st.widgets.remove(k); st.order.remove(k); st.spans.remove(k)
        }

        fun reorderTo(f: Geo, p: Offset) {
            val key = drag.key ?: return
            val (c, r) = f.cellAt(p)
            val ks = st.order.filter { it != key }
            val pk = pack(ks, f.cols, f.rows, st::spanOf, ::exists)
            var idx = pk.size
            var found = false
            for (i in pk.indices) {
                val q = pk[i]
                if (q.page == f.pg && c in q.c until q.c + q.w && r in q.r until q.r + q.h) {
                    val left = (p.x - (f.ox + q.c * f.cell)) < q.w * f.cell / 2
                    idx = if (left) i else i + 1; found = true; break
                }
            }
            if (!found) for (i in pk.indices) {
                val q = pk[i]
                if (q.page > f.pg || (q.page == f.pg && (q.r > r || (q.r == r && q.c > c)))) { idx = i; break }
            }
            val ns = pk.map { it.key }.toMutableList()
            ns.add(idx.coerceIn(0, ns.size), key)
            if (ns != st.order.toList()) { st.order.clear(); st.order.addAll(ns) }
        }

        fun appAt(f: Geo, p: Offset): String? {
            val (c, r) = f.cellAt(p)
            return f.placed.firstOrNull { it.key.startsWith("a:") && it.page == f.pg && c in it.c until it.c + it.w && r in it.r until it.r + it.h }?.key?.removePrefix("a:")
        }

        fun dragMove(p: Offset) {
            val f = geoS.value
            val key = drag.key ?: return
            drag.pos = p
            if (!drag.fromDock) {
                val isW = !key.startsWith("a:")
                drag.removeHot = isW && p.y < f.topInset + 88 * f.dp && abs(p.x - f.wp / 2) < 170 * f.dp
                drag.dockSlot = if (!isW) f.dockSlotAt(p) else -1
                if (!drag.removeHot && drag.dockSlot < 0) reorderTo(f, p)
                drag.edge = if (p.x < 64 * f.dp) -1 else if (p.x > f.wp - 64 * f.dp) 1 else 0
            } else {
                drag.dockSlot = f.dockSlotAt(p)
                drag.tgtApp = if (drag.dockSlot < 0) appAt(f, p) else null
            }
        }

        fun endDrag() {
            val key = drag.key ?: return
            if (!drag.fromDock) {
                if (!key.startsWith("a:") && drag.removeHot) removeWidget(key)
                else if (key.startsWith("a:") && drag.dockSlot >= 0 && drag.dockSlot < st.dock.size) {
                    val pkg = key.removePrefix("a:")
                    val x = st.dock[drag.dockSlot]
                    st.dock[drag.dockSlot] = pkg
                    val oi = st.order.indexOf(key)
                    if (oi >= 0) st.order[oi] = "a:$x"
                }
            } else {
                val pkg = key.removePrefix("a:")
                val from = st.dock.indexOf(pkg)
                val slot = drag.dockSlot
                if (from >= 0 && slot >= 0 && slot != from) { st.dock.removeAt(from); st.dock.add(slot.coerceAtMost(st.dock.size), pkg) }
                else if (from >= 0 && slot < 0 && drag.tgtApp != null) {
                    val y = drag.tgtApp!!
                    st.dock[from] = y
                    val oj = st.order.indexOf("a:$y")
                    if (oj >= 0) st.order[oj] = "a:$pkg"
                }
            }
            drag.key = null; drag.removeHot = false; drag.dockSlot = -1; drag.tgtApp = null; drag.edge = 0
            st.fixOrder(apps); st.save()
        }

        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalBlur provides blurLayer) {
                Wallpaper(st, blurLayer)

                Box(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            if (settingsOpen || searchOpen) return@awaitEachGesture
                            menuAt = null
                            val f = geoS.value
                            val start = down.position
                            var hit: Hit? = null
                            if (f.dockSlotAt(start) >= 0) {
                                val i = f.dockSlotAt(start)
                                if (i < st.dock.size && f.iconRect(i).inflate(8 * f.dp).contains(start))
                                    hit = Hit("a:" + st.dock[i], true, f.iconRect(i))
                            }
                            if (hit == null) {
                                val q = f.placed.firstOrNull { it.page == f.pg && f.rectOf(it).contains(start) }
                                if (q != null) hit = Hit(q.key, false, f.rectOf(q))
                            }
                            val onSearchPill = st.search && start.y < f.topInset + 80 * f.dp && abs(start.x - f.wp / 2) < 340 * f.dp
                            if (onSearchPill) return@awaitEachGesture
                            val id = down.id
                            val t0 = down.uptimeMillis
                            val holdMs = if (hit != null) 380L else 2000L
                            if (hit == null) ringAt = start
                            var mode = 0
                            try {
                                while (true) {
                                    val ev = if (mode == 0) {
                                        val left = holdMs - (SystemClock.uptimeMillis() - t0)
                                        withTimeoutOrNull(left.coerceAtLeast(1L)) { awaitPointerEvent(PointerEventPass.Initial) }
                                    } else awaitPointerEvent(PointerEventPass.Initial)
                                    if (ev == null) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (hit != null) {
                                            drag.key = hit.key; drag.fromDock = hit.fromDock; drag.pos = start
                                            drag.grab = start - hit.rect.topLeft; drag.size = hit.rect.size
                                            mode = 2
                                        } else { menuAt = start; ringAt = null; mode = 3 }
                                        continue
                                    }
                                    val ch = ev.changes.firstOrNull { it.id == id } ?: break
                                    if (!ch.pressed) {
                                        if (mode >= 1) ch.consume()
                                        if (mode == 1) {
                                            val total = swipe
                                            val np = if (total < -120 * f.dp) f.pg + 1 else if (total > 120 * f.dp) f.pg - 1 else f.pg
                                            scope.launch {
                                                trackX.snapTo(trackX.value + total); swipe = 0f
                                                goTo(np)
                                            }
                                        }
                                        if (mode == 2) endDrag()
                                        break
                                    }
                                    when (mode) {
                                        0 -> {
                                            val d = ch.position - start
                                            if (d.getDistance() > viewConfiguration.touchSlop) {
                                                ringAt = null
                                                mode = if (abs(d.x) > abs(d.y) && f.pages > 1 && hit?.fromDock != true) 1 else 3
                                            }
                                        }
                                        1 -> {
                                            ch.consume()
                                            val atEdge = (f.pg == 0 && swipe + (ch.position.x - ch.previousPosition.x) > 0) ||
                                                (f.pg == f.pages - 1 && swipe + (ch.position.x - ch.previousPosition.x) < 0)
                                            swipe += (ch.position.x - ch.previousPosition.x) * (if (atEdge) 0.3f else 1f)
                                        }
                                        2 -> { ch.consume(); dragMove(ch.position) }
                                    }
                                }
                            } finally {
                                ringAt = null
                                if (drag.key != null) endDrag()
                            }
                        }
                    }
                ) {
                    val labelWhite = st.dark || when (st.wp) { "hills" -> true; "photo" -> st.photoWhite; else -> false }
                    for (p in placed) key(p.key) {
                        if (abs(p.page - pg) <= 1) {
                            val isApp = p.key.startsWith("a:")
                            val tx = p.page * wp + ox + p.c * cell + (if (isApp) 0f else geo.g)
                            val ty = oy + p.r * cell + (if (isApp) 0f else geo.g)
                            val ax by animateFloatAsState(tx, tween(380, easing = FastOutSlowInEasing), label = "x")
                            val ay by animateFloatAsState(ty, tween(380, easing = FastOutSlowInEasing), label = "y")
                            val w = if (isApp) cell else p.w * cell - 2 * geo.g
                            val h = if (isApp) cell else p.h * cell - 2 * geo.g
                            val dragging = drag.key == p.key && !drag.fromDock
                            Box(
                                Modifier
                                    .offset { IntOffset((trackX.value + swipe + ax).roundToInt(), ay.roundToInt()) }
                                    .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                                    .then(
                                        if (dragging) Modifier.drawBehind {
                                            val r = if (isApp) cell * .2f else cell * .21f
                                            val inset = if (isApp) (cell - cell * .58f) / 2 else 0f
                                            drawRoundRect(
                                                Color.White.copy(alpha = .75f),
                                                topLeft = Offset(inset, inset - cell * .08f),
                                                size = if (isApp) Size(cell * .58f, cell * .58f) else size,
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                                                style = Stroke(2f * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f * dp, 8f * dp)))
                                            )
                                        } else Modifier
                                    )
                            ) {
                                if (!dragging) ItemBody(p.key, w, h, cell, dp, st, pal, appMap, host, labelWhite, ctx)
                            }
                        }
                    }
                }

                // busca
                if (st.search) {
                    val sw = min(680 * dp, wp - 2 * padX)
                    GlassBox(
                        Modifier
                            .offset { IntOffset(((wp - sw) / 2).roundToInt(), (topInset + 16 * dp).roundToInt()) }
                            .size(with(density) { sw.toDp() }, 56.dp)
                            .clickable(remember { MutableInteractionSource() }, null) { searchOpen = true },
                        RoundedCornerShape(28.dp), pal
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            VIcon(Icons.Filled.Search, pal.ink2, Modifier.size(24.dp))
                            Text1("Pesquisar no Google", pal.ink2, 19f, FontWeight.Normal)
                        }
                    }
                }

                // dock
                GlassBox(
                    Modifier
                        .offset { IntOffset(dockLeft.roundToInt(), dockTop.roundToInt()) }
                        .size(with(density) { dockW.toDp() }, with(density) { dockH.toDp() }),
                    RoundedCornerShape(44.dp), pal
                ) {}
                for ((i, pkg) in st.dock.withIndex()) key("d$pkg") {
                    val app = appMap[pkg]
                    if (app != null) {
                        val r = geo.iconRect(i)
                        val hidden = drag.key == "a:$pkg" && drag.fromDock
                        val tgt = drag.dockSlot == i && drag.key != "a:$pkg"
                        val sc by animateFloatAsState(if (tgt) .84f else 1f, tween(200), label = "tgt")
                        Box(
                            Modifier
                                .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                                .size(with(density) { dt.toDp() })
                                .graphicsLayer { alpha = if (hidden) 0f else 1f; scaleX = sc; scaleY = sc }
                                .clip(RoundedCornerShape(32))
                                .clickable(remember { MutableInteractionSource() }, null) { Apps.launch(ctx, pkg) }
                        ) { Image(app.icon, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                }

                // remover widget
                val dk = drag.key
                if (dk != null && !dk.startsWith("a:") && !drag.fromDock) {
                    Box(
                        Modifier
                            .offset { IntOffset(((wp - 300 * dp) / 2).roundToInt(), (topInset + 16 * dp).roundToInt()) }
                            .size(300.dp, 56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .drawBehind { drawRect(if (drag.removeHot) pal.danger else pal.sheet) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            VIcon(Icons.Filled.Close, if (drag.removeHot) Color.White else pal.ink, Modifier.size(22.dp))
                            Text1("Remover widget", if (drag.removeHot) Color.White else pal.ink, 18f, FontWeight.Medium)
                        }
                    }
                }

                // item sendo arrastado
                if (dk != null) {
                    val sz = drag.size
                    Box(
                        Modifier
                            .offset { IntOffset((drag.pos.x - drag.grab.x).roundToInt(), (drag.pos.y - drag.grab.y).roundToInt()) }
                            .size(with(density) { sz.width.toDp() }, with(density) { sz.height.toDp() })
                            .graphicsLayer { scaleX = 1.07f; scaleY = 1.07f; shadowElevation = 28f; shape = RoundedCornerShape(24.dp); clip = false }
                    ) {
                        val isApp = dk.startsWith("a:")
                        ItemBody(dk, sz.width, sz.height, if (isApp) sz.width else cell, dp, st, pal, appMap, host, st.dark || st.wp == "hills" || (st.wp == "photo" && st.photoWhite), ctx)
                    }
                }

                // anel do toque longo
                if (ringAt != null && ringP.value > 0f) {
                    val c = ringAt!!
                    Canvas(Modifier.fillMaxSize()) {
                        val r = 30f * dp
                        val col = if (st.dark || st.wp == "hills") Color.White else Color(0xFF15191C)
                        drawCircle(col.copy(alpha = .28f), r, c, style = Stroke(3.5f * dp))
                        drawArc(col, -90f, 360f * ringP.value, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(3.5f * dp, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                }

                // menu do toque longo
                menuAt?.let { m ->
                    val mw = 330 * dp
                    val mx = (m.x - mw / 2).coerceIn(24 * dp, wp - mw - 24 * dp)
                    val my = (m.y - 60 * dp - 30 * dp).coerceIn(topInset + 12 * dp, hp - botInset - 72 * dp)
                    GlassBox(
                        Modifier
                            .offset { IntOffset(mx.roundToInt(), my.roundToInt()) }
                            .size(with(density) { mw.toDp() }, 60.dp)
                            .clickable(remember { MutableInteractionSource() }, null) { menuAt = null; settingsOpen = true },
                        RoundedCornerShape(30.dp), pal, strong = true
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            VIcon(Icons.Filled.Settings, pal.ink, Modifier.size(26.dp))
                            Text1("Configurações do Launcher", pal.ink, 18f, FontWeight.Medium)
                        }
                    }
                }

                // pontos de página
                if (pages > 1) {
                    Row(
                        Modifier
                            .offset { IntOffset(((wp - (pages * 18 + 18) * dp) / 2).roundToInt(), (dockTop - 30 * dp).roundToInt()) },
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (i in 0 until pages) {
                            val on = i == pg
                            Box(
                                Modifier.size(if (on) 26.dp else 8.dp, 8.dp).clip(RoundedCornerShape(4.dp))
                                    .drawBehind { drawRect((if (st.dark || st.wp == "hills") Color.White else Color(0xFF15191C)).copy(alpha = if (on) .95f else .4f)) }
                                    .clickable(remember { MutableInteractionSource() }, null) { goTo(i) }
                            )
                        }
                    }
                }

                if (settingsOpen) SettingsSheet(st, pal, apps, host, land, wp, hp, topInset, botInset, dp, pages) { settingsOpen = false }
                if (searchOpen) SearchOverlay(st, pal, apps, host, wp, topInset, padX, dp) { searchOpen = false }
            }
        }
    }
}

@Composable
private fun ItemBody(
    key: String, w: Float, h: Float, cell: Float, dp: Float,
    st: LState, pal: Pal, appMap: Map<String, AppInfo>, host: Host, labelWhite: Boolean, ctx: android.content.Context,
) {
    val density = LocalDensity.current
    when {
        key.startsWith("a:") -> {
            val pkg = key.removePrefix("a:")
            val app = appMap[pkg] ?: return
            val tile = cell * .58f
            Column(
                Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { Apps.launch(ctx, pkg) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.size(with(density) { tile.toDp() })
                        .graphicsLayer { shadowElevation = 10f; shape = RoundedCornerShape(32); clip = false }
                        .clip(RoundedCornerShape(32))
                ) { Image(app.icon, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Box(Modifier.fillMaxWidth().padding(top = (cell * .06f / dp).dp), contentAlignment = Alignment.Center) {
                    Text1(app.label, if (labelWhite) Color.White else Color(0xFF15191C), max(12f, cell / dp * .095f), FontWeight.Medium, maxLines = 1)
                }
            }
        }
        key.startsWith("x:") -> {
            val id = key.removePrefix("x:").toInt()
            val r = (cell * .21f / dp).dp
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(r))) {
                AndroidWidgetView(id, host.widgetHost, host.widgetMgr, Modifier.fillMaxSize())
            }
        }
        else -> GlassBox(Modifier.fillMaxSize(), RoundedCornerShape((cell * .21f / dp).dp), pal) {
            when (key) {
                "w:clock" -> ClockWidget(pal)
                "w:weather" -> WeatherWidget(pal)
                "w:music" -> MusicWidget(pal)
                "w:notes" -> NotesWidget(pal, st)
            }
        }
    }
}
