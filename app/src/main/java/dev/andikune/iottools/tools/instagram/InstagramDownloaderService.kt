package dev.andikune.iottools.tools.instagram

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
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

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Extracts shortcode from Instagram URLs:
     * - https://www.instagram.com/p/SHORTCODE/
     * - https://www.instagram.com/reel/SHORTCODE/
     * - https://www.instagram.com/reels/SHORTCODE/
     * - https://instagram.com/tv/SHORTCODE/
     */
    fun extractShortcode(rawUrl: String): String? {
        val clean = rawUrl.trim()
        val pattern = Pattern.compile("instagram\\.com/(?:p|reel|reels|tv)/([^/?#&\\s]+)")
        val matcher = pattern.matcher(clean)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    /**
     * Resolves Instagram Post or Reel media links.
     * Uses Instagram Graph API / embed scraper with multiple fallback endpoints.
     */
    suspend fun fetchPostDetails(rawUrl: String): Result<InstagramPostData> = withContext(Dispatchers.IO) {
        try {
            val shortcode = extractShortcode(rawUrl)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Tautan harus berupa link Post, Reel, atau TV Instagram (contoh: instagram.com/reel/C... atau /p/C...)")
                )

            val mediaItems = mutableListOf<InstagramMediaItem>()
            var caption = "Instagram Post ($shortcode)"
            var author = "instagram_user"
            var isVideoPost = rawUrl.contains("/reel") || rawUrl.contains("/reels") || rawUrl.contains("/tv")

            // Approach 1: Try public Instagram Embed page to extract OpenGraph video/image
            val embedUrl = "https://www.instagram.com/p/$shortcode/embed/captioned/"
            val embedReq = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            var extractedViaEmbed = false
            try {
                client.newCall(embedReq).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string().orEmpty()
                        
                        // Extract Author
                        val authorMatcher = Pattern.compile("class=\"CaptionUsername\"[^>]*>([^<]+)<").matcher(html)
                        if (authorMatcher.find()) {
                            author = authorMatcher.group(1)?.trim() ?: author
                        }

                        // Extract Caption
                        val captionMatcher = Pattern.compile("class=\"Caption\"[^>]*>(.*?)</div>", Pattern.DOTALL).matcher(html)
                        if (captionMatcher.find()) {
                            val rawCap = captionMatcher.group(1)?.replace("<[^>]+>".toRegex(), "")?.trim()
                            if (!rawCap.isNullOrBlank()) caption = rawCap
                        }

                        // Check for Embedded Video
                        val videoMatcher = Pattern.compile("class=\"EmbeddedMediaImage\"[^>]*src=\"([^\"]+)\"").matcher(html)
                        val videoUrlMatcher = Pattern.compile("\"video_url\":\"([^\"]+)\"").matcher(html)
                        
                        if (videoUrlMatcher.find()) {
                            val vUrl = videoUrlMatcher.group(1)?.replace("\\u0026", "&")
                            if (!vUrl.isNullOrBlank()) {
                                mediaItems.add(InstagramMediaItem(vUrl, true, vUrl))
                                isVideoPost = true
                                extractedViaEmbed = true
                            }
                        } else if (videoMatcher.find()) {
                            val imgUrl = videoMatcher.group(1)?.replace("&amp;", "&")
                            if (!imgUrl.isNullOrBlank()) {
                                mediaItems.add(InstagramMediaItem(imgUrl, false, imgUrl))
                                extractedViaEmbed = true
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Fallback continues
            }

            // Approach 2: If embed parser couldn't get full video stream, query dedicated IG downstream resolver
            if (mediaItems.isEmpty()) {
                val resolverUrl = "https://api.snapinsta.to/api/convert?url=${Uri.encode("https://www.instagram.com/p/$shortcode/")}"
                try {
                    val req = Request.Builder()
                        .url(resolverUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            val json = JSONObject(body)
                            val direct = json.optString("url", "")
                            if (direct.isNotBlank()) {
                                val isVid = json.optBoolean("isVideo", isVideoPost)
                                mediaItems.add(InstagramMediaItem(direct, isVid, direct))
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Fallback: If external APIs block due to rate limits, create direct media proxy link
            if (mediaItems.isEmpty()) {
                val directMediaUrl = "https://ddinstagram.com/images/$shortcode/1.jpg"
                val directVideoUrl = "https://ddinstagram.com/videos/$shortcode/1.mp4"
                
                if (isVideoPost) {
                    mediaItems.add(InstagramMediaItem(directVideoUrl, true, directMediaUrl))
                } else {
                    mediaItems.add(InstagramMediaItem(directMediaUrl, false, directMediaUrl))
                }
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
     * Downloads media directly to standard Android Downloads directory.
     */
    fun enqueueDownload(
        context: Context,
        mediaItem: InstagramMediaItem,
        shortcode: String,
        author: String
    ): Long {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                ?: throw IllegalStateException("DownloadManager tidak tersedia di HP.")

            val ext = if (mediaItem.isVideo) "mp4" else "jpg"
            val fileName = "IG_${author}_${shortcode}_${System.currentTimeMillis() % 10000}.$ext"
            val downloadUri = Uri.parse(mediaItem.url)

            val request = DownloadManager.Request(downloadUri).apply {
                setTitle("Instagram: @$author")
                setDescription(if (mediaItem.isVideo) "Mengunduh Video / Reel Instagram..." else "Mengunduh Foto Instagram...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            val id = downloadManager.enqueue(request)
            Toast.makeText(
                context,
                "✓ Memulai unduhan: $fileName\nDisimpan di folder Download.",
                Toast.LENGTH_LONG
            ).show()
            return id
        } catch (e: Exception) {
            // Fallback to direct browser download
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mediaItem.url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "Membuka link unduhan via Browser...", Toast.LENGTH_SHORT).show()
                return 0L
            } catch (err: Exception) {
                Toast.makeText(context, "Gagal mengunduh: ${err.message}", Toast.LENGTH_SHORT).show()
                return -1L
            }
        }
    }

    /**
     * Share Instagram Post
     */
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
