package app.yomu.feature.catalog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomu.core.ui.components.errorMessage
import app.yomu.core.ui.components.topLevelContentInsets
import app.yomu.core.ui.theme.Dimens
import app.yomu.source.ProbeResult
import app.yomu.core.ui.R as CoreR

/**
 * Adding a connection by hand: what is being added, how, the address, a real check of it, and one save.
 *
 * The wizard exists because the addresses people need are not guessable — an OPDS catalogue, a podcast feed, a folder
 * of files. It explains the difference between a connection and a one-time import at the step where it matters, and it
 * never saves an address that was not checked.
 */
@Composable
fun SourceConnectionScreen(
    viewModel: SourceConnectionViewModel,
    onBack: () -> Unit,
    onOpenExtensions: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var signedIn by rememberSaveable { mutableStateOf(false) }
    // The password is never written to the saved state: a rotation clears it rather than keeping a secret in a Bundle.
    var showPassword by remember { mutableStateOf(false) }

    // The same document pickers the library's import sheet uses; the files go to the same import service.
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> viewModel.importFiles(uris) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(viewModel::importFolder) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { if (it is SourceConnectionEvent.Saved) onBack() }
    }

    Scaffold(
        contentWindowInsets = topLevelContentInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.connection_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (state.step is ConnectionStep.Content) onBack() else viewModel.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(CoreR.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Dimens.l),
            verticalArrangement = Arrangement.spacedBy(Dimens.m),
        ) {
            when (val step = state.step) {
                ConnectionStep.Content -> {
                    Text(stringResource(R.string.connection_what), style = MaterialTheme.typography.titleLarge)
                    ChoiceCard(stringResource(R.string.connection_content_books), stringResource(R.string.connection_content_books_hint), Icons.AutoMirrored.Filled.MenuBook) {
                        viewModel.chooseContent(ConnectionContent.BOOKS)
                    }
                    ChoiceCard(stringResource(R.string.connection_content_comics), stringResource(R.string.connection_content_comics_hint), Icons.Default.Image) {
                        viewModel.chooseContent(ConnectionContent.COMICS)
                    }
                    ChoiceCard(stringResource(R.string.connection_content_audio), stringResource(R.string.connection_content_audio_hint), Icons.Default.Headphones) {
                        viewModel.chooseContent(ConnectionContent.AUDIO)
                    }
                    ChoiceCard(stringResource(R.string.connection_content_files), stringResource(R.string.connection_content_files_hint), Icons.Default.FolderOpen) {
                        viewModel.chooseContent(ConnectionContent.FILES)
                    }
                }

                ConnectionStep.Method -> {
                    Text(stringResource(R.string.connection_how), style = MaterialTheme.typography.titleLarge)
                    SourceConnectionFlow.methodsFor(state.content).forEach { method ->
                        ChoiceCard(method.title(), method.hint(), method.icon()) { viewModel.chooseMethod(method) }
                    }
                }

                is ConnectionStep.Address -> AddressStep(
                    state = state,
                    signedIn = signedIn || step.method == ConnectionMethod.OPDS && state.username.isNotBlank(),
                    showPassword = showPassword,
                    onToggleSignIn = { signedIn = !signedIn; if (!signedIn) viewModel.editAddress(username = "", password = "") },
                    onTogglePassword = { showPassword = !showPassword },
                    onAddress = viewModel::editAddress,
                    onCheck = viewModel::check,
                )

                is ConnectionStep.Probing -> Busy(stringResource(R.string.connection_checking), step.address)

                is ConnectionStep.Preview -> {
                    Text(stringResource(R.string.connection_preview_title), style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(Dimens.s))
                        Column(Modifier.weight(1f)) {
                            Text(
                                state.checkedTitle?.takeIf { it.isNotBlank() } ?: SourceConnectionFlow.suggestedName(state),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                when (step.result.kind) {
                                    app.yomu.source.ConnectionKind.OPDS -> stringResource(R.string.connection_kind_opds)
                                    app.yomu.source.ConnectionKind.RSS -> stringResource(R.string.connection_kind_rss)
                                    app.yomu.source.ConnectionKind.AUDIO -> stringResource(R.string.connection_kind_audio)
                                    else -> stringResource(R.string.connection_kind_page)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (step.result.sampleItems > 0) {
                                Text(
                                    pluralStringResource(R.plurals.connection_samples, step.result.sampleItems, step.result.sampleItems),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(state.checkedAddress, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(stringResource(R.string.connection_preview_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touch)) {
                        Text(stringResource(R.string.connection_add))
                    }
                }

                ConnectionStep.Saving -> Busy(stringResource(R.string.connection_saving), state.checkedAddress)

                is ConnectionStep.Failed -> {
                    Text(stringResource(R.string.connection_failed), style = MaterialTheme.typography.titleLarge)
                    Text(failureText(step, context), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = viewModel::back, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touch)) {
                        Text(stringResource(R.string.connection_retry))
                    }
                    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(CoreR.string.close)) }
                }

                ConnectionStep.Done -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(Dimens.m))
                        Text(stringResource(R.string.connection_done_title), style = MaterialTheme.typography.titleLarge)
                    }
                    Text(stringResource(R.string.connection_done_text, SourceConnectionFlow.suggestedName(state)))
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touch)) {
                        Text(stringResource(CoreR.string.done))
                    }
                }

                is ConnectionStep.HandOff -> HandOffStep(
                    method = step.method,
                    onOpenExtensions = onOpenExtensions,
                    onImportFiles = { filePicker.launch(arrayOf("*/*")) },
                    onImportFolder = { folderPicker.launch(null) },
                    onUseAudioLink = { viewModel.chooseMethod(ConnectionMethod.AUDIO_LINK) },
                    onBack = viewModel::back,
                )

                ConnectionStep.Importing -> Busy(stringResource(R.string.connection_importing), stringResource(R.string.connection_importing_hint))

                is ConnectionStep.Imported -> {
                    Text(stringResource(R.string.connection_imported_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.connection_imported_text, step.added, step.duplicates, step.failed),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touch)) {
                        Text(stringResource(CoreR.string.done))
                    }
                    TextButton(onClick = viewModel::back, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.connection_import_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddressStep(
    state: SourceConnectionState,
    signedIn: Boolean,
    showPassword: Boolean,
    onToggleSignIn: () -> Unit,
    onTogglePassword: () -> Unit,
    onAddress: (String?, String?, String?) -> Unit,
    onCheck: () -> Unit,
) {
    val method = state.method ?: ConnectionMethod.OPDS
    val booking = method == ConnectionMethod.OPDS
    val feed = method == ConnectionMethod.RSS
    Text(
        stringResource(
            when {
                booking -> R.string.connection_address_title
                feed -> R.string.connection_feed_title
                else -> R.string.connection_audio_title
            },
        ),
        style = MaterialTheme.typography.titleLarge,
    )
    Text(
        stringResource(
            when {
                booking -> R.string.connection_help_opds
                feed -> R.string.connection_help_feed
                else -> R.string.connection_help_audio
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        state.address,
        { onAddress(it, null, null) },
        label = {
            Text(
                stringResource(
                    when {
                        booking -> R.string.opds_url
                        feed -> R.string.connection_feed_url
                        else -> R.string.connection_audio_url
                    },
                ),
            )
        },
        placeholder = {
            Text(
                stringResource(
                    when {
                        booking -> R.string.opds_url_placeholder
                        feed -> R.string.connection_feed_placeholder
                        else -> R.string.connection_audio_placeholder
                    },
                ),
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
    if (booking) {
        TextButton(onClick = onToggleSignIn) { Text(stringResource(R.string.connection_signin)) }
        if (signedIn) {
            OutlinedTextField(
                state.username, { onAddress(null, it, null) },
                label = { Text(stringResource(R.string.opds_user)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                state.password, { onAddress(null, null, it) },
                label = { Text(stringResource(R.string.opds_password)) },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailingIcon = {
                    IconButton(onClick = onTogglePassword) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            stringResource(if (showPassword) R.string.opds_hide_password else R.string.opds_show_password),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Button(
        onClick = onCheck,
        enabled = state.address.isNotBlank(),
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touch),
    ) {
        Icon(Icons.Default.Search, null, Modifier.size(18.dp))
        Text(stringResource(R.string.connection_check), Modifier.padding(start = Dimens.s))
    }
}

@Composable
private fun HandOffStep(
    method: ConnectionMethod,
    onOpenExtensions: () -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onUseAudioLink: () -> Unit,
    onBack: () -> Unit,
) {
    Text(method.title(), style = MaterialTheme.typography.titleLarge)
    Text(method.handOffText(), style = MaterialTheme.typography.bodyMedium)
    when (method) {
        ConnectionMethod.EXTENSION -> Button(onClick = onOpenExtensions, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touch)) {
            Text(stringResource(R.string.connection_open_extensions))
        }
        ConnectionMethod.FILES -> Button(onClick = onImportFiles, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touch)) {
            Text(stringResource(R.string.connection_pick_files))
        }
        ConnectionMethod.FOLDER -> Button(onClick = onImportFolder, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touch)) {
            Text(stringResource(R.string.connection_pick_folder))
        }
        // A feed cannot be picked as a file: the one-time audio link is the working path until subscriptions exist.
        ConnectionMethod.RSS -> Button(onClick = onUseAudioLink, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touch)) {
            Text(stringResource(R.string.connection_method_audio))
        }
        else -> Unit
    }
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connection_choose_another)) }
}

@Composable
private fun ChoiceCard(title: String, hint: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Dimens.l).heightIn(min = Dimens.touch - Dimens.l), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(Dimens.l))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Busy(text: String, detail: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = Dimens.xxl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.s)) {
            CircularProgressIndicator()
            Text(text, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun failureText(step: ConnectionStep.Failed, context: android.content.Context): String = when (val reason = step.reason) {
    is ConnectionFailure.Check -> stringResource(
        when (val result = reason.result) {
            is ProbeResult.InvalidUrl -> R.string.opds_url_invalid
            else -> result.message()
        },
    )
    is ConnectionFailure.Save -> errorMessage(context, reason.error)
}

private fun ConnectionMethod.icon(): ImageVector = when (this) {
    ConnectionMethod.OPDS -> Icons.Default.Link
    ConnectionMethod.AUDIO_LINK -> Icons.Default.AudioFile
    ConnectionMethod.EXTENSION -> Icons.Default.Storage
    ConnectionMethod.RSS -> Icons.Default.RssFeed
    ConnectionMethod.FILES -> Icons.Default.Download
    ConnectionMethod.FOLDER -> Icons.Default.FolderOpen
}

@Composable
private fun ConnectionMethod.title(): String = stringResource(
    when (this) {
        ConnectionMethod.OPDS -> R.string.connection_method_opds
        ConnectionMethod.AUDIO_LINK -> R.string.connection_method_audio
        ConnectionMethod.EXTENSION -> R.string.connection_method_extension
        ConnectionMethod.RSS -> R.string.connection_method_rss
        ConnectionMethod.FILES -> R.string.connection_method_files
        ConnectionMethod.FOLDER -> R.string.connection_method_folder
    },
)

@Composable
private fun ConnectionMethod.hint(): String = stringResource(
    when (this) {
        ConnectionMethod.OPDS -> R.string.connection_method_opds_hint
        ConnectionMethod.AUDIO_LINK -> R.string.connection_method_audio_hint
        ConnectionMethod.EXTENSION -> R.string.connection_method_extension_hint
        ConnectionMethod.RSS -> R.string.connection_method_rss_hint
        ConnectionMethod.FILES -> R.string.connection_method_files_hint
        ConnectionMethod.FOLDER -> R.string.connection_method_folder_hint
    },
)

@Composable
private fun ConnectionMethod.handOffText(): String = stringResource(
    when (this) {
        ConnectionMethod.EXTENSION -> R.string.connection_handoff_extension
        ConnectionMethod.RSS -> R.string.connection_handoff_rss
        ConnectionMethod.FILES -> R.string.connection_handoff_files
        ConnectionMethod.FOLDER -> R.string.connection_handoff_folder
        else -> R.string.connection_handoff_generic
    },
)
