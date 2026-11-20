package app.yomu.feature.dictionary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomu.core.ui.components.ChipItem
import app.yomu.core.ui.components.EmptyState
import app.yomu.core.ui.components.FilterChipsRow
import app.yomu.core.ui.components.SearchField
import app.yomu.core.ui.components.navBarBottomPadding
import app.yomu.core.ui.theme.Dimens
import app.yomu.domain.glossary.GlossaryEntry
import app.yomu.domain.glossary.GlossaryKind
import app.yomu.domain.index.IndexPlan
import app.yomu.domain.index.IndexPlans
import app.yomu.domain.index.IndexState
import app.yomu.domain.index.TextSearchKind
import app.yomu.domain.model.Locator

/** Glossary notes and the opt-in index panel, hosted by the Dictionary destination. */
@Composable
fun GlossaryContent(viewModel: GlossaryViewModel, onOpenBook: (Long, Locator?, String) -> Unit) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val bookFilter by viewModel.bookFilter.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val booksWithEntries by viewModel.booksWithEntries.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val estimate by viewModel.estimate.collectAsStateWithLifecycle()
    val enabled by viewModel.indexEnabled.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val hits by viewModel.hitsList.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<GlossaryEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<GlossaryEntry?>(null) }
    var bookMenu by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = navBarBottomPadding(88.dp))) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = Dimens.xs)) {
                    SearchField(
                        query = query,
                        onQueryChange = { viewModel.query.value = it },
                        placeholder = stringResource(R.string.gloss_search),
                        modifier = Modifier.weight(1f).padding(start = Dimens.l, top = Dimens.s, bottom = Dimens.s),
                    )
                    Box {
                        IconButton(onClick = { bookMenu = true }) { Icon(Icons.Default.Book, stringResource(R.string.gloss_filter_book)) }
                        DropdownMenu(expanded = bookMenu, onDismissRequest = { bookMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.gloss_all_books)) },
                                onClick = { bookMenu = false; viewModel.bookFilter.value = null },
                            )
                            booksWithEntries.forEach { (id, title) ->
                                DropdownMenuItem(
                                    text = { Text(title) },
                                    onClick = { bookMenu = false; viewModel.bookFilter.value = id },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { adding = true }) { Icon(Icons.Default.Add, stringResource(R.string.gloss_add)) }
                }
            }
            item {
                IndexPanel(
                    viewModel = viewModel,
                    plan = plan,
                    enabled = enabled,
                    estimateSections = estimate.sections,
                    estimateBooks = estimate.books,
                    searchQuery = searchQuery,
                    hits = hits,
                    onOpenBook = onOpenBook,
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.s), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.gloss_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { adding = true }) { Text(stringResource(R.string.gloss_add)) }
                }
            }
            if (entries.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.gloss_empty_title),
                        message = stringResource(R.string.gloss_empty_text),
                        icon = Icons.Default.Book,
                    )
                }
            }
            items(entries, key = { it.id }) { entry ->
                ListItem(
                    headlineContent = { Text(entry.term, maxLines = 1) },
                    supportingContent = {
                        Column {
                            if (entry.aliases.isNotEmpty()) {
                                Text(entry.aliases.joinToString(", "), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            entry.note?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            Text(stringResource(kindLabel(entry.kind)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    trailingContent = {
                        Row {
                            if (entry.bookId != null && entry.locator != null) {
                                IconButton(onClick = { onOpenBook(entry.bookId!!, entry.locator, "g:${entry.id}:${entry.updatedAt}") }) {
                                    Icon(Icons.Default.Book, stringResource(R.string.gloss_open_book))
                                }
                            }
                            IconButton(onClick = { deleting = entry }) { Icon(Icons.Default.Delete, stringResource(app.yomu.core.ui.R.string.delete)) }
                        }
                    },
                    modifier = Modifier.clickable { editing = entry },
                )
            }
        }
    }

    if (adding || editing != null) {
        GlossaryEditDialog(
            initial = editing,
            books = books,
            onSave = { entry ->
                viewModel.save(entry)
                editing = null
                adding = false
            },
            onDismiss = { editing = null; adding = false },
        )
    }
    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.gloss_delete_title)) },
            text = { Text(stringResource(R.string.gloss_delete_text, entry.term)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(entry.id); deleting = null }) {
                    Text(stringResource(app.yomu.core.ui.R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(app.yomu.core.ui.R.string.cancel)) } },
        )
    }
}

