package app.yomu.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.yomu.core.ui.R as CoreR
import app.yomu.data.catalog.CatalogEditionSummary

@Composable
internal fun EditionComparisonDialog(
    comparison: TitleViewModel.EditionComparison,
    tabNames: Map<Long, String>,
    onDismiss: () -> Unit,
    onAddSeparately: () -> Unit,
    onOpenSaved: ((Long) -> Unit)?,
    onReplace: ((Long) -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_duplicate_heading)) },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.title_duplicate_hint))
                EditionCard(comparison.incoming, comparison.sourceNames, tabNames)
                Text(stringResource(R.string.title_duplicate_saved), style = MaterialTheme.typography.titleSmall)
                comparison.saved.forEach { saved ->
                    EditionCard(saved, comparison.sourceNames, tabNames) {
                        FlowRow {
                            if (onOpenSaved != null) {
                                TextButton(onClick = { onOpenSaved(saved.entry.id) }) {
                                    Text(stringResource(R.string.title_open_saved_edition))
                                }
                            }
                            if (onReplace != null) {
                                TextButton(onClick = { onReplace(saved.entry.id) }) {
                                    Text(stringResource(R.string.title_replace_saved_edition))
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.cancel)) } },
        confirmButton = {
            TextButton(onClick = onAddSeparately) { Text(stringResource(R.string.title_add_edition_separately)) }
        },
    )
}

@Composable
private fun EditionCard(
    summary: CatalogEditionSummary,
    sourceNames: Map<Long, String>,
    tabNames: Map<Long, String>,
    actions: @Composable () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(summary.entry.title, style = MaterialTheme.typography.titleSmall)
            Text(sourceNames[summary.entry.sourceId] ?: stringResource(R.string.title_duplicate_missing_source, summary.entry.sourceId))
            Text(stringResource(R.string.chapters_read_of, summary.chaptersRead, summary.chapterCount))
            summary.lastChapter?.let {
                Text(stringResource(R.string.title_duplicate_last_chapter, it.name, it.lastPageRead + 1))
            }
            val shelves = summary.tabIds.mapNotNull(tabNames::get)
            if (shelves.isNotEmpty()) Text(shelves.joinToString())
            actions()
        }
    }
}
