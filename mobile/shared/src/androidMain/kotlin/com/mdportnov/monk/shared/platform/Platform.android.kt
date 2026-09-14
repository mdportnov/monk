package com.mdportnov.monk.shared.platform

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.size
import androidx.core.graphics.drawable.toBitmap
import com.mdportnov.monk.shared.data.KeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class SharedPrefsStore(context: Context, name: String = "monk") : KeyValueStore {
    private val prefs: SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun remove(key: String) { prefs.edit().remove(key).apply() }
}

actual fun systemLanguage(): String = Locale.getDefault().language

private val iconCache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

@Composable
actual fun AppIcon(packageName: String, size: Dp, modifier: Modifier) {
    val context = LocalContext.current
    val px = (size.value * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
    val key = "$packageName@$px"
    // produceState keeps the previous value when its key changes, so a row reused for another
    // package would keep showing the old icon; reset explicitly before loading.
    val bitmap by produceState<ImageBitmap?>(iconCache[key], key) {
        value = iconCache[key]
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val drawable: Drawable = context.packageManager.getApplicationIcon(packageName)
                    drawable.toBitmap(px, px).asImageBitmap()
                }.getOrNull()?.also { iconCache[key] = it }
            }
        }
    }
    val m = modifier.size(size).clip(MaterialTheme.shapes.small)
    // The icon arrives from IO a frame or two late: cross-fade it in over the placeholder.
    Crossfade(targetState = bitmap, animationSpec = tween(200), label = "icon") { bmp ->
        if (bmp != null) {
            Image(bmp, contentDescription = null, modifier = m, contentScale = ContentScale.Fit)
        } else {
            Icon(Icons.Outlined.Android, null, modifier = m, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
