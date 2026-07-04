package com.soundwave.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest

data class AlbumTheme(val primary: Color, val dark: Color)

private val cache = HashMap<String, AlbumTheme?>()

/**
 * Extracts a dominant/darker color pair from album art, same purpose as the
 * web app's canvas-based extractColors(), but using Android's Palette API
 * which is more robust than manual pixel averaging.
 */
suspend fun extractAlbumTheme(context: android.content.Context, imageUrl: String?): AlbumTheme? {
    if (imageUrl == null) return null
    if (cache.containsKey(imageUrl)) return cache[imageUrl]

    return try {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context).data(imageUrl).allowHardware(false).build()
        val result = loader.execute(request)
        val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        if (bitmap == null) {
            cache[imageUrl] = null
            return null
        }

        val palette = Palette.from(bitmap).generate()
        val primarySwatch = palette.dominantSwatch ?: palette.swatches.firstOrNull()
        val darkSwatch = palette.darkMutedSwatch ?: palette.darkVibrantSwatch

        val primary = primarySwatch?.rgb?.let(::Color) ?: Color(0xFF8B5CF6)
        val dark = darkSwatch?.rgb?.let(::Color) ?: Color(
            (primary.red * 0.4f).coerceIn(0f, 1f),
            (primary.green * 0.4f).coerceIn(0f, 1f),
            (primary.blue * 0.4f).coerceIn(0f, 1f)
        )
        val theme = AlbumTheme(primary, dark)
        cache[imageUrl] = theme
        theme
    } catch (e: Exception) {
        cache[imageUrl] = null
        null
    }
}
