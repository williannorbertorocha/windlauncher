package app.launchertab

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject

data class AppInfo(val pkg: String, val label: String, val icon: ImageBitmap)
data class NoteItem(val text: String, val done: Boolean)
data class Placed(val key: String, val page: Int, val c: Int, val r: Int, val w: Int, val h: Int)

/** Cores do launcher nos temas claro e escuro. */
class Pal(val dark: Boolean) {
    val ink = if (dark) Color(0xFFF2F4F5) else Color(0xFF15191C)
    val ink2 = if (dark) Color(0xFFB6BEC3) else Color(0xFF566067)
    val ink3 = if (dark) Color(0xFF848E94) else Color(0xFF85909A)
    val inv = if (dark) Color(0xFF111417) else Color.White
    val tint = if (dark) Color(0x6B1C2024) else Color(0x66FFFFFF)
    val edge = if (dark) Color(0x24FFFFFF) else Color(0xA0FFFFFF)
    val tile = if (dark) Color(0xBD2E3338) else Color(0xCCFFFFFF)
    val row = if (dark) Color(0x12FFFFFF) else Color(0xB8FFFFFF)
    val sheet = if (dark) Color(0xD71A1D21) else Color(0xCCFAFBFC)
    val acc = if (dark) Color(0xFF7EA3FF) else Color(0xFF2F63E6)
    val danger = Color(0xFFE5484D)
}

/** Estado salvo do launcher. */
class LState(private val ctx: Context) {
    private val p = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var dark by mutableStateOf(p.getBoolean("dark", false))
    var wp by mutableStateOf(p.getString("wp", "hills") ?: "hills")
    var photo by mutableStateOf<ImageBitmap?>(null)
    var photoWhite by mutableStateOf(true)
    var colsL by mutableIntStateOf(p.getInt("colsL", 8))
    var rowsL by mutableIntStateOf(p.getInt("rowsL", 4))
    var colsP by mutableIntStateOf(p.getInt("colsP", 6))
    var rowsP by mutableIntStateOf(p.getInt("rowsP", 8))
    var dockCount by mutableIntStateOf(p.getInt("dockCount", 5))
    var search by mutableStateOf(p.getBoolean("search", true))

    val widgets = mutableStateListOf<String>()
    val order = mutableStateListOf<String>()
    val dock = mutableStateListOf<String>()
    val notes = mutableStateListOf<NoteItem>()
    val spans = HashMap<String, Pair<Int, Int>>()

