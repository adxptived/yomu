package app.yomu.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.yomu.core.ui.theme.Dimens
import app.yomu.core.ui.R as CoreR

/** Five stars for the reader's own score; the icon row is the whole control. */
@Composable
fun RatingDialog(title: String, current: Int, onRate: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.library_rating_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = Dimens.m),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (star in 1..5) {
                        IconButton(onClick = { onRate(star) }) {
                            Icon(
                                if (star <= current) Icons.Filled.Star else Icons.Filled.StarOutline,
                                stringResource(R.string.library_rate_n, star),
                                Modifier.size(32.dp),
                                tint = if (star <= current) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row {
                if (current > 0) {
                    TextButton(onClick = { onRate(0) }) { Text(stringResource(R.string.library_rating_clear)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.close)) }
            }
        },
    )
}
