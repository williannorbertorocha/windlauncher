package app.launchertab

import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

@Composable
private fun Modifier.tap(onClick: () -> Unit): Modifier =
    this.clickable(remember { MutableInteractionSource() }, null, onClick = onClick)

@Composable
private fun SRow(title: String, sub: String?, pal: Pal, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).drawBehind { drawRect(pal.row) }
            .tap(onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text1(title, pal.ink, 17f, FontWeight.Medium)
            if (sub != null) Text1(sub, pal.ink3, 14f, FontWeight.Normal, maxLines = 1)
        }
        Text1("›", pal.ink3, 22f)
    }
}

@Composable
private fun Pill(text: String, on: Boolean, pal: Pal, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).drawBehind { drawRect(if (on) pal.acc else pal.row) }
            .tap(onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text1(text, if (on) Color.White else pal.ink, 15f, FontWeight.Medium) }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, pal: Pal, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).drawBehind { drawRect(pal.row) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text1(label, pal.ink, 16f, FontWeight.Medium, modifier = Modifier.weight(1f))
        Box(Modifier.size(40.dp).clip(CircleShape).drawBehind { drawRect(pal.tile) }.tap { if (value > min) onChange(value - 1) }, contentAlignment = Alignment.Center) {
            Text1("−", if (value > min) pal.ink else pal.ink3, 22f)
        }
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) { Text1("$value", pal.ink, 18f, FontWeight.Medium) }
        Box(Modifier.size(40.dp).clip(CircleShape).drawBehind { drawRect(pal.tile) }.tap { if (value < max) onChange(value + 1) }, contentAlignment = Alignment.Center) {
            Text1("+", if (value < max) pal.ink else pal.ink3, 22f)
        }
    }
}

@Composable
private fun ProviderRow(info: AppWidgetProviderInfo, pal: Pal, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val label = remember(info) { runCatching { info.loadLabel(pm) }.getOrDefault("Widget") }
    val appName = remember(info) { runCatching { pm.getApplicationLabel(info.providerInfo.applicationInfo).toString() }.getOrDefault("") }
    val bmp = remember(info) {
        runCatching { (info.loadPreviewImage(ctx, 0) ?: info.loadIcon(ctx, 0))?.toBitmap(256, 160)?.asImageBitmap() }.getOrNull()
    }
    val w = ((info.minWidth + 30) / 70).coerceIn(1, 6)
    val h = ((info.minHeight + 30) / 70).coerceIn(1, 6)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).drawBehind { drawRect(pal.row) }
            .tap(onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(96.dp, 60.dp).clip(RoundedCornerShape(12.dp)).drawBehind { drawRect(pal.tile) }, contentAlignment = Alignment.Center) {
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Column(Modifier.weight(1f)) {
            Text1(label, pal.ink, 16f, FontWeight.Medium, maxLines = 1)
            Text1("$appName · $w×$h", pal.ink3, 13f, FontWeight.Normal, maxLines = 1)
        }
    }
}