@Composable
private fun IndexPanel(
    viewModel: GlossaryViewModel,
    plan: IndexPlan,
    enabled: Boolean,
    estimateSections: Int,
    estimateBooks: Int,
    searchQuery: String,
    hits: List<app.yomu.domain.index.IndexedHit>,
    onOpenBook: (Long, Locator?, String) -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.s)) {
        Column(Modifier.padding(Dimens.m), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.xs)) {
            Text(stringResource(R.string.gloss_index_title), style = MaterialTheme.typography.titleMedium)
            Text(indexStateLabel(plan), style = MaterialTheme.typography.bodyMedium)
            if (!enabled) {
                Text(stringResource(R.string.gloss_index_disabled), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (plan.running && plan.fraction != null) {
                LinearProgressIndicator(progress = { plan.fraction ?: 0f }, modifier = Modifier.fillMaxWidth())
            }
            if (estimateSections > 0) {
                Text(stringResource(R.string.gloss_index_estimate, estimateBooks, estimateSections), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { viewModel.estimate() }, enabled = enabled && !plan.running) {
                    Text(stringResource(R.string.gloss_index_estimate_btn))
                }
                FilledTonalButton(onClick = { viewModel.rebuild() }, enabled = enabled && !plan.running) {
                    Text(stringResource(R.string.gloss_index_rebuild))
                }
                if (plan.running) {
                    TextButton(onClick = { viewModel.cancel() }) { Text(stringResource(R.string.gloss_index_cancel)) }
                }
            }
            SearchField(
                query = searchQuery,
                onQueryChange = { viewModel.searchQuery.value = it },
                placeholder = stringResource(R.string.gloss_index_search),
                onSearch = { viewModel.searchIndexed() },
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s),
            )
            // Never call a heading search "fulltext": say what is actually searchable.
            if (IndexPlans.searchKind(enabled, plan) == TextSearchKind.HEADING) {
                Text(stringResource(R.string.gloss_index_heading_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (hits.isNotEmpty()) {
                Text(stringResource(R.string.gloss_index_results), style = MaterialTheme.typography.titleSmall)
                hits.take(20).forEach { hit ->
                    ListItem(
                        headlineContent = { Text(hit.text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(stringResource(R.string.gloss_index_source, hit.source), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.clickable { onOpenBook(hit.bookId, null, "i:${hit.key}") },
                    )
                }
            }
        }
    }
}

@Composable
private fun indexStateLabel(plan: IndexPlan): String = when (plan.state) {
    IndexState.IDLE -> stringResource(R.string.gloss_index_state_idle)
    IndexState.ESTIMATING -> stringResource(R.string.gloss_index_state_estimating)
    IndexState.RUNNING -> stringResource(R.string.gloss_index_state_running, plan.progress.done, plan.progress.total)
    IndexState.CANCELLED -> stringResource(R.string.gloss_index_state_cancelled)
    IndexState.DONE -> stringResource(R.string.gloss_index_state_done, plan.estimate.sections)
    IndexState.FAILED -> stringResource(R.string.gloss_index_state_failed, plan.error.orEmpty())
}

private fun kindLabel(kind: GlossaryKind) = when (kind) {
    GlossaryKind.CHARACTER -> R.string.gloss_kind_character
    GlossaryKind.TERM -> R.string.gloss_kind_term
    GlossaryKind.PLACE -> R.string.gloss_kind_place
}

@Composable
private fun GlossaryEditDialog(initial: GlossaryEntry?, books: List<Pair<Long, String>>, onSave: (GlossaryEntry) -> Unit, onDismiss: () -> Unit) {
    var term by rememberSaveable { mutableStateOf(initial?.term.orEmpty()) }
    var aliases by rememberSaveable { mutableStateOf(initial?.aliases?.joinToString(", ").orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: GlossaryKind.TERM) }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    var bookId by rememberSaveable { mutableStateOf(initial?.bookId) }
    var bookMenu by remember { mutableStateOf(false) }
    val bookTitle = books.firstOrNull { it.first == bookId }?.second
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.gloss_add else R.string.gloss_edit)) },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.xs)) {
                OutlinedTextField(term, { term = it }, label = { Text(stringResource(R.string.gloss_term)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(aliases, { aliases = it }, label = { Text(stringResource(R.string.gloss_aliases)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.gloss_kind), style = MaterialTheme.typography.labelMedium)
                FilterChipsRow(GlossaryKind.entries.map { k -> ChipItem(stringResource(kindLabel(k)), kind == k, { kind = k }) })
                Box {
                    OutlinedTextField(
                        value = bookTitle ?: stringResource(R.string.gloss_no_book),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.gloss_book)) },
                        trailingIcon = { IconButton(onClick = { bookMenu = true }) { Icon(Icons.Default.KeyboardArrowDown, null) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(expanded = bookMenu, onDismissRequest = { bookMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.gloss_no_book)) },
                            onClick = { bookMenu = false; bookId = null },
                        )
                        books.forEach { (id, title) ->
                            DropdownMenuItem(text = { Text(title) }, onClick = { bookMenu = false; bookId = id })
                        }
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.gloss_note)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = term.isNotBlank(), onClick = {
                val now = System.currentTimeMillis()
                onSave(
                    (initial ?: GlossaryEntry(term = "")).copy(
                        term = term.trim(),
                        aliases = aliases.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                        kind = kind,
                        note = note.trim().ifBlank { null },
                        bookId = bookId,
                        createdAt = if (initial == null) now else initial.createdAt,
                        updatedAt = now,
                    ),
                )
            }) { Text(stringResource(app.yomu.core.ui.R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(app.yomu.core.ui.R.string.cancel)) } },
    )
}
