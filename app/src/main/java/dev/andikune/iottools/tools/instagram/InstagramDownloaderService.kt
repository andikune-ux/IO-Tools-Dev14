package dev.andikune.iottools.tools.instagram

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import dev.andikune.iottools.tools.common.AppDownloadManager
import dev.andikune.iottools.tools.common.VideoDownloadHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.regex.Pattern

enum class InstagramMediaType {
    VIDEO,
    PHOTO,
    CAROUSEL
}

data class InstagramMediaItem(
    val url: String,
    val isVideo: Boolean,
    val previewUrl: String?
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

object InstagramDownloaderService {

    private val client by lazy { VideoDownloadHelper.client }

    fun extractShortcode(rawUrl: String): String? {
        val uri = runCatching { Uri.parse(rawUrl.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (uri.scheme !in setOf("http", "https")) return null
        if (host != "instagram.com" && !host.endsWith(".instagram.com")) return null
        val segments = uri.pathSegments
        val typeIndex = segments.indexOfFirst { it in setOf("p", "reel", "reels", "tv") }
        if (typeIndex < 0 || typeIndex + 1 >= segments.size) return null
        return segments[typeIndex + 1].takeIf { it.matches(Regex("^[A-Za-z0-9_-]+$")) }
    }

    /**
     * Resolves Instagram Post or Reel media links to extract REAL MP4 video CDN streams.
     * Uses Instagram Embed parser, SnapInsta API, and Cobalt resolver.
     */
    suspend fun fetchPostDetails(rawUrl: String): Result<InstagramPostData> = withContext(Dispatchers.IO) {
        try {
            val shortcode = extractShortcode(rawUrl)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Tautan harus berupa link Post, Reel, atau TV Instagram (contoh: instagram.com/reel/C... atau /p/C...)")
                )

            val mediaItems = mutableListOf<InstagramMediaItem>()
            var caption = "Instagram Video ($shortcode)"
            var author = "instagram_creator"
            var isVideoPost = rawUrl.contains("/reel") || rawUrl.contains("/reels") || rawUrl.contains("/tv")

            // Approach 1: Parse Instagram public embed page to extract genuine FB/IG CDN video_url
            val embedUrl = "https://www.instagram.com/p/$shortcode/embed/captioned/"
            val embedReq = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", VideoDownloadHelper.DEFAULT_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            try {
                client.newCall(embedReq).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string().orEmpty()

                        val authorMatcher = Pattern.compile("class=\"CaptionUsername\"[^>]*>([^<]+)<").matcher(html)
                        if (authorMatcher.find()) {
                            author = authorMatcher.group(1)?.trim() ?: author
                        }

                        val captionMatcher = Pattern.compile("class=\"Caption\"[^>]*>(.*?)</div>", Pattern.DOTALL).matcher(html)
                        if (captionMatcher.find()) {
                            val rawCap = captionMatcher.group(1)?.replace("<[^>]+>".toRegex(), "")?.trim()
                            if (!rawCap.isNullOrBlank()) caption = rawCap
                        }

                        val videoPatterns = listOf(
                            Pattern.compile("\"video_url\":\"([^\"]+)\""),
                            Pattern.compile("video_url=([^\"]+?)(?:&|\")"),
                            Pattern.compile("<video[^>]+src=\"([^\"]+)\"")
                        )

                        for (pat in videoPatterns) {
                            val matcher = pat.matcher(html)
                            if (matcher.find()) {
                                var vUrl = matcher.group(1)
                                if (!vUrl.isNullOrBlank()) {
                                    vUrl = vUrl.replace("\\u0026", "&")
                                        .replace("&amp;", "&")
                                        .replace("\\/", "/")
                                    if (vUrl.startsWith("http") && (vUrl.contains("fbcdn.net") || vUrl.contains("cdninstagram.com") || vUrl.contains(".mp4"))) {
                                        mediaItems.add(InstagramMediaItem(vUrl, true, vUrl))
                                        isVideoPost = true
                                        break
                                    }
                                }
                            }
                        }

                        if (mediaItems.isEmpty()) {
                            val imgMatcher = Pattern.compile("class=\"EmbeddedMediaImage\"[^>]*src=\"([^\"]+)\"").matcher(html)
                            if (imgMatcher.find()) {
                                val imgUrl = imgMatcher.group(1)?.replace("&amp;", "&")?.replace("\\/", "/")
                                if (!imgUrl.isNullOrBlank() && imgUrl.startsWith("http")) {
                                    mediaItems.add(InstagramMediaItem(imgUrl, false, imgUrl))
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // Approach 2: SnapInsta API endpoint
            if (mediaItems.isEmpty()) {
                try {
                    val resolverUrl = "https://api.snapinsta.to/api/convert?url=${Uri.encode("https://www.instagram.com/p/$shortcode/")}"
                    val req = Request.Builder()
                        .url(resolverUrl)
                        .header("User-Agent", VideoDownloadHelper.DEFAULT_USER_AGENT)
                        .build()

                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            val json = JSONObject(body)
                            val direct = json.optString("url", "")
                            if (direct.isNotBlank() && direct.startsWith("http")) {
                                val isVid = json.optBoolean("isVideo", true)
                                mediaItems.add(InstagramMediaItem(direct, isVid, direct))
                                if (isVid) isVideoPost = true
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Approach 3: Cobalt resolver
            if (mediaItems.isEmpty()) {
                val cobaltEndpoints = listOf("https://api.cobalt.tools", "https://co.wuk.sh")
                for (endpoint in cobaltEndpoints) {
                    try {
                        val cobaltPayload = JSONObject().apply {
                            put("url", "https://www.instagram.com/reel/$shortcode/")
                        }
                        val cobaltReq = Request.Builder()
                            .url("$endpoint/")
                            .header("Accept", "application/json")
                            .header("Content-Type", "application/json")
                            .header("User-Agent", VideoDownloadHelper.DEFAULT_USER_AGENT)
                            .post(cobaltPayload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                            .build()

                        client.newCall(cobaltReq).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val body = resp.body?.string().orEmpty()
                                val json = JSONObject(body)
                                val streamUrl = json.optString("url", "")
                                if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                                    mediaItems.add(InstagramMediaItem(streamUrl, true, streamUrl))
                                    isVideoPost = true
                                }
                            }
                        }
                    } catch (_: Exception) {}
                    if (mediaItems.isNotEmpty()) break
                }
            }

            if (mediaItems.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "Tidak dapat mengekstrak stream video asli dari Instagram. Pastikan akun tidak diprivate dan tautan berupa Reel/Post yang aktif."
                    )
                )
            }

            val finalType = if (isVideoPost) InstagramMediaType.VIDEO else InstagramMediaType.PHOTO
            val postData = InstagramPostData(
                shortcode = shortcode,
                caption = caption,
                ownerUsername = author,
                ownerAvatar = null,
                mediaType = finalType,
                mediaList = mediaItems,
                likeCount = 0,
                commentCount = 0
            )

            Result.success(postData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads genuine media directly to Android Downloads directory as .mp4 or .jpg
     * with live 0-100% progress and speed tracking in-app.
     */
    fun enqueueDownload(
        context: Context,
        mediaItem: InstagramMediaItem,
        shortcode: String,
        author: String
    ) {
        val ext = if (mediaItem.isVideo) "mp4" else "jpg"
        val mime = if (mediaItem.isVideo) "video/mp4" else "image/jpeg"
        val fileName = "IG_${author}_${shortcode}_${System.currentTimeMillis() % 10000}.$ext"

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
