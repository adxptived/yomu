package app.yomu.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import app.yomu.core.ui.R as CoreR

/**
 * Choosing an AniList match: the source's title is the first query, the results show the cover, year and format, and
 * a tap confirms the match. Nothing is written before the tap.
 */
@Composable
internal fun MetadataMatchDialog(
    state: TitleViewModel.MetadataSearchState,
    initialQuery: String,
    onSearch: (String) -> Unit,
    onPick: (ExternalMetadata) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.metadata_match_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.s),
            ) {
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
                    TextButton(onClick = { onSearch(query) }, enabled = query.isNotBlank()) {
                        Text(stringResource(R.string.metadata_match_search))
                    }
                }
                when (state) {
                    TitleViewModel.MetadataSearchState.Idle -> Unit
                    TitleViewModel.MetadataSearchState.Loading -> Box(
                        Modifier.fillMaxWidth().padding(Dimens.l),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    is TitleViewModel.MetadataSearchState.Ready -> {
                        if (state.results.isEmpty()) {
                            Text(stringResource(R.string.metadata_match_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                                items(state.results, key = { it.provider + it.id }) { item ->
                                    Surface(
                                        onClick = { onPick(item) },
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            Modifier.padding(Dimens.s),
                                            horizontalArrangement = Arrangement.spacedBy(Dimens.s),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            CoverImage(item.coverUrl, item.title, Modifier.width(48.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    item.title,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.titleSmall,
                                                )
                                                Text(
                                                    item.subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.close)) } },
    )
}
