package dev.andikune.iottools.tools.instagram

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dev.andikune.iottools.tools.common.MediaStoreSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URI

enum class InstagramMediaType {
    VIDEO,
    PHOTO,
    CAROUSEL
}

data class InstagramMediaItem(
    val url: String,
    val isVideo: Boolean,
    val previewUrl: String?,
    val index: Int,
    val isCarousel: Boolean = false
)

data class InstagramPostData(
    val shortcode: String,
    val caption: String,
    val ownerUsername: String,
    val ownerAvatar: String?,
    val mediaType: InstagramMediaType,
    val mediaList: List<InstagramMediaItem>,
    val likeCount: Int,
    val commentCount: Int,
    val sourceUrl: String,
    val isCarousel: Boolean
)

object InstagramDownloaderService {

    private val mediaCdnSuffixes = setOf(".cdninstagram.com", ".fbcdn.net")

    fun extractShortcode(rawUrl: String): String? {
        val normalized = normalizeInstagramUrl(rawUrl) ?: return null
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val typeIndex = segments.indexOfFirst { it in setOf("p", "reel", "reels", "tv") }
        if (typeIndex < 0 || typeIndex + 1 >= segments.size) return null
        return segments[typeIndex + 1].takeIf { it.matches(Regex("^[A-Za-z0-9_-]+$")) }
    }

    fun normalizeInstagramUrl(rawUrl: String): String? {
        val clean = rawUrl.trim()
        if (clean.isBlank() || clean.length > 2048 || clean.any { it.isISOControl() }) return null
        val candidate = if ("://" in clean) clean else "https://$clean"
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val allowedHosts = setOf("instagram.com", "www.instagram.com", "m.instagram.com", "instagr.am", "www.instagr.am")
        if (uri.scheme?.lowercase() !in setOf("http", "https") ||
            uri.userInfo != null || (uri.port != -1 && uri.port !in setOf(80, 443)) ||
            host !in allowedHosts
        ) return null

        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val typeIndex = segments.indexOfFirst { it in setOf("p", "reel", "reels", "tv") }
        if (typeIndex < 0 || typeIndex + 1 >= segments.size) return null
        val type = if (segments[typeIndex] == "reels") "reel" else segments[typeIndex]
        val shortcode = segments[typeIndex + 1]
        if (!shortcode.matches(Regex("^[A-Za-z0-9_-]+$"))) return null
        return "https://www.instagram.com/$type/$shortcode/"
    }

    fun isAllowedMediaUrl(rawUrl: String): Boolean {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (uri.scheme?.lowercase() != "https" || uri.userInfo != null ||
            (uri.port != -1 && uri.port != 443)
        ) return false
        return mediaCdnSuffixes.any { host.endsWith(it) }
    }

    internal fun isVideoMediaFormat(vcodec: String?, extension: String): Boolean {
        val codec = vcodec?.trim()
        if (!codec.isNullOrBlank()) return codec != "none"
        return extension.lowercase() in setOf("mp4", "m4v", "webm", "mov")
    }

