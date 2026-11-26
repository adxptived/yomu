package app.yomu.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomu.R
import app.yomu.core.common.ScreenErrors
import app.yomu.core.datastore.AppSettings
import app.yomu.core.ui.theme.Accent
import app.yomu.core.ui.theme.ThemeMode
import app.yomu.data.backup.BackupManager
import app.yomu.data.backup.BackupPreview
import app.yomu.data.backup.RestoreResult
import app.yomu.data.library.ImportOutcome
import app.yomu.data.library.ImportService
import app.yomu.data.work.LibraryScanWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import app.yomu.core.ui.R as CoreR
import app.yomu.feature.settings.R as SettingsR

private val LANGUAGES = listOf("en", "ru", "uk", "de", "fr", "es", "it", "pt", "pl", "tr", "ja", "zh", "ko")

/** The task chosen on the first screen: it only picks the shortest path, everything stays available afterwards. */
private enum class StartGoal { READ, MANGA, AUDIO, LANGUAGE }

/**
 * First start: what the user wants to do, one way to get the first book, and the translation language. Every step can
 * be skipped, and nothing is requested outside the chosen path: no notification permission, no account, no sources.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(settings: AppSettings, onDone: (openCatalog: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val imports: ImportService = koinInject()
    var goal by rememberSaveable { mutableStateOf(StartGoal.READ) }
    var folderName by rememberSaveable { mutableStateOf<String?>(null) }
    var importedName by rememberSaveable { mutableStateOf<String?>(null) }
    var importFailed by rememberSaveable { mutableStateOf(false) }
    var language by rememberSaveable { mutableStateOf(Locale.getDefault().language.takeIf { it in LANGUAGES } ?: "en") }
    val pages = 3
    val pager = rememberPagerState { pages }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.recoverCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        folderName = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment
        scope.launch(ScreenErrors) {
            settings.scanFolders.update { it + uri.toString() }
            LibraryScanWorker.runNow(context)
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(ScreenErrors) {
            val outcome = imports.import(listOf(uri)).firstOrNull()
            importedName = outcome?.takeIf { it !is ImportOutcome.Failed }?.name
            importFailed = outcome is ImportOutcome.Failed
        }
    }

    fun finish(openCatalog: Boolean = false) {
        scope.launch(ScreenErrors) {
            settings.targetLang.set(language)
            onDone(openCatalog)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { finish() }) { Text(stringResource(R.string.onboarding_skip)) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.widthIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (page) {
                        0 -> {
                            PageHeader(
                                Icons.AutoMirrored.Outlined.MenuBook,
                                stringResource(R.string.onboarding_welcome_title),
                                stringResource(R.string.onboarding_goal_title),
                            )
                            OnboardingAppearance(settings)
                            Text(
                                stringResource(R.string.onboarding_goal_text),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            GoalOption(Icons.AutoMirrored.Outlined.MenuBook, R.string.onboarding_goal_read, goal == StartGoal.READ) { goal = StartGoal.READ }
                            GoalOption(Icons.Outlined.PhotoLibrary, R.string.onboarding_goal_manga, goal == StartGoal.MANGA) { goal = StartGoal.MANGA }
                            GoalOption(Icons.Outlined.Headphones, R.string.onboarding_goal_audio, goal == StartGoal.AUDIO) { goal = StartGoal.AUDIO }
                            GoalOption(Icons.Outlined.Translate, R.string.onboarding_goal_language, goal == StartGoal.LANGUAGE) { goal = StartGoal.LANGUAGE }
                            Text(
                                stringResource(R.string.onboarding_goal_switch),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        1 -> {
                            PageHeader(
                                Icons.Outlined.Folder,
                                stringResource(
                                    when (goal) {
                                        StartGoal.AUDIO -> R.string.onboarding_audio_title
                                        StartGoal.MANGA -> R.string.onboarding_manga_title
                                        else -> R.string.onboarding_folder_title
                                    },
                                ),
                                stringResource(
                                    when (goal) {
                                        StartGoal.AUDIO -> R.string.onboarding_audio_text
                                        StartGoal.MANGA -> R.string.onboarding_manga_text
                                        else -> R.string.onboarding_folder_text
                                    },
                                ),
                            )
                            val added = importedName
                            val name = folderName
                            when {
                                added != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                    Text(stringResource(R.string.onboarding_file_added, added), style = MaterialTheme.typography.bodyLarge)
                                }
                                name != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                    Text(stringResource(R.string.onboarding_folder_chosen, name), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            if (importFailed) {
                                Text(
                                    stringResource(R.string.onboarding_file_failed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(onClick = { folderPicker.launch(null) }) { Text(stringResource(R.string.onboarding_folder_pick)) }
                                OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.onboarding_file_pick)) }
                            }
                            Text(
                                stringResource(R.string.onboarding_folder_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            OnboardingRestore()
                            TextButton(onClick = { finish(openCatalog = true) }) {
                                Text(stringResource(R.string.onboarding_open_catalog))
                            }
                        }
                        else -> {
                            PageHeader(Icons.Outlined.Translate, stringResource(R.string.onboarding_language_title), stringResource(R.string.onboarding_language_text))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LANGUAGES.forEach { code ->
                                    val locale = Locale.forLanguageTag(code)
                                    FilterChip(
                                        selected = language == code,
                                        onClick = { language = code },
                                        label = { Text(locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(pages) { i ->
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(if (pager.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            if (pager.currentPage > 0) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) { Text(stringResource(R.string.onboarding_back)) }
            }
            Spacer(Modifier.size(8.dp))
            val last = pager.currentPage == pages - 1
            // The translation language is a step of its own only for the language goal; everyone else starts from the
            // screen with the first book.
            val next = when {
                !last && pager.currentPage == 1 && goal != StartGoal.LANGUAGE -> null
                last -> null
                else -> pager.currentPage + 1
            }
            Button(onClick = { if (next != null) scope.launch { pager.animateScrollToPage(next) } else finish() }) {
                Text(
                    stringResource(
                        when {
                            pager.currentPage == 0 -> R.string.onboarding_next
                            pager.currentPage == 1 && goal != StartGoal.LANGUAGE -> R.string.onboarding_start
                            last -> R.string.onboarding_start
                            else -> R.string.onboarding_next
                        },
                    ),
                )
            }
        }
    }
}

/**
 * Moving from another phone: a backup brings back the library, progress, notes and settings. The archive is previewed
 * before anything is written, and the step can be skipped like every other one.
 */
