package com.maheshkatakam.stash.link

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.maheshkatakam.stash.StashApp
import java.io.IOException

/**
 * Fetches a saved link's title/text/picture in the background. It waits for internet, so links
 * saved offline get their preview later, and retries a few times if the network drops.
 */
class LinkPreviewWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1L)
        val force = inputData.getBoolean(KEY_FORCE, false)
        val repo = (applicationContext as StashApp).repository
        val item = repo.getItemRow(id) ?: return Result.success()
        val url = item.url ?: return Result.success()
        return try {
            val preview = if (force || item.linkImageUrl == null) LinkPreviewFetcher.fetch(url) else null
            val imageUrl = preview?.imageUrl ?: item.linkImageUrl
            val needThumb = imageUrl != null && (item.linkThumb == null || (force && preview?.imageUrl != null))
            val thumb = if (needThumb && imageUrl != null) repo.files.downloadImage(imageUrl) else null
            if (thumb != null && item.linkThumb != null) repo.files.delete(item.linkThumb)
            repo.applyPreview(id, preview?.title, preview?.description, preview?.imageUrl, thumb)
            Result.success()
        } catch (e: IOException) {
            if (runAttemptCount < 4) Result.retry() else Result.success()
        }
    }

    companion object {
        const val KEY_ID = "itemId"
        const val KEY_FORCE = "force"
    }
}