    private fun selectMedia(entry: JSONObject, index: Int, isCarousel: Boolean = false): InstagramMediaItem? {
        val formats = entry.optJSONArray("formats")
        val candidates = mutableListOf<JSONObject>()
        if (formats != null) {
            for (i in 0 until formats.length()) {
                val format = formats.optJSONObject(i) ?: continue
                val formatUrl = format.optString("url")
                if (!isAllowedMediaUrl(formatUrl)) continue
                val formatExtension = format.optString("ext").ifBlank {
                    runCatching { URI(formatUrl).path.substringAfterLast('.', "") }.getOrDefault("")
                }.lowercase()
                val codec = format.optString("vcodec").takeIf { it.isNotBlank() }
                val supportedImage = formatExtension in setOf("jpg", "jpeg", "png", "webp")
                if (isVideoMediaFormat(codec, formatExtension) || supportedImage) candidates += format
            }
        }

        val selected = candidates.maxByOrNull { format ->
            format.optInt("height", 0) * 1_000_000L +
                format.optLong("tbr", 0L).coerceAtMost(999_999L)
        }
        val directUrl = selected?.optString("url")
            ?.takeIf(::isAllowedMediaUrl)
            ?: entry.optString("url").takeIf(::isAllowedMediaUrl)
            ?: return null

        val ext = selected?.optString("ext").orEmpty().ifBlank {
            runCatching { URI(directUrl).path.substringAfterLast('.', "") }.getOrDefault("")
        }.lowercase()
        val video = isVideoMediaFormat(selected?.optString("vcodec"), ext)
        val thumbnail = entry.optString("thumbnail")
            .takeIf(::isAllowedMediaUrl)
            ?: entry.optJSONArray("thumbnails")?.let { thumbnails ->
                (0 until thumbnails.length()).mapNotNull { thumbnails.optJSONObject(it)?.optString("url") }
                    .lastOrNull(::isAllowedMediaUrl)
            }

        return InstagramMediaItem(
            url = directUrl,
            isVideo = video,
            previewUrl = thumbnail ?: if (video) null else directUrl,
            index = index,
            isCarousel = isCarousel
        )
    }

    /**
     * Extracts public Instagram posts/Reels/carousels locally with yt-dlp.
     * No SnapInsta, Cobalt, or anonymous third-party resolver is contacted.
     */
    suspend fun fetchPostDetails(rawUrl: String): Result<InstagramPostData> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceUrl = normalizeInstagramUrl(rawUrl)
                ?: throw IllegalArgumentException("Masukkan URL Instagram Post, Reel, atau IGTV yang valid.")

            val request = YoutubeDLRequest(sourceUrl).apply {
                addOption("--dump-single-json")
                addOption("--no-warnings")
                addOption("--skip-download")
            }
            val response = YoutubeDL.getInstance().execute(request)
            val output = response.out.trim()
            if (output.isBlank()) throw IllegalStateException("yt-dlp tidak mengembalikan metadata Instagram.")

            val data = JSONObject(output)
            val entries = data.optJSONArray("entries")
            val mediaItems = mutableListOf<InstagramMediaItem>()
            if (entries != null) {
                for (i in 0 until entries.length()) {
                    val entry = entries.optJSONObject(i) ?: continue
                    selectMedia(entry, i + 1, isCarousel = entries.length() > 1)?.let(mediaItems::add)
                }
            }
            if (mediaItems.isEmpty()) {
                selectMedia(data, 1)?.let(mediaItems::add)
            }
            if (mediaItems.isEmpty()) {
                val message = data.optString("error").ifBlank {
                    "Tidak ada media publik yang dapat diekstrak dari post ini."
                }
                throw IllegalStateException(message)
            }

            val isCarousel = entries != null && entries.length() > 1
            val mediaType = when {
                isCarousel -> InstagramMediaType.CAROUSEL
                mediaItems.first().isVideo -> InstagramMediaType.VIDEO
                else -> InstagramMediaType.PHOTO
            }
            val caption = data.optString("description")
                .ifBlank { data.optString("title") }
                .ifBlank { "Instagram media (${data.optString("id", "post")})" }
            val author = data.optString("uploader")
                .ifBlank { data.optString("channel") }
                .ifBlank { "instagram_creator" }

