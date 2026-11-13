package app.yomu.feature.reader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.yomu.core.common.Files
import app.yomu.core.ui.components.SearchField
import app.yomu.core.ui.theme.Dimens
import app.yomu.feature.reader.epub.ReaderCss
import java.io.File
import app.yomu.core.ui.R as CoreR

/** Font family for the sample in the picker: imported faces cannot be loaded by Compose, so they use the default. */
internal fun previewFamily(name: String): FontFamily = when (name) {
    "serif", "georgia", "literata" -> FontFamily.Serif
    "monospace" -> FontFamily.Monospace
    "sans-serif", "roboto" -> FontFamily.SansSerif
    else -> FontFamily.Default
}

/**
 * The list of book fonts with a live sample of every face, a search and the import at the end. Importing a file that is
 * not a font explains the failure instead of closing without a word; imported fonts can be removed again.
 */
@Composable
internal fun BookFontDialog(current: String, vm: ReaderViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val fontsDir = remember { File(context.filesDir, "fonts").apply { mkdirs() } }
    var custom by remember { mutableStateOf(fontsDir.listFiles()?.map { it.name }?.sorted().orEmpty()) }
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val sample = stringResource(R.string.rs_font_sample)
    val importFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = Files.sanitize(Files.displayName(context, uri) ?: "font.ttf")
        val target = File(fontsDir, name)
        val ok = runCatching {
            Files.copy(context, uri, target)
            // A tiny file is almost never a usable face; a real one is at least a few kilobytes.
            if (target.length() < 1024) error("too small")
        }.isSuccess
        if (ok) {
            custom = fontsDir.listFiles()?.map { it.name }?.sorted().orEmpty()
            vm.updateText { it.copy(fontFamily = name) }
            message = context.getString(R.string.rs_font_imported, name)
        } else {
            target.delete()
            message = context.getString(R.string.rs_font_import_failed)
        }
    }
    val all = ReaderCss.BUILT_IN_FONTS + custom
    val needle = query.trim()
    val shown = if (needle.isEmpty()) all else all.filter { fontLabelText(it).contains(needle, ignoreCase = true) || it.contains(needle, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rs_font_pick_title)) },
        text = {
            Column {
                SearchField(query = query, onQueryChange = { query = it }, placeholder = stringResource(R.string.rs_font_search))
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = Dimens.s))
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp).padding(top = Dimens.s)) {
                    items(shown) { name ->
                        val selected = name == current
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = Dimens.touch)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { vm.updateText { it.copy(fontFamily = name) }; onDismiss() })
                                .padding(vertical = Dimens.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Column(Modifier.weight(1f).padding(start = Dimens.m)) {
                                Text(fontLabelText(name), style = MaterialTheme.typography.bodyLarge)
                                Text(sample, style = MaterialTheme.typography.bodyMedium, fontFamily = previewFamily(name), maxLines = 1)
                            }
                            if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            if (name in custom) {
                                val deleteLabel = stringResource(R.string.rs_font_delete, fontLabelText(name))
                                IconButton(onClick = {
                                    vm.deleteFont(name)
                                    custom = custom - name
                                    message = context.getString(R.string.rs_font_deleted)
                                }) { Icon(Icons.Default.Delete, deleteLabel, tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                    item {
                        TextButton(
                            onClick = { importFont.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream", "*/*")) },
                            modifier = Modifier.padding(vertical = Dimens.s),
                        ) {
                            Icon(Icons.Default.Add, null)
                            Text(stringResource(R.string.rs_import_font), Modifier.padding(start = Dimens.s))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.close)) } },
    )
}

/** Human name of a font entry; custom faces are shown without their extension. */
@Composable
internal fun fontLabelText(f: String): String = when (f) {
    "serif" -> stringResource(R.string.rs_font_serif)
    "sans-serif" -> stringResource(R.string.rs_font_sans)
    "monospace" -> stringResource(R.string.rs_font_mono)
    "georgia" -> "Georgia"
    "literata" -> "Literata"
    "roboto" -> "Roboto"
    else -> f.substringBeforeLast('.')
}
