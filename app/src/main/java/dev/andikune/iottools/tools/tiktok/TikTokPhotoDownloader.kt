package dev.andikune.iottools.tools.tiktok

import android.content.Context
import android.widget.Toast
import dev.andikune.iottools.tools.common.MediaStoreSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class TikTokPhotoPost(
    val id: String,
    val title: String,
    val author: String,
    val username: String,
    val images: List<String>
)

object TikTokPhotoDownloader {
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS).followRedirects(true).build()
    }

    private fun itemFrom(root: JSONObject): JSONObject? {
        val scope = root.optJSONObject("__DEFAULT_SCOPE__")
        return scope?.optJSONObject("webapp")?.optJSONObject("video-detail")
            ?.optJSONObject("itemInfo")?.optJSONObject("itemStruct")
            ?: root.optJSONObject("itemInfo")?.optJSONObject("itemStruct")
            ?: root.optJSONObject("itemStruct")
            ?: root.optJSONObject("item")
    }

    suspend fun resolve(rawUrl: String): Result<TikTokPhotoPost> = withContext(Dispatchers.IO) {
        runCatching {
            val url = TikTokDownloaderService.normalizeTikTokUrl(rawUrl)
                ?: throw IllegalArgumentException("Link TikTok tidak valid.")
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36",
                "Referer" to "https://www.tiktok.com/",
                "Accept" to "text/html,application/json"
            )
            val page = client.newCall(Request.Builder().url(url).apply {
                headers.forEach { (key, value) -> header(key, value) }
            }.get().build()).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("TikTok menolak halaman (HTTP ${response.code}).")
                response.body?.string().orEmpty()
            }
            val htmlJson = Regex("""(?s)<script[^>]+id=["']__UNIVERSAL_DATA_FOR_REHYDRATION__["'][^>]*>(.*?)</script>""")
                .find(page)?.groupValues?.getOrNull(1)
            var item = htmlJson?.let { runCatching { itemFrom(JSONObject(it)) }.getOrNull() }
            if (item == null) {
                val id = Regex("""/(?:photo|video)/(\d+)""").find(url)?.groupValues?.getOrNull(1)
                    ?: throw IllegalStateException("ID post foto tidak ditemukan.")
                val api = client.newCall(Request.Builder()
                    .url("https://www.tiktok.com/api/item/detail/?itemId=$id")
                    .apply { headers.forEach { (key, value) -> header(key, value) } }
                    .get().build()).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("API foto gagal (HTTP ${response.code}).")
                    response.body?.string().orEmpty()
                }
                item = itemFrom(JSONObject(api))
            }
            val images = item?.optJSONObject("imagePost")?.optJSONArray("images")
                ?: throw IllegalStateException("Post ini tidak berisi foto atau metadata fotonya tidak bisa dibaca.")
            val urls = mutableListOf<String>()
            for (i in 0 until images.length()) {
                val list = images.optJSONObject(i)?.optJSONObject("imageURL")?.optJSONArray("urlList") ?: continue
                for (j in 0 until list.length()) {
                    val imageUrl = list.optString(j)
                    if (TikTokDownloaderService.isAllowedMediaUrl(imageUrl) && imageUrl !in urls) urls += imageUrl
                }
            }
            if (urls.isEmpty()) throw IllegalStateException("Tidak ditemukan foto dari CDN TikTok.")
            val author = item?.optJSONObject("authorInfo") ?: item?.optJSONObject("author")
            TikTokPhotoPost(
                id = item?.optString("id").orEmpty().ifBlank { "photo_${System.currentTimeMillis()}" },
                title = item?.optString("desc").orEmpty().ifBlank { "TikTok Photo Post" },
                author = author?.optString("nickname").orEmpty().ifBlank { "TikTok Creator" },
                username = author?.optString("uniqueId").orEmpty().ifBlank {
                    author?.optString("unique_id").orEmpty()
                }.ifBlank { "creator" },
                images = urls
            )
        }
    }

    fun downloadOne(context: Context, post: TikTokPhotoPost, index: Int) {
        val url = post.images.getOrNull(index) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching { saveImage(context, url, "TikTok_${post.id}_${index + 1}") }
            withContext(Dispatchers.Main) {
                Toast.makeText(context,
                    if (result.isSuccess) "Foto ${index + 1} tersimpan di Galeri."
                    else "Gagal mengunduh foto: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    fun downloadAll(context: Context, post: TikTokPhotoPost) {
        CoroutineScope(Dispatchers.IO).launch {
            var saved = 0
            post.images.forEachIndexed { index, url ->
                if (runCatching { saveImage(context, url, "TikTok_${post.id}_${index + 1}") }.isSuccess) saved++
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "$saved dari ${post.images.size} foto berhasil disimpan ke Galeri.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveImage(context: Context, url: String, baseName: String) {
        require(TikTokDownloaderService.isAllowedMediaUrl(url)) { "Domain foto tidak diizinkan." }
        client.newCall(Request.Builder().url(url).header(
            "User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"
        ).get().build()).execute().use { response ->
            check(response.isSuccessful) { "CDN foto mengembalikan HTTP ${response.code}." }
            val body = response.body ?: error("Data foto kosong.")
            val mime = body.contentType()?.toString()?.substringBefore(';')?.lowercase()
                ?.takeIf { it in setOf("image/jpeg", "image/png", "image/webp") } ?: "image/jpeg"
            val ext = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
            val pending = MediaStoreSaver.beginImageOutput(context, "$baseName.$ext", mime)
                ?: error("Tidak bisa membuat file gambar di Galeri.")
            try {
                val bytes = body.byteStream().use { it.copyTo(pending.output, 64 * 1024) }
                check(bytes > 0L) { "File foto kosong." }
                pending.commit()
            } catch (error: Exception) {
                pending.abort()
                throw error
            }
        }
    }
}