            InstagramPostData(
                shortcode = extractShortcode(sourceUrl) ?: data.optString("id"),
                caption = caption,
                ownerUsername = data.optString("uploader_id").ifBlank { author }.removePrefix("@"),
                ownerAvatar = null,
                mediaType = mediaType,
                mediaList = mediaItems,
                likeCount = data.optInt("like_count", 0),
                commentCount = data.optInt("comment_count", 0),
                sourceUrl = sourceUrl,
                isCarousel = isCarousel
            )
        }.recoverCatching { error ->
            val message = error.message.orEmpty()
            when {
                message.contains("login", ignoreCase = true) ||
                    message.contains("private", ignoreCase = true) ||
                    message.contains("authentication", ignoreCase = true) ->
                    throw IllegalStateException("Konten Instagram memerlukan login atau tidak publik. Tool ini hanya mendukung konten publik yang boleh diakses.")
                else -> throw error
            }
        }
    }

    fun enqueueDownload(
        context: Context,
        sourceUrl: String,
        mediaItem: InstagramMediaItem,
        shortcode: String,
        author: String
    ) {
        val normalizedSource = normalizeInstagramUrl(sourceUrl)
        if (normalizedSource == null || !isAllowedMediaUrl(mediaItem.url)) {
            Toast.makeText(context, "URL media Instagram tidak valid.", Toast.LENGTH_LONG).show()
            return
        }

        val processId = "instagram-$shortcode-${mediaItem.index}-${System.currentTimeMillis()}"
        val outputDir = File(context.cacheDir, "instagram-downloads/$processId").apply { mkdirs() }
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching {
                val outputTemplate = File(outputDir, "%(title).100B [%(id)s].%(ext)s").absolutePath
                val request = YoutubeDLRequest(normalizedSource).apply {
                    addOption("-o", outputTemplate)
                    addOption("--no-warnings")
                    addOption("--no-overwrites")
                    addOption("--no-write-thumbnail")
                    addOption("--no-write-info-json")
                    addOption("--no-write-subs")
                    addOption("--no-write-auto-subs")
                    if (mediaItem.isCarousel) {
                        addOption("--playlist-items", mediaItem.index.toString())
                    } else {
                        addOption("--no-playlist")
                    }
                    if (mediaItem.isVideo) {
                        addOption("-f", "bestvideo+bestaudio/best")
                        addOption("--merge-output-format", "mp4")
                    } else {
                        addOption("-f", "best")
                    }
                }
                YoutubeDL.getInstance().execute(request, processId)
                val downloaded = outputDir.listFiles()
                    ?.filter { it.isFile && it.length() > 0L && !it.name.endsWith(".part") }
                    ?.maxByOrNull { it.lastModified() }
                    ?: throw IllegalStateException("yt-dlp selesai tetapi file media tidak ditemukan.")

                val extension = downloaded.extension.lowercase()
                val (mime, isImage) = when (extension) {
                    "mp4", "m4v" -> "video/mp4" to false
                    "mov" -> "video/quicktime" to false
                    "webm" -> "video/webm" to false
                    "jpg", "jpeg" -> "image/jpeg" to true
                    "png" -> "image/png" to true
                    "webp" -> "image/webp" to true
                    else -> throw IllegalStateException("Format hasil Instagram tidak didukung: $extension")
                }
                if (mediaItem.isVideo == isImage) {
                    throw IllegalStateException("Tipe file hasil tidak sesuai dengan media Instagram yang dipilih.")
                }
                val safeAuthor = MediaStoreSaver.sanitizeFileName(author, "creator").take(40)
                val fileName = MediaStoreSaver.sanitizeFileName(
                    "IG_${safeAuthor}_${shortcode}_${mediaItem.index}.$extension"
                )
                val pending = if (isImage) {
                    MediaStoreSaver.beginImageOutput(context, fileName, mime)
                } else {
                    MediaStoreSaver.beginVideoOutput(context, fileName, mime)
                } ?: throw IllegalStateException("Tidak dapat membuat file tujuan di penyimpanan publik.")
                try {
                    downloaded.inputStream().use { input -> input.copyTo(pending.output, 64 * 1024) }
                    pending.commit()
                } catch (e: Exception) {
                    pending.abort()
                    throw e
                }
                downloaded.delete()
                pending.uri
            }
            outputDir.deleteRecursively()
            withContext(Dispatchers.Main) {
                result.onSuccess {
                    Toast.makeText(context, "Media tersimpan di ${if (mediaItem.isVideo) "Movies" else "Pictures"}/IO-Tools.", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(context, "Gagal mengunduh Instagram: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun sharePost(context: Context, shortcode: String, caption: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Instagram Media")
                putExtra(Intent.EXTRA_TEXT, "Instagram Post: https://www.instagram.com/p/$shortcode/\n\n$caption")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Post Instagram"))
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