@Composable
fun SettingsSheet(
    st: LState, pal: Pal, apps: List<AppInfo>, host: Host, land: Boolean,
    wp: Float, hp: Float, topInset: Float, botInset: Float, dp: Float, pages: Int,
    onClose: () -> Unit,
) {
    var view by remember { mutableStateOf("main") }
    val pm = LocalContext.current.packageManager
    BackHandler(enabled = view != "main") { view = "main" }

    Box(
        Modifier.fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = if (st.dark) .45f else .22f)) }
            .pointerInput(Unit) { detectTapGestures { onClose() } }
    ) {
        val panelMod = if (land) {
            Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
                .size(460.dp, ((hp - topInset - botInset) / dp - 32f).dp)
        } else {
            Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = (botInset / dp + 16f).dp)
                .fillMaxWidth().height((hp * 0.72f / dp).dp)
        }
        GlassBox(panelMod.pointerInput(Unit) { detectTapGestures { } }, RoundedCornerShape(32.dp), pal, strong = true) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                val title = when (view) {
                    "wall" -> "Wallpaper"; "grid" -> "Grade de apps"; "dock" -> "Dock"
                    "widgets" -> "Adicionar widget"; else -> "Configurações do Launcher"
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (view != "main") {
                        Box(Modifier.size(40.dp).clip(CircleShape).tap { view = "main" }, contentAlignment = Alignment.Center) {
                            Text1("‹", pal.ink, 28f)
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text1(title, pal.ink, 21f, FontWeight.Bold, modifier = Modifier.weight(1f))
                    Box(Modifier.size(40.dp).clip(CircleShape).drawBehind { drawRect(pal.row) }.tap(onClose), contentAlignment = Alignment.Center) {
                        VIcon(Icons.Filled.Close, pal.ink, Modifier.size(20.dp))
                    }
                }

                when (view) {
                    "wall" -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val presets = listOf(
                            Triple("hills", "Colinas", listOf(Color(0xFF9DBFE0), Color(0xFF86B062))),
                            Triple("mist", "Névoa", listOf(Color(0xFFDCDFE1), Color(0xFFFF963C))),
                            Triple("aurora", "Aurora", listOf(Color(0xFFF6E0D0), Color(0xFFBEAAFA))),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for ((id, name, cs) in presets) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(18.dp))
                                            .drawBehind { drawRect(Brush.verticalGradient(cs)) }
                                            .tap { st.wp = id; st.save() },
                                        contentAlignment = Alignment.Center,
                                    ) { if (st.wp == id) Text1("✓", Color.White, 26f, FontWeight.Bold) }
                                    Spacer(Modifier.height(6.dp))
                                    Text1(name, pal.ink2, 14f)
                                }
                            }
                        }
                        SRow("Escolher das fotos do tablet", "Abre o app de fotos", pal) { host.pickPhoto(); onClose() }
                    }

                    "grid" -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text1("Tablet na horizontal", pal.ink3, 14f, FontWeight.Medium)
                        Stepper("Colunas", st.colsL, 5, 10, pal) { st.colsL = it; st.save() }
                        Stepper("Linhas", st.rowsL, 3, 6, pal) { st.rowsL = it; st.save() }
                        Spacer(Modifier.height(6.dp))
                        Text1("Tablet na vertical", pal.ink3, 14f, FontWeight.Medium)
                        Stepper("Colunas", st.colsP, 3, 8, pal) { st.colsP = it; st.save() }
                        Stepper("Linhas", st.rowsP, 4, 10, pal) { st.rowsP = it; st.save() }
                    }

                    "dock" -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stepper("Apps na dock", st.dockCount, 3, 8, pal) { st.dockCount = it; st.save() }
                        Text1("Arraste ícones da tela para a dock (e vice-versa) para trocar.", pal.ink3, 14f)
                    }

                    "widgets" -> {
                        val names = listOf("w:clock" to "Relógio", "w:weather" to "Clima", "w:music" to "Música", "w:notes" to "Notas")
                        val providers = remember {
                            host.widgetMgr.installedProviders.sortedBy {
                                runCatching { it.loadLabel(pm) }.getOrDefault("")
                            }
                        }
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            item { Text1("Widgets do Launcher", pal.ink3, 14f, FontWeight.Medium) }
                            items(names) { (k, n) ->
                                val has = k in st.widgets
                                SRow(n, if (has) "Na tela inicial — toque para remover" else "Toque para adicionar", pal) {
                                    if (has) { st.widgets.remove(k); st.order.remove(k) } else st.widgets.add(k)
                                    st.save()
                                }
                            }
                            val real = st.widgets.filter { it.startsWith("x:") }
                            if (real.isNotEmpty()) {
                                item { Spacer(Modifier.height(4.dp)); Text1("Widgets do Android na tela", pal.ink3, 14f, FontWeight.Medium) }
                                items(real) { k ->
                                    val id = k.removePrefix("x:").toIntOrNull() ?: -1
                                    val lbl = runCatching { host.widgetMgr.getAppWidgetInfo(id).loadLabel(pm) }.getOrDefault("Widget")
                                    SRow(lbl, "Toque para remover", pal) {
                                        runCatching { host.widgetHost.deleteAppWidgetId(id) }
                                        st.widgets.remove(k); st.order.remove(k); st.spans.remove(k); st.save()
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(4.dp)); Text1("Widgets instalados no tablet", pal.ink3, 14f, FontWeight.Medium) }
                            items(providers) { info ->
                                ProviderRow(info, pal) { host.addWidget(info); onClose() }
                            }
                        }
                    }

                    else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SRow("Wallpaper", when (st.wp) { "photo" -> "Foto do tablet"; "mist" -> "Névoa"; "aurora" -> "Aurora"; else -> "Colinas" }, pal) { view = "wall" }
                        SRow("Grade de apps", "${st.colsL} × ${st.rowsL} horizontal · ${st.colsP} × ${st.rowsP} vertical", pal) { view = "grid" }
                        SRow("Dock", "${st.dockCount} apps", pal) { view = "dock" }
                        SRow("Adicionar widget", "Todos os widgets do tablet", pal) { view = "widgets" }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).drawBehind { drawRect(pal.row) }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text1("Barra de pesquisa", pal.ink, 17f, FontWeight.Medium, modifier = Modifier.weight(1f))
                            Pill("Mostrar", st.search, pal) { st.search = true; st.save() }
                            Spacer(Modifier.width(8.dp))
                            Pill("Ocultar", !st.search, pal) { st.search = false; st.save() }
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).drawBehind { drawRect(pal.row) }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text1("Tema", pal.ink, 17f, FontWeight.Medium, modifier = Modifier.weight(1f))
                            Pill("Claro", !st.dark, pal) { st.dark = false; st.save() }
                            Spacer(Modifier.width(8.dp))
                            Pill("Escuro", st.dark, pal) { st.dark = true; st.save() }
                        }
                    }
                }
            }
        }
    }
}
