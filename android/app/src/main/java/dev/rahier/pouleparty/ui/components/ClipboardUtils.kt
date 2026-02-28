package dev.rahier.pouleparty.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Copy [text] to the system clipboard with the given [label].
 */
fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
