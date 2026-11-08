package dev.andikune.iottools.tools.instagram

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dev.andikune.iottools.tools.common.AppDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
    val fileExtension: String = if (isVideo) "mp4" else "jpg"
)

data class InstagramPostData(
    val shortcode: String,
    val caption: String,
    val ownerUsername: String,
    val ownerAvatar: String?,
    val mediaType: InstagramMediaType,
    val mediaList: List<InstagramMediaItem>,
    val likeCount: Int,
    val commentCount: Int
)

/**
 * Public Instagram extraction backed by the app's local yt-dlp engine.
 *
 * No third-party converter endpoints, login automation, private-account access,
 * DRM bypass, or platform access-control circumvention is attempted.
 */
object InstagramDownloaderService {

    private val videoExtensions = setOf("mp4", "m4v", "mov", "webm")
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp")

    fun extractShortcode(rawUrl: String): String? {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        if (host != "instagram.com" && !host.endsWith(".instagram.com")) return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val typeIndex = segments.indexOfFirst { it in setOf("p", "reel", "reels", "tv") }
        if (typeIndex < 0 || typeIndex + 1 >= segments.size) return null
        return segments[typeIndex + 1].takeIf { it.matches(Regex("^[A-Za-z0-9_-]+$")) }
    }

    /** Accept only HTTPS media URLs on Instagram's media CDN domains. */
    fun isAllowedMediaUrl(rawUrl: String): Boolean {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (uri.scheme?.lowercase() != "https") return false
        return host == "cdninstagram.com" ||
            host.endsWith(".cdninstagram.com") ||
            host == "fbcdn.net" ||
            host.endsWith(".fbcdn.net")
    }

    suspend fun fetchPostDetails(rawUrl: String): Result<InstagramPostData> = withContext(Dispatchers.IO) {
        runCatching {
            val shortcode = extractShortcode(rawUrl)
                ?: throw IllegalArgumentException(
                    "Tautan harus berupa link Post, Reel, atau TV Instagram yang valid."
                )

            val request = YoutubeDLRequest(rawUrl.trim()).apply {
                addOption("--dump-single-json")
                addOption("--no-warnings")
                addOption("--skip-download")
                addOption("--no-call-home")
            }
            val response = YoutubeDL.getInstance().execute(request)
            val output = response.out.trim()
            if (output.isBlank()) {
                throw IllegalStateException("Engine Instagram tidak mengembalikan metadata media.")
            }

            val metadata = JSONObject(output)
            val entries = metadata.optJSONArray("entries")
            val candidates = mutableListOf<JSONObject>()
            if (entries != null) {
                for (index in 0 until entries.length()) {
                    entries.optJSONObject(index)?.let(candidates::add)
                }
            } else {
                candidates += metadata
            }

            val media = candidates.mapNotNull(::extractMediaItem).distinctBy { it.url }
            if (media.isEmpty()) {
                throw IllegalStateException(
                    "Media publik tidak ditemukan. Konten mungkin private, memerlukan login, sudah dihapus, atau tidak didukung extractor."
                )
            }

            val caption = metadata.optString("description")
                .ifBlank { metadata.optString("title") }
                .ifBlank { "Instagram media ($shortcode)" }
            val author = metadata.optString("uploader")
                .ifBlank { metadata.optString("channel") }
                .ifBlank { "instagram_creator" }
            val finalType = when {
                media.size > 1 -> InstagramMediaType.CAROUSEL
                media.first().isVideo -> InstagramMediaType.VIDEO
                else -> InstagramMediaType.PHOTO
            }

            InstagramPostData(
                shortcode = shortcode,
                caption = caption,
                ownerUsername = author.removePrefix("@"),
                ownerAvatar = metadata.optString("uploader_url").takeIf { it.startsWith("https://") },
                mediaType = finalType,
                mediaList = media,
                likeCount = metadata.optInt("like_count", 0),
                commentCount = metadata.optInt("comment_count", 0)
            )
        }.recoverCatching { error ->
            val message = error.message.orEmpty()
            when {
                message.contains("login", ignoreCase = true) ||
                    message.contains("private", ignoreCase = true) ||
                    message.contains("authentication", ignoreCase = true) ->
                    throw IllegalStateException(
                        "Instagram meminta login atau konten tidak publik. Tool ini hanya mendukung media publik yang boleh diakses."
                    )
                else -> throw error
            }
        }
    }

    private fun extractMediaItem(entry: JSONObject): InstagramMediaItem? {
        val formats = entry.optJSONArray("formats")
        val formatCandidates = mutableListOf<JSONObject>()
        if (formats != null) {
            for (index in 0 until formats.length()) {
                formats.optJSONObject(index)?.let(formatCandidates::add)
            }
        }

        val selected = formatCandidates
            .filter { format ->
                val url = format.optString("url")
                val vcodec = format.optString("vcodec", "none")
                val ext = format.optString("ext").lowercase()
                isAllowedMediaUrl(url) && vcodec != "none" &&
                    (ext in videoExtensions || ext.isBlank())
            }
            .maxByOrNull { it.optInt("height", 0) }

        val source = selected ?: entry.takeIf {
            isAllowedMediaUrl(it.optString("url"))
        } ?: return null

        val url = source.optString("url").takeIf(::isAllowedMediaUrl) ?: return null
        val ext = source.optString("ext").lowercase()
            .takeIf { it in videoExtensions || it in imageExtensions }
            ?: URI(url).path.orEmpty().substringAfterLast('.', "jpg").lowercase()
                .takeIf { it in videoExtensions || it in imageExtensions }
            ?: return null
        val isVideo = ext in videoExtensions ||
            source.optString("vcodec", "none") != "none"

        val preview = entry.optString("thumbnail")
            .takeIf(::isAllowedMediaUrl)
            ?: url

        return InstagramMediaItem(
            url = url,
            isVideo = isVideo,
            previewUrl = preview,
            fileExtension = ext
        )
    }

    fun enqueueDownload(
        context: Context,
        mediaItem: InstagramMediaItem,
        shortcode: String,
        author: String
    ) {
        val ext = mediaItem.fileExtension.lowercase()
        val mime = when (ext) {
            "mp4" -> "video/mp4"
            "m4v" -> "video/x-m4v"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "jpeg", "jpg" -> "image/jpeg"
            else -> {
                Toast.makeText(context, "Format media Instagram tidak didukung.", Toast.LENGTH_LONG).show()
                return
            }
        }
        val safeAuthor = author.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40).ifBlank { "creator" }
        val fileName = "IG_${safeAuthor}_${shortcode}_${System.currentTimeMillis() % 10000}.$ext"

        AppDownloadManager.startDownload(
            context = context,
            directUrl = mediaItem.url,
            suggestedFileName = fileName,
            title = "Instagram: @$author",
            mimeType = mime
        )
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