    init {
        widgets.addAll(readList("widgets") ?: listOf("w:clock", "w:weather", "w:music", "w:notes"))
        order.addAll(readList("order") ?: emptyList())
        dock.addAll(readList("dock") ?: emptyList())
        val ns = p.getString("notes", null)
        if (ns != null) {
            runCatching {
                val a = JSONArray(ns)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    notes.add(NoteItem(o.getString("t"), o.getBoolean("d")))
                }
            }
        } else {
            notes.addAll(listOf(
                NoteItem("Revisar o layout da tela inicial", false),
                NoteItem("Ligar para a gráfica", false),
                NoteItem("Ideias para o fim de semana", true),
            ))
        }
        p.getString("spans", null)?.let { s ->
            runCatching {
                val o = JSONObject(s)
                o.keys().forEach { k -> val a = o.getJSONArray(k); spans[k] = a.getInt(0) to a.getInt(1) }
            }
        }
    }

    private fun readList(name: String): List<String>? {
        val s = p.getString(name, null) ?: return null
        return runCatching {
            val a = JSONArray(s)
            (0 until a.length()).map { a.getString(it) }
        }.getOrNull()
    }

    fun save() {
        val na = JSONArray()
        notes.forEach { na.put(JSONObject().put("t", it.text).put("d", it.done)) }
        val so = JSONObject()
        spans.forEach { (k, v) -> so.put(k, JSONArray().put(v.first).put(v.second)) }
        p.edit()
            .putBoolean("dark", dark)
            .putString("wp", wp)
            .putInt("colsL", colsL).putInt("rowsL", rowsL)
            .putInt("colsP", colsP).putInt("rowsP", rowsP)
            .putInt("dockCount", dockCount)
            .putBoolean("search", search)
            .putString("widgets", JSONArray(widgets.toList()).toString())
            .putString("order", JSONArray(order.toList()).toString())
            .putString("dock", JSONArray(dock.toList()).toString())
            .putString("notes", na.toString())
            .putString("spans", so.toString())
            .apply()
    }

    fun spanOf(key: String): Pair<Int, Int> = when {
        key.startsWith("a:") -> 1 to 1
        key.startsWith("x:") -> spans[key] ?: (2 to 2)
        else -> 2 to 2
    }

    /** Garante que a dock só tenha apps instalados e o número certo de itens. */
    fun fixDock(apps: List<AppInfo>) {
        if (apps.isEmpty()) return
        val valid = apps.map { it.pkg }.toSet()
        val d = dock.map { it.removePrefix("a:") }.filter { it in valid }.distinct().toMutableList()
        if (d.size < dockCount) {
            val prefer = listOf("dialer", "phone", "messag", "mms", "chrome", "browser", "camera", "gallery", "photos", "contacts", "settings")
            val pool = prefer.flatMap { k -> apps.filter { it.pkg.contains(k, true) }.map { it.pkg } } + apps.map { it.pkg }
            for (pk in pool) {
                if (d.size >= dockCount) break
                if (pk !in d) d.add(pk)
            }
        }
        val nd = d.take(dockCount)
        if (nd != dock.toList()) { dock.clear(); dock.addAll(nd) }
    }

    /** Mantém a ordem da grade coerente com apps, widgets e dock. */
    fun fixOrder(apps: List<AppInfo>) {
        if (apps.isEmpty()) return
        val valid = apps.map { it.pkg }.toSet()
        val have = order.filter { k ->
            when {
                k.startsWith("w:") || k.startsWith("x:") -> k in widgets
                k.startsWith("a:") -> k.removePrefix("a:") in valid && k.removePrefix("a:") !in dock
                else -> false
            }
        }.distinct().toMutableList()
        widgets.forEach { w ->
            if (w !in have) {
                val fa = have.indexOfFirst { it.startsWith("a:") }
                if (fa < 0) have.add(w) else have.add(fa, w)
            }
        }
        apps.forEach { a -> if (a.pkg !in dock && "a:${a.pkg}" !in have) have.add("a:${a.pkg}") }
        if (have != order.toList()) { order.clear(); order.addAll(have) }
    }
}

/** Carrega os apps instalados que aparecem no menu de apps. */
object Apps {
    fun load(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { ri ->
                AppInfo(
                    ri.activityInfo.packageName,
                    ri.loadLabel(pm).toString(),
                    ri.loadIcon(pm).toBitmap(160, 160).asImageBitmap()
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(ctx: Context, pkg: String) {
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(it)
        }
    }
}

/** Distribui os itens na grade, página por página, na ordem recebida. */
fun pack(
    keys: List<String>,
    cols: Int,
    rows: Int,
    span: (String) -> Pair<Int, Int>,
    exists: (String) -> Boolean,
): List<Placed> {
    val pages = ArrayList<Array<BooleanArray>>()
    fun newPage() { pages.add(Array(rows) { BooleanArray(cols) }) }
    newPage()
    val out = ArrayList<Placed>()
    for (k in keys) {
        if (!exists(k)) continue
        val (w0, h0) = span(k)
        val w = minOf(w0, cols)
        val h = minOf(h0, rows)
        var placed = false
        var p = 0
        while (!placed && p < 60) {
            if (p >= pages.size) newPage()
            loop@ for (r in 0..rows - h) for (c in 0..cols - w) {
                var ok = true
                for (y in r until r + h) for (x in c until c + w) if (pages[p][y][x]) ok = false
                if (ok) {
                    for (y in r until r + h) for (x in c until c + w) pages[p][y][x] = true
                    out.add(Placed(k, p, c, r, w, h))
                    placed = true
                    break@loop
                }
            }
            if (!placed) p++
        }
    }
    return out
}
