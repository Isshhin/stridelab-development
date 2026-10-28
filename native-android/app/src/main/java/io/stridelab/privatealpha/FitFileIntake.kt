package io.stridelab.privatealpha

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.IOException

/**
 * Phase 41: read-only inspection of user-selected files and share intents.
 * Does not persist, publish, deduplicate, or create an ActivityRecord.
 */
internal object FitFileIntake {
    fun inspect(context:Context,uri:Uri,account:String):FitContainerReader.Result {
        require(uri.scheme=="content" || uri.scheme=="file") {
            "FIT inspection requires a user-provided document or app-private file URI."
        }
        val stream=context.contentResolver.openInputStream(uri)
            ?: throw IOException("Selected FIT document is unreadable.")
        return FitContainerReader.read(stream,account,"fit-file")
    }

    fun sharedSingleFile(intent:Intent):Uri? {
        if(intent.action!=Intent.ACTION_SEND) return null
        @Suppress("DEPRECATION")
        val candidate=intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return null
        return candidate.takeIf { it.scheme=="content" || it.scheme=="file" }
    }
}
