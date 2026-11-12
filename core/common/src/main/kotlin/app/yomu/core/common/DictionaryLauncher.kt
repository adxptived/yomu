package app.yomu.core.common

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Sends a selected word to an external dictionary app. Dictionary apps accept words in different ways
 * (`ACTION_SEARCH`, `ACTION_PROCESS_TEXT`, `ACTION_SEND`, or a private URI scheme), so every known form is tried
 * before falling back to the system chooser of text-processing apps.
 */
object DictionaryLauncher {
    /** ColourDict understands its own scheme and also registers for search. */
    private const val COLORDICT_PACKAGE = "com.socialnmobile.colordict"

    /**
     * Opens [text] in one of [packages]. An empty list means "ask the system": the chooser shows every installed
     * dictionary (Lingvo, ColorDict, GoldenDict…) that handles selected text.
     */
    fun lookup(context: Context, text: String, packages: List<String>): Boolean {
        val word = text.trim().take(300)
        if (word.isEmpty()) return false
        for (pkg in packages) {
            if (pkg.startsWith(COLORDICT_PACKAGE) && start(context, Intent(Intent.ACTION_VIEW, Uri.parse("colordict://dict/${Uri.encode(word)}")).setPackage(pkg))) return true
            if (start(context, Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra(SearchManager.QUERY, word))) return true
            if (start(context, processText(word).setPackage(pkg))) return true
            if (start(context, Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(pkg).putExtra(Intent.EXTRA_TEXT, word))) return true
        }
        // System chooser: ACTION_PROCESS_TEXT lists dictionary and translator apps installed for selected text.
        if (start(context, Intent.createChooser(processText(word), null))) return true
        return start(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, word), null))
    }

    private fun processText(word: String): Intent = Intent(Intent.ACTION_PROCESS_TEXT)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_PROCESS_TEXT, word)
        .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
