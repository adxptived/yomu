package app.yomu.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomu.core.ui.components.PreferenceChoice
import app.yomu.core.ui.components.PreferenceItem
import app.yomu.core.ui.theme.Dimens
import app.yomu.data.backup.SyncManager
import app.yomu.domain.sync.SyncCategory
import app.yomu.domain.sync.SyncFailure
import app.yomu.domain.sync.SyncStep
import app.yomu.domain.sync.SyncWizard
import app.yomu.domain.sync.classifySyncFailure
import app.yomu.core.ui.R as CoreR

/**
 * B03: the setup wizard is a real stepper over the pure [SyncWizard] state machine. Each step writes only the setting
 * it owns, PROBE calls the real connection check, and nothing is enabled before the confirmed preview is finished.
 */
@Composable
internal fun SyncWizardDialog(vm: SettingsViewModel) {
    val state by vm.syncWizard.collectAsStateWithLifecycle()
    val current = state ?: return
    val busy by vm.busy.collectAsStateWithLifecycle()
    val s = vm.settings

    AlertDialog(
        onDismissRequest = vm::closeSyncWizard,
        title = { Text(stringResource(R.string.set_sync_wizard_title)) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.s),
            ) {
                Text(
                    stringResource(R.string.set_sync_wizard_progress, current.step.ordinal + 1, SyncStep.entries.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(current.step.label()), style = MaterialTheme.typography.titleMedium)
                when (current.step) {
                    SyncStep.BACKEND -> {
                        Hint(stringResource(R.string.set_sync_wizard_backend_hint))
                        PreferenceChoice(
                            stringResource(R.string.set_sync_method),
                            listOf(
                                SyncManager.FOLDER to stringResource(R.string.set_sync_target_folder),
                                SyncManager.WEBDAV to stringResource(R.string.set_sync_target_webdav),
                                SyncManager.GITHUB to stringResource(R.string.set_sync_target_github),
                            ),
                            current.backend ?: SyncManager.FOLDER,
                            vm::wizardSetBackend,
                        )
                    }
                    SyncStep.INPUT -> {
                        Hint(stringResource(R.string.set_sync_wizard_input_hint))
                        // Any confirmed credential edit drops a probe done with the old values. The "Details are
                        // entered" checkbox is unchecked by the state machine and stays the way to re-confirm.
                        val onCredentialChanged = { vm.wizardInputReady(false) }
                        when (current.backend) {
                            SyncManager.WEBDAV -> {
                                TextPref(vm, s.webdavUrl, R.string.set_webdav_url, summaryWhenEmpty = R.string.set_webdav_url_hint, onChanged = onCredentialChanged)
                                TextPref(vm, s.webdavUser, R.string.set_webdav_user, onChanged = onCredentialChanged)
                                TextPref(vm, s.webdavPassword, R.string.set_webdav_password, secret = true, summaryWhenEmpty = R.string.set_webdav_password_sum, onChanged = onCredentialChanged)
                            }
                            SyncManager.GITHUB -> {
                                TextPref(vm, s.githubToken, R.string.set_github_token, secret = true, summaryWhenEmpty = R.string.set_github_token_sum, onChanged = onCredentialChanged)
                                TextPref(vm, s.githubRepo, R.string.set_github_repo, summaryWhenEmpty = R.string.set_github_repo_hint, onChanged = onCredentialChanged)
                            }
                            else -> {
                                val folder by s.syncFolder.state()
                                val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                                    uri?.let {
                                        vm.setSyncFolder(it)
                                        onCredentialChanged()
                                    }
                                }
                                PreferenceItem(
                                    stringResource(R.string.set_sync_folder),
                                    folder.takeIf { it.isNotBlank() }?.let { android.net.Uri.decode(it.substringAfterLast("/tree/")) }
                                        ?: stringResource(R.string.set_not_set),
                                    Icons.Outlined.CloudSync,
                                    onClick = { pick.launch(null) },
                                )
                            }
                        }
                        WizardCheckRow(
                            checked = current.inputReady,
                            label = stringResource(R.string.set_sync_wizard_input_ready),
                            onClick = { vm.wizardInputReady(!current.inputReady) },
                        )
                    }
                    SyncStep.PROBE -> {
                        Hint(stringResource(R.string.set_sync_wizard_probe_hint))
                        PreferenceItem(
                            stringResource(R.string.set_sync_wizard_check),
                            stringResource(R.string.set_sync_check),
                            Icons.Outlined.CloudSync,
                            enabled = !busy,
                            onClick = vm::probeSyncWizard,
                        )
                        current.failure?.let { SyncRecoveryCard(it) }
                        if (current.probePassed) {
                            Text(
                                stringResource(R.string.set_sync_check_ok),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    SyncStep.CATEGORIES -> {
                        Hint(stringResource(R.string.set_sync_wizard_categories_hint))
                        SyncCategory.entries.forEach { category ->
                            WizardCheckRow(
                                checked = category in current.categories,
                                label = stringResource(category.label()),
                                onClick = { vm.wizardToggleCategory(category) },
                            )
                        }
                    }
                    SyncStep.SCHEDULE -> {
                        Hint(stringResource(R.string.set_sync_wizard_schedule_hint))
                        listOf(0, 1, 3, 6, 12, 24).forEach { hours ->
                            WizardRadioRow(
                                selected = current.scheduleHours == hours,
                                label = if (hours == 0) stringResource(R.string.set_off)
                                else pluralStringResource(R.plurals.set_every_hours, hours, hours),
                                onClick = { vm.wizardSchedule(hours) },
                            )
                        }
                    }
                    SyncStep.PREVIEW -> {
                        val backend = when (current.backend) {
                            SyncManager.WEBDAV -> stringResource(R.string.set_sync_target_webdav)
                            SyncManager.GITHUB -> stringResource(R.string.set_sync_target_github)
                            else -> stringResource(R.string.set_sync_target_folder)
                        }
                        val interval = if (current.scheduleHours == 0) stringResource(R.string.set_off)
                        else pluralStringResource(R.plurals.set_every_hours, current.scheduleHours, current.scheduleHours)
                        Text(
                            stringResource(R.string.set_sync_wizard_preview, backend, current.categories.size, interval),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        WizardCheckRow(
                            checked = current.previewConfirmed,
                            label = stringResource(R.string.set_sync_wizard_preview_confirm),
                            onClick = { vm.wizardConfirmPreview(!current.previewConfirmed) },
                        )
                    }
                    SyncStep.READY -> Text(
                        stringResource(R.string.set_sync_wizard_ready),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            if (current.step == SyncStep.READY) {
                TextButton(onClick = { vm.wizardFinish() }) { Text(stringResource(R.string.set_sync_wizard_finish)) }
            } else {
                TextButton(
                    enabled = SyncWizard.canAdvance(current) && !busy,
                    onClick = vm::wizardAdvance,
                ) { Text(stringResource(R.string.set_sync_wizard_next)) }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { if (current.step == SyncStep.BACKEND) vm.closeSyncWizard() else vm.wizardBack() },
            ) {
                Text(
                    if (current.step == SyncStep.BACKEND) stringResource(CoreR.string.cancel)
                    else stringResource(R.string.set_sync_wizard_back),
                )
            }
        },
    )
}

/** One status line plus the concrete recovery the last sync failure needs. */
@Composable
internal fun ColumnScope.SyncRecoveryBanner(vm: SettingsViewModel) {
    val summary by vm.syncSummary.collectAsStateWithLifecycle()
    val error = summary.error ?: return
    val kind = classifySyncFailure(error)
    val recovery = SyncWizard.recovery(kind)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.xs),
    ) {
        Column(Modifier.padding(Dimens.m), verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
            Text(
                stringResource(R.string.set_sync_recovery_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                stringResource(kind.adviceLabel()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = { if (recovery.retryNow) vm.syncNow() else vm.openSyncWizard() }) {
                Text(
                    stringResource(if (recovery.retryNow) R.string.set_sync_recovery_retry else R.string.set_sync_recovery_open),
                )
            }
        }
    }
}

@Composable
private fun SyncRecoveryCard(failure: SyncFailure) {
    val recovery = SyncWizard.recovery(failure)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.m), verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
            Text(
                stringResource(R.string.set_sync_recovery_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                stringResource(failure.adviceLabel()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                stringResource(if (recovery.retryNow) R.string.set_sync_recovery_retry_hint else R.string.set_sync_recovery_user_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun WizardCheckRow(checked: Boolean, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Dimens.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, Modifier.padding(start = Dimens.s), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WizardRadioRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Dimens.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = Dimens.s), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun SyncStep.label(): Int = when (this) {
    SyncStep.BACKEND -> R.string.set_sync_step_backend
    SyncStep.INPUT -> R.string.set_sync_step_input
    SyncStep.PROBE -> R.string.set_sync_step_probe
    SyncStep.CATEGORIES -> R.string.set_sync_step_categories
    SyncStep.SCHEDULE -> R.string.set_sync_step_schedule
    SyncStep.PREVIEW -> R.string.set_sync_step_preview
    SyncStep.READY -> R.string.set_sync_step_ready
}

private fun SyncCategory.label(): Int = when (this) {
    SyncCategory.PROGRESS -> R.string.set_sync_progress
    SyncCategory.CATALOG -> R.string.set_sync_catalog
    SyncCategory.ANNOTATIONS -> R.string.set_sync_annotations
    SyncCategory.BOOKMARKS -> R.string.set_sync_bookmarks
    SyncCategory.WORDS -> R.string.set_sync_words
    SyncCategory.FILES -> R.string.set_sync_book_files
    SyncCategory.GLOSSARY -> R.string.set_sync_glossary
}

private fun SyncFailure.adviceLabel(): Int = when (this) {
    SyncFailure.AUTH -> R.string.set_sync_recovery_auth
    SyncFailure.NETWORK -> R.string.set_sync_recovery_network
    SyncFailure.STORAGE -> R.string.set_sync_recovery_storage
    SyncFailure.BATTERY -> R.string.set_sync_recovery_battery
    SyncFailure.OTHER -> R.string.set_sync_recovery_other
}
