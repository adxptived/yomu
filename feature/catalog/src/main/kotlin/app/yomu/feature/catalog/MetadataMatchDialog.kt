package app.yomu.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import app.yomu.core.ui.components.CoverImage
import app.yomu.core.ui.theme.Dimens
import app.yomu.domain.model.ExternalMetadata
import app.yomu.domain.model.MetadataAvailability
import app.yomu.domain.model.MetadataProvider
import app.yomu.domain.model.MetadataProviderState
import app.yomu.core.ui.R as CoreR

/**
 * Choosing an external metadata match (C1): the provider row switches between AniList, MyAnimeList, MangaBaka and the
 * source's own fields; the source's title is the first query; a result is previewed before the tap that confirms it.
 * Nothing is written before "Apply".
 */
@Composable
fun MetadataMatchDialog(
    state: TitleViewModel.MetadataSearchState,
    initialQuery: String,
    providers: List<MetadataProviderState>,
    provider: MetadataProvider?,
    onProvider: (MetadataProvider, String) -> Unit,
    onSearch: (String) -> Unit,
    onPick: (ExternalMetadata) -> Unit,
    onUseSource: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var selected by remember { mutableStateOf<ExternalMetadata?>(null) }
    // A new answer belongs to a new provider/query: an old preview must not be applied to it. Cleared on the actions
    // that start a search, not in an effect keyed by the state (which could restart while the dialog settles).
    fun searchFor(text: String) {
        selected = null
        onSearch(text)
    }
    fun chooseProvider(provider: MetadataProvider) {
        selected = null
        onProvider(provider, query)
    }
    val activeState = when (state) {
        is TitleViewModel.MetadataSearchState.Ready -> state.takeIf { it.provider == provider }
        is TitleViewModel.MetadataSearchState.Failed -> state.takeIf { it.provider == provider }
        else -> state
    } ?: TitleViewModel.MetadataSearchState.Loading
    val unavailable = providers.firstOrNull { it.provider == provider && !it.usable }
    val preview = selected

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.metadata_match_title)) },
        text = {
            Column(
                // A definite height keeps the dialog out of a measure loop when the answer is short (no results
                // yet, or a provider that could not be asked): the content is never taller than the cap.
                Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.s),
            ) {
                Text(
                    stringResource(R.string.metadata_provider),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // A scrollable row instead of a wrapping one: a FlowRow inside a dialog measured its content in a
                // loop on some devices/test hosts, and the chips are only four.
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    providers.forEach { entry ->
                        FilterChip(
                            selected = entry.provider == provider,
                            onClick = { chooseProvider(entry.provider) },
                            label = { Text(entry.provider.displayName, maxLines = 1) },
                        )
                    }
                }
                if (unavailable != null) {
                    Text(
                        stringResource(
                            when (unavailable.availability) {
                                MetadataAvailability.NEEDS_CLIENT_ID -> R.string.metadata_needs_client_id
                                MetadataAvailability.NEEDS_SIGN_IN -> R.string.metadata_needs_sign_in
                                else -> R.string.metadata_provider_unavailable
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (unavailable.availability == MetadataAvailability.NEEDS_CLIENT_ID) {
                        Text(
                            stringResource(R.string.metadata_needs_client_id_where),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.s),
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.metadata_match_query)) },
                        )
                        TextButton(onClick = { searchFor(query) }, enabled = query.isNotBlank()) {
                            Text(stringResource(R.string.metadata_match_search))
                        }
                    }
                    // The answer always lives in one scrolling list: a stable, bounded measure for results, an empty
                    // answer and an error alike (without it the dialog measured its content against its cap forever).
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(min = 120.dp, max = if (preview == null) 320.dp else 190.dp),
                        verticalArrangement = Arrangement.spacedBy(Dimens.xs),
                    ) {
                        when (val state = activeState) {
                            TitleViewModel.MetadataSearchState.Idle -> Unit
                            TitleViewModel.MetadataSearchState.Loading -> item(key = "loading") {
                                Box(Modifier.fillMaxWidth().padding(Dimens.l), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            is TitleViewModel.MetadataSearchState.Failed -> item(key = "failed") {
                                Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                                    Text(
                                        stringResource(R.string.metadata_provider_failed, state.provider.displayName, state.message.orEmpty()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    if (state.availability == MetadataAvailability.READY) {
                                        Text(
                                            stringResource(R.string.metadata_provider_try_other),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            is TitleViewModel.MetadataSearchState.Ready -> {
                                if (state.results.isEmpty()) {
                                    item(key = "empty") {
                                        Text(stringResource(R.string.metadata_match_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    items(state.results, key = { "${it.provider}:${it.id}" }) { item ->
                                        MetadataResultRow(
                                            item,
                                            selected = item.provider == preview?.provider && item.id == preview?.id,
                                        ) { selected = item }
                                    }
                                }
                            }
                        }
                    }
                }
                preview?.let { item ->
                    // The preview keeps the tap that confirms it apart: the card is not changed by looking.
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(Dimens.m), verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                            Text(stringResource(R.string.metadata_preview), style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.m), verticalAlignment = Alignment.Top) {
                                CoverImage(item.coverUrl, item.title, Modifier.width(56.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    item.altTitle?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    item.description?.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            Text(
                                stringResource(R.string.metadata_preview_fields, item.provenance.joinToString(", ")),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (preview != null) {
                TextButton(onClick = { onPick(preview) }) { Text(stringResource(R.string.metadata_apply)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.close)) }
            }
        },
        dismissButton = {
            if (preview != null) {
                TextButton(onClick = { selected = null }) { Text(stringResource(CoreR.string.cancel)) }
            } else {
                TextButton(onClick = onUseSource) { Text(stringResource(R.string.metadata_source_option)) }
            }
        },
    )
}

@Composable
private fun MetadataResultRow(item: ExternalMetadata, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(Dimens.s),
            horizontalArrangement = Arrangement.spacedBy(Dimens.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(item.coverUrl, item.title, Modifier.width(48.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                item.altTitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
