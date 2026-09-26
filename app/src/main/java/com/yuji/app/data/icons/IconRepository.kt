package com.yuji.app.data.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.svg.SvgDecoder
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Account icons are always stored as small square PNGs under files/icons, whatever their source
 * (gallery, Iconify SVG, legacy cache). Rendering is then a plain bitmap load — no WebView.
 */
class IconRepository(
    private val context: Context,
    private val http: OkHttpClient,
) {
    val dir: File get() = File(context.filesDir, "icons").apply { mkdirs() }

    private val svgLoader by lazy {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }

    /** Iconify search, returns ids such as "logos:alipay". Retries up to 3 times on transient errors. */
    suspend fun search(query: String): List<String> = withContext(Dispatchers.IO) {
        val url = "https://api.iconify.design/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("limit", "60")
            .build()
        var lastError: IOException? = null
        repeat(3) { attempt ->
            try {
                http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (resp.code == 429) throw IOException("搜索请求过于频繁，请稍后再试")
                    if (!resp.isSuccessful) throw IOException("搜索失败（${resp.code}）")
                    val body = resp.body?.string() ?: throw IOException("搜索失败")
                    val json = Json.parseToJsonElement(body).jsonObject
                    return@withContext json["icons"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
                }
            } catch (e: IOException) {
                if (e.message?.contains("频繁") == true) throw e
                lastError = e
                if (attempt < 2) delay(1_500L * (attempt + 1))
            }
        }
        throw lastError ?: IOException("搜索失败")
    }

    /**
     * One icon as SVG from the Iconify API: api.iconify.design/{prefix}/{name}.svg.
     * (The @iconify-json npm packages ship a single icons.json, not per-icon SVG files.)
     * Monochrome icons are rendered white to sit on the dark icon plate; multicolor logos
     * ignore the color and keep their own.
     */
    fun previewUrl(id: String): String {
        val (prefix, name) = id.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return "https://api.iconify.design".toHttpUrl().newBuilder()
            .addPathSegment(prefix)
            .addPathSegment("$name.svg")
            .addQueryParameter("color", "#ffffff")
            .build()
            .toString()
    }

    suspend fun saveFromIconify(id: String): File = withContext(Dispatchers.IO) {
        val bytes = download(previewUrl(id))
        if (bytes.size < 64) throw IOException("图标数据无效，请换一个试试")
        save(bytes, "iconify")
    }

    suspend fun saveFromUri(uri: Uri): File = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("无法读取图片")
        save(bytes, "album")
    }

    /** Normalize arbitrary image bytes (PNG/JPEG/WebP/SVG) into a 256px PNG and store it. */
    suspend fun save(bytes: ByteArray, prefix: String): File {
        val bitmap = decode(bytes) ?: throw IOException("不是有效的图片")
        val square = centerSquare(bitmap, SIZE)
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}_${(1000..9999).random()}.png")
        file.outputStream().use { square.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    fun pngBytes(file: File): ByteArray? = runCatching { file.readBytes() }.getOrNull()

    /** Files in the icon dir that no account references. */
    fun unused(referenced: Set<String>): List<File> =
        dir.listFiles().orEmpty().filter { it.isFile && it.absolutePath !in referenced }

    fun totalSize(): Long = dir.listFiles().orEmpty().sumOf { it.length() }

    private suspend fun decode(bytes: ByteArray): Bitmap? {
        if (isSvg(bytes)) {
            val request = ImageRequest.Builder(context)
                .data(bytes)
                .size(SIZE)
                .allowHardware(false)
                .build()
            val result = svgLoader.execute(request)
            return (result as? SuccessResult)?.image?.toBitmap()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun centerSquare(src: Bitmap, size: Int): Bitmap {
        val side = minOf(src.width, src.height)
        val left = (src.width - side) / 2
        val top = (src.height - side) / 2
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, Rect(left, top, left + side, top + side), Rect(0, 0, size, size), null)
        return out
    }

    private fun download(url: String): ByteArray {
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("图标下载失败（${resp.code}）")
            return resp.body?.bytes() ?: throw IOException("图标下载失败")
        }
    }

    companion object {
        const val SIZE = 256

        fun isSvg(bytes: ByteArray): Boolean {
            val head = String(bytes, 0, minOf(bytes.size, 512), Charsets.UTF_8).lowercase()
            return head.contains("<svg")
        }

        fun encodePng(bitmap: Bitmap): ByteArray =
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}
