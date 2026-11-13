package app.yomu.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yomu.core.ui.theme.Dimens

/**
 * A group of settings behind a header: the header says what the group is about and, while collapsed, what is inside
 * ([summary]); the body expands in place. Keeps long reader sheets readable and puts the rarely changed options away.
 */
@Composable
fun CollapsibleSection(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    initiallyExpanded: Boolean = false,
    /** 0 in a list that already has its own horizontal padding (the profile overview). */
    horizontalPadding: Dp = Dimens.m,
    /** A settings search: a group whose title and summary do not contain it is hidden, a matching one opens. */
    query: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    val needle = query.trim()
    if (needle.isNotEmpty() && !"$title ${summary.orEmpty()}".contains(needle, ignoreCase = true)) return
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded || needle.isNotEmpty()) }
    LaunchedEffect(needle) { if (needle.isNotEmpty()) expanded = true }
    val rotation by animateMotionFloat(if (expanded) 180f else 0f)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = Dimens.xs),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = Dimens.touch + 8.dp)
                    .toggleable(value = expanded, role = Role.Switch, onValueChange = { expanded = it })
                    .padding(horizontal = Dimens.l, vertical = Dimens.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.m),
            ) {
                if (icon != null) PrefIcon(icon)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (!expanded && !summary.isNullOrBlank()) {
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ExpandVisibility(expanded) {
                Column(Modifier.padding(bottom = Dimens.s), content = content)
            }
        }
    }
}
