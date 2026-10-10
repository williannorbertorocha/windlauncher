package app.launchertab

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URLEncoder
import java.text.Normalizer
import kotlin.math.min

private fun norm(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

@Composable
fun SearchOverlay(
    st: LState, pal: Pal, apps: List<AppInfo>, host: Host,
    wp: Float, topInset: Float, padX: Float, dp: Float,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val matches = remember(q, apps) {
        val n = norm(q.trim())
        if (n.isEmpty()) emptyList() else apps.filter { norm(it.label).contains(n) }.take(6)
    }
    fun go(url: String) { host.openUrl(url); onClose() }
    fun enc() = URLEncoder.encode(q.trim(), "UTF-8")
    val sw = min(680f * dp, wp - 2 * padX) / dp

    Box(
        Modifier.fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = if (st.dark) .5f else .28f)) }
            .pointerInput(Unit) { detectTapGestures { onClose() } }
    ) {
        Column(
            Modifier.align(Alignment.TopCenter)
                .padding(top = (topInset / dp + 16f).dp)
                .width(sw.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassBox(Modifier.fillMaxWidth().size(width = sw.dp, height = 56.dp), RoundedCornerShape(28.dp), pal, strong = true) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    VIcon(Icons.Filled.Search, pal.ink2, Modifier.size(24.dp))
                    BasicTextField(
                        value = q,
                        onValueChange = { q = it },
                        singleLine = true,
                        textStyle = TextStyle(color = pal.ink, fontSize = 19.sp),
                        cursorBrush = SolidColor(pal.ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (q.isNotBlank()) go("https://www.google.com/search?q=" + enc())
                        }),
                        decorationBox = { inner ->
                            if (q.isEmpty()) Text1("Pesquisar no Google", pal.ink3, 19f)
                            inner()
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
            if (q.isNotBlank() || matches.isNotEmpty()) {
                GlassBox(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp), pal, strong = true) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (a in matches) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                    .clickable(remember { MutableInteractionSource() }, null) { Apps.launch(ctx, a.pkg); onClose() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Image(a.icon, a.label, Modifier.size(40.dp).clip(RoundedCornerShape(32)), contentScale = ContentScale.Crop)
                                Text1(a.label, pal.ink, 17f, FontWeight.Medium, maxLines = 1)
                            }
                        }
                        val links = listOf(
                            "Pesquisar “${q.trim()}” no Google" to "https://www.google.com/search?q=",
                            "Ver imagens no Google" to "https://www.google.com/search?tbm=isch&q=",
                            "Buscar no Google Maps" to "https://www.google.com/maps/search/",
                        )
                        if (q.isNotBlank()) for ((label, base) in links) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                    .clickable(remember { MutableInteractionSource() }, null) { go(base + enc()) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                VIcon(Icons.Filled.Search, pal.acc, Modifier.size(22.dp))
                                Text1(label, pal.ink, 16f, FontWeight.Normal, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
