package com.cycling.mynote.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.cycling.mynote.core.error.DataError
import com.cycling.mynote.data.repo.RepoPathResolver
import com.cycling.mynote.data.repo.RepoSession
import com.cycling.mynote.data.saf.DocumentTreeStore
import com.cycling.mynote.di.IoDispatcher
import com.cycling.mynote.markdown.ImageReference
import com.cycling.mynote.markdown.MarkdownImages
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Loads the pictures a note refers to, from the repository or from the network.
 *
 * One loader for both sources, because everything after the bytes arrive is the same — decode,
 * downscale, cache — and because the preview should not have to care whether `![](...)` named a file
 * or an address.
 *
 * The network part is hand-rolled rather than delegated to an image library: the app has exactly one
 * need here (fetch bytes once, scale them down), and a library would add a dependency, its own disk
 * cache and a second HTTP stack to keep current for that.
 *
 * Images are cached in memory under the reference they were loaded from. Replacing an image file
 * without changing its name keeps the old picture until the app restarts — the trade a reference-keyed
 * cache makes, and the right one while the alternative is re-reading every image on every
 * recomposition.
 */
@Singleton
class NoteImageLoader @Inject constructor(
    private val store: DocumentTreeStore,
    private val paths: RepoPathResolver,
    private val session: RepoSession,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val cache = LruCache<String, Bitmap>(MAX_CACHED_KB)

    /**
     * @param reference the target written in the note, exactly as it appears in `![](...)`.
     * @param noteFolder the folder of the note doing the referring, for relative paths.
     * @return the decoded image, or `null` when it could not be read, fetched or decoded.
     */
    suspend fun load(reference: String, noteFolder: String): Bitmap? {
        val resolved = MarkdownImages.resolve(reference, noteFolder)
        val key = when (resolved) {
            is ImageReference.Remote -> "url:${resolved.url}"
            is ImageReference.RepoFile -> "repo:${resolved.path}"
            is ImageReference.External -> return null
        }
        cache.get(key)?.let { return it }

        val bitmap = withContext(io) {
            when (resolved) {
                is ImageReference.Remote -> decode(fetch(resolved.url))
                is ImageReference.RepoFile -> decode(readFromRepo(resolved.path))
                is ImageReference.External -> null
            }
        }
        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    private suspend fun readFromRepo(path: String): ByteArray? {
        val treeUri = try {
            session.requireTreeUri()
        } catch (e: DataError) {
            return null
        }
        val documentId = try {
            paths.resolveDocumentId(treeUri, path)
        } catch (e: DataError) {
            return null
        }
        return store.readBytes(treeUri, documentId, MAX_IMAGE_BYTES)
    }

    private fun fetch(url: String): ByteArray? {
        val connection = try {
            URL(url).openConnection() as? HttpURLConnection ?: return null
        } catch (e: IOException) {
            return null
        }

        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            if (connection.responseCode !in 200..299) return null
            if (connection.contentLength > MAX_IMAGE_BYTES) return null
            return connection.inputStream.use { readAtMost(it, MAX_IMAGE_BYTES) }
        } catch (e: IOException) {
            return null
        } finally {
            connection.disconnect()
        }
    }

    private fun readAtMost(stream: InputStream, maxBytes: Int): ByteArray? {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = stream.read(chunk)
            if (read < 0) break
            if (buffer.size() + read > maxBytes) return null
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    /**
     * Decodes with a power-of-two sample, so a phone-sized picture is never allocated at full camera
     * resolution: the preview is a few hundred dp wide, and a 12 MP image would be tens of megabytes
     * in memory at full size.
     */
    private fun decode(bytes: ByteArray?): Bitmap? {
        if (bytes == null || bytes.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_WIDTH && bounds.outHeight / (sample * 2) >= MAX_HEIGHT) {
            sample *= 2
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
        })
    }

    private companion object {
        const val MAX_WIDTH = 1_200
        const val MAX_HEIGHT = 1_200
        const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
        const val MAX_CACHED_KB = 16 * 1024
        const val TIMEOUT_MS = 8_000
    }
}
