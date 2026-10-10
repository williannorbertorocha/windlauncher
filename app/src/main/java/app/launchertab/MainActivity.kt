package app.launchertab

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.max

class MainActivity : ComponentActivity() {
    private lateinit var st: LState
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetMgr: AppWidgetManager
    private val apps = mutableStateOf<List<AppInfo>>(emptyList())
    private var pendingId = -1

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { reloadApps() }
    }

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadPhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        st = LState(this)
        widgetMgr = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(this, 1024)

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, pkgReceiver, f, ContextCompat.RECEIVER_EXPORTED)

        reloadApps()
        val saved = File(filesDir, "wallpaper.jpg")
        if (st.wp == "photo" && saved.exists()) {
            thread {
                val b = BitmapFactory.decodeFile(saved.path)
                if (b != null) runOnUiThread { applyPhoto(b, false) }
            }
        }

        val host = Host(widgetHost, widgetMgr, { picker.launch("image/*") }, { addWidget(it) }, { openUrl(it) })
        setContent {
            val h = remember { host }
            HomeScreen(st, apps.value, h)
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { widgetHost.startListening() }
    }

    override fun onStop() {
        runCatching { widgetHost.stopListening() }
        super.onStop()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pkgReceiver) }
        super.onDestroy()
    }

    private fun reloadApps() {
        thread {
            val l = runCatching { Apps.load(this) }.getOrNull() ?: return@thread
            runOnUiThread { apps.value = l }
        }
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ---------- papel de parede a partir das fotos ----------
    private fun loadPhoto(uri: Uri) {
        thread {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > 2560) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                if (bmp != null) {
                    File(filesDir, "wallpaper.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    runOnUiThread { applyPhoto(bmp, true) }
                }
            }
        }
    }

    private fun applyPhoto(bmp: Bitmap, select: Boolean) {
        val small = Bitmap.createScaledBitmap(bmp, 16, 16, true)
        var sum = 0.0
        for (y in 0 until 16) for (x in 0 until 16) {
            val c = small.getPixel(x, y)
            val r = (c shr 16 and 255) / 255.0
            val g = (c shr 8 and 255) / 255.0
            val b = (c and 255) / 255.0
            sum += 0.2126 * r + 0.7152 * g + 0.0722 * b
        }
        st.photoWhite = sum / 256.0 < 0.6
        st.photo = bmp.asImageBitmap()
        if (select) { st.wp = "photo"; st.save() }
    }

    // ---------- widgets reais do Android ----------
    private fun addWidget(info: AppWidgetProviderInfo) {
        val id = widgetHost.allocateAppWidgetId()
        pendingId = id
        if (widgetMgr.bindAppWidgetIdIfAllowed(id, info.provider)) {
            afterBind(id)
        } else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_BIND)
        }
    }

    private fun afterBind(id: Int) {
        val info = widgetMgr.getAppWidgetInfo(id)
        if (info?.configure != null) {
            try {
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null)
            } catch (e: Exception) { finishAdd(id) }
        } else finishAdd(id)
    }

    private fun finishAdd(id: Int) {
        val info = widgetMgr.getAppWidgetInfo(id)
        if (info == null) { widgetHost.deleteAppWidgetId(id); return }
        val key = "x:$id"
        val w = ((info.minWidth + 30) / 70).coerceIn(1, 6)
        val h = ((info.minHeight + 30) / 70).coerceIn(1, 6)
        st.spans[key] = w to h
        st.widgets.add(key)
        st.save()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val id = pendingId
        if (id < 0) return
        when (requestCode) {
            REQ_BIND -> if (resultCode == RESULT_OK) afterBind(id) else widgetHost.deleteAppWidgetId(id)
            REQ_CONFIG -> if (resultCode == RESULT_OK) finishAdd(id) else widgetHost.deleteAppWidgetId(id)
        }
    }

    companion object {
        private const val REQ_BIND = 101
        private const val REQ_CONFIG = 102
    }
}
