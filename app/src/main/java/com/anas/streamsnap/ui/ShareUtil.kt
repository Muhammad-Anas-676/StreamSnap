package com.anas.streamsnap.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.anas.streamsnap.data.DownloadItem
import com.anas.streamsnap.data.tr
import java.io.File

/** Share plain text (a link or a list of links) through the Android share sheet. */
internal fun shareText(ctx: Context, text: String) {
    try {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(i, tr("Share", "شیئر")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (t: Throwable) {
        Toast.makeText(ctx, tr("Could not share", "شیئر نہیں ہو سکا"), Toast.LENGTH_SHORT).show()
    }
}

/** Share several finished downloads at once. */
internal fun shareFiles(ctx: Context, items: List<DownloadItem>) {
    try {
        val uris = ArrayList<Uri>()
        for (it in items) {
            val f = File(it.filePath)
            if (it.filePath.isNotBlank() && f.exists()) {
                uris.add(FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f))
            }
        }
        if (uris.isEmpty()) {
            Toast.makeText(ctx, tr("File not found", "فائل نہیں ملی"), Toast.LENGTH_SHORT).show()
            return
        }
        val i = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        }
        i.type = "*/*"
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, tr("Share", "شیئر")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (t: Throwable) {
        Toast.makeText(ctx, tr("Could not share these files", "یہ فائلیں شیئر نہیں ہو سکیں"), Toast.LENGTH_SHORT).show()
    }
}