@Composable
private fun OnboardingRestore() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backup: BackupManager = koinInject()
    var selected by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var restored by remember { mutableStateOf<RestoreResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    fun apply(uri: Uri) {
        preview = null
        scope.launch(ScreenErrors) {
            busy = true
            failed = false
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { backup.restore(it) } ?: error("Cannot read the file")
                }
            }.onSuccess { result ->
                restored = result
                LibraryScanWorker.runNow(context)
            }.onFailure {
                failed = true
            }
            busy = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(ScreenErrors) {
            busy = true
            failed = false
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { backup.preview(it) } ?: error("Cannot read the file")
                }
            }.onSuccess { manifest ->
                selected = uri
                preview = manifest
            }.onFailure {
                failed = true
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.onboarding_restore_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        val done = restored
        if (done != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(SettingsR.string.set_restore_done, done.rows, done.files), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy) {
                Text(stringResource(SettingsR.string.set_backup_restore))
            }
        }
        if (failed) {
            Text(
                stringResource(R.string.onboarding_restore_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }

    preview?.let { p ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(stringResource(SettingsR.string.set_restore_preview_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (p.createdAt > 0) {
                        Text(
                            stringResource(
                                SettingsR.string.set_restore_preview_date,
                                SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(p.createdAt)),
                            ),
                        )
                    }
                    Text(stringResource(SettingsR.string.set_restore_preview_counts, p.books, p.highlights, p.notes, p.bookmarks, p.words))
                    if (p.files > 0) {
                        Text(stringResource(SettingsR.string.set_restore_preview_files, p.files, android.text.format.Formatter.formatShortFileSize(context, p.bytes)))
                    }
                    Text(stringResource(SettingsR.string.set_restore_preview_merge), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { selected?.let(::apply) }) { Text(stringResource(SettingsR.string.set_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { preview = null }) { Text(stringResource(CoreR.string.cancel)) }
            },
        )
    }
}

@Composable
private fun OnboardingAppearance(settings: AppSettings) {
    val scope = rememberCoroutineScope()
    val mode by settings.themeMode.flow.collectAsStateWithLifecycle(initialValue = settings.themeMode.default)
    val accent by settings.accent.flow.collectAsStateWithLifecycle(initialValue = settings.accent.default)
    val amoled by settings.amoled.flow.collectAsStateWithLifecycle(initialValue = settings.amoled.default)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(SettingsR.string.set_appearance), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { choice ->
                val label = when (choice) {
                    ThemeMode.SYSTEM -> SettingsR.string.set_theme_system
                    ThemeMode.LIGHT -> SettingsR.string.set_theme_light
                    ThemeMode.DARK -> SettingsR.string.set_theme_dark
                }
                FilterChip(
                    selected = mode == choice.name,
                    onClick = { scope.launch(ScreenErrors) { settings.themeMode.set(choice.name) } },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Accent.YOMU to R.string.onboarding_yomu_accent, Accent.CLASSIC to R.string.onboarding_classic_accent)
                .forEach { (choice, label) ->
                    FilterChip(
                        selected = accent == choice.name,
                        onClick = { scope.launch(ScreenErrors) { settings.accent.set(choice.name) } },
                        label = { Text(stringResource(label)) },
                    )
                }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val label = stringResource(SettingsR.string.set_amoled)
            Text(label, modifier = Modifier.weight(1f))
            Switch(
                checked = amoled,
                onCheckedChange = { checked -> scope.launch(ScreenErrors) { settings.amoled.set(checked) } },
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}

@Composable
private fun GoalOption(icon: ImageVector, label: Int, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PageHeader(icon: ImageVector, title: String, text: String) {
    Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
