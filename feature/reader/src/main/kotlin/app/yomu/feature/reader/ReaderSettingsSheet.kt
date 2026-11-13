package app.yomu.feature.reader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.Margin
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomu.core.common.Files
import app.yomu.core.ui.components.CollapsibleSection
import app.yomu.core.ui.components.OptionTiles
import app.yomu.core.ui.components.PreferenceChoice
import app.yomu.core.ui.components.PreferenceSlider
import app.yomu.core.ui.components.SearchField
import app.yomu.core.ui.components.PreferenceSwitch
import app.yomu.core.ui.components.TileOption
import app.yomu.core.ui.components.YomuSheet
import app.yomu.core.ui.components.YomuTab
import app.yomu.core.ui.theme.Dimens
import app.yomu.domain.model.CommonReaderSettings
import app.yomu.domain.model.MangaSettings
import app.yomu.domain.model.PageTurnAnimation
import app.yomu.domain.model.PdfMode
import app.yomu.domain.model.PdfSettings
import app.yomu.domain.model.ReaderOrientation
import app.yomu.domain.model.ReaderTheme
import app.yomu.domain.model.ScreenTimeout
import app.yomu.domain.model.TapZoneLayout
import app.yomu.domain.model.TextAlignment
import app.yomu.domain.model.TextPreset
import app.yomu.domain.model.TextReaderSettings
import app.yomu.domain.model.WordTapGesture
import app.yomu.feature.reader.epub.ReaderCss
import app.yomu.feature.reader.manga.MangaControlSettings
import app.yomu.feature.reader.manga.MangaPageSettings
import app.yomu.feature.reader.manga.TapZonesDiagram
import app.yomu.feature.reader.manga.zoneLabel
import java.io.File
import app.yomu.core.ui.R as CoreR

/** Reader settings for the active engine, with a "this book only" switch. */
@Composable
fun ReaderSettingsSheet(
    viewModel: ReaderViewModel,
    content: ReaderContent,
    text: TextReaderSettings,
    manga: MangaSettings,
    pdf: PdfSettings,
    common: CommonReaderSettings,
    perBook: Boolean,
    wordTap: WordTapGesture,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    YomuSheet(onDismiss = onDismiss) {
        if (content is ReaderContent.Text) QuickTextBar(text, viewModel)
        SearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(R.string.rs_search_settings),
            modifier = Modifier.padding(horizontal = Dimens.l, vertical = Dimens.xs),
        )
        PrimaryTabRow(selectedTabIndex = tab) {
            YomuTab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(if (content is ReaderContent.Text) R.string.rs_tab_text else if (content is ReaderContent.Pdf) R.string.rs_tab_pdf else R.string.rs_tab_manga)) })
            YomuTab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.rs_tab_display)) })
            YomuTab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.rs_tab_controls)) })
        }
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            if (viewModel.bookId > 0 || viewModel.isOnline) {
                PreferenceSwitch(stringResource(R.string.rs_per_book), perBook, viewModel::setPerBook, summary = stringResource(R.string.rs_per_book_summary))
            }
            when (tab) {
                0 -> when (content) {
                    is ReaderContent.Text -> {
                        if (query.isBlank()) PresetsRow(text, presets, viewModel)
                        TextSettings(text, viewModel, query)
                    }
                    is ReaderContent.Pdf -> PdfSettingsContent(pdf, viewModel, query)
                    is ReaderContent.Manga -> MangaSettingsContent(manga, viewModel, query)
                }
                1 -> {
                    if (content is ReaderContent.Text) ThemeSection(text, viewModel, query)
                    DisplaySettings(common, viewModel, showFooter = content !is ReaderContent.Manga, query = query)
                }
                else -> ControlSettings(content, common, manga, wordTap, viewModel, query)
            }
            Spacer(Modifier.height(Dimens.l))
        }
    }
}

/**
 * Text settings in collapsible groups: the font, the distances between letters, lines and paragraphs, the margins,
 * the book's own styling and page turning. Only the two most used groups start open.
 */
@Composable
private fun TextSettings(s: TextReaderSettings, vm: ReaderViewModel, query: String) {
    val context = LocalContext.current
    val fontsDir = remember { File(context.filesDir, "fonts").apply { mkdirs() } }
    var customFonts by remember { mutableStateOf(fontsDir.listFiles()?.map { it.name }?.sorted().orEmpty()) }
    val importFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val name = Files.sanitize(Files.displayName(context, uri) ?: "font.ttf")
                Files.copy(context, uri, File(fontsDir, name))
                customFonts = fontsDir.listFiles()?.map { it.name }?.sorted().orEmpty()
                vm.updateText { it.copy(fontFamily = name) }
            }
        }
    }

    CollapsibleSection(
        title = stringResource(R.string.rs_group_font),
        query = query,
        summary = stringResource(R.string.rs_group_font_summary),
        icon = Icons.Filled.TextFields,
        initiallyExpanded = true,
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = Dimens.l, end = Dimens.l, top = Dimens.s), horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
            (ReaderCss.BUILT_IN_FONTS + customFonts).forEach { f ->
                FilterChip(selected = s.fontFamily == f, onClick = { vm.updateText { it.copy(fontFamily = f) } }, label = { Text(fontLabel(f)) })
            }
            FilterChip(
                selected = false,
                onClick = { importFont.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream", "*/*")) },
                label = { Text(stringResource(R.string.rs_import_font)) },
                leadingIcon = { Icon(Icons.Default.Add, null) },
            )
        }
        PreferenceSlider(
            stringResource(R.string.rs_font_size),
            s.fontSizePercent.toFloat(),
            50f..250f,
            { v -> vm.updateText { it.copy(fontSizePercent = v.toInt()) } },
            { "${it.toInt()}%" },
            steps = 39,
            icon = Icons.Default.FormatSize,
            summary = stringResource(R.string.rs_font_size_summary),
        )
        PreferenceSlider(
            stringResource(R.string.rs_weight),
            s.fontWeight.toFloat(),
            300f..700f,
            { v -> vm.updateText { it.copy(fontWeight = (v / 100).toInt() * 100) } },
            { it.toInt().toString() },
            steps = 3,
            summary = stringResource(R.string.rs_weight_summary),
        )
    }

    CollapsibleSection(
        title = stringResource(R.string.rs_group_spacing),
        query = query,
        summary = stringResource(R.string.rs_group_spacing_summary),
        icon = Icons.Outlined.FormatLineSpacing,
    ) {
        PreferenceSlider(
            stringResource(R.string.rs_line_height), s.lineHeight, 1f..2.5f,
            { v -> vm.updateText { it.copy(lineHeight = (v * 20).toInt() / 20f) } },
            { "%.2f".format(it) }, steps = 29, summary = stringResource(R.string.rs_line_height_summary),
        )
        PreferenceSlider(
            stringResource(R.string.rs_letter_spacing), s.letterSpacing, 0f..0.2f,
            { v -> vm.updateText { it.copy(letterSpacing = Math.round(v * 100) / 100f) } },
            { "%.2f".format(it) }, steps = 19, summary = stringResource(R.string.rs_letter_spacing_summary),
        )
        PreferenceSlider(
            stringResource(R.string.rs_word_spacing), s.wordSpacing, 0f..0.5f,
            { v -> vm.updateText { it.copy(wordSpacing = Math.round(v * 50) / 50f) } },
            { "%.2f".format(it) }, steps = 24, summary = stringResource(R.string.rs_word_spacing_summary),
        )
        PreferenceSlider(
            stringResource(R.string.rs_paragraph), s.paragraphSpacing, 0f..2f,
            { v -> vm.updateText { it.copy(paragraphSpacing = (v * 10).toInt() / 10f) } },
            { "%.1f".format(it) }, steps = 19, summary = stringResource(R.string.rs_paragraph_summary),
        )
        PreferenceSlider(
            stringResource(R.string.rs_indent), s.indent, 0f..3f,
            { v -> vm.updateText { it.copy(indent = (v * 10).toInt() / 10f) } },
            { "%.1f".format(it) }, steps = 29, summary = stringResource(R.string.rs_indent_summary),
        )
        Text(
            stringResource(R.string.rs_align),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = Dimens.l, top = Dimens.s),
        )
        Text(
            stringResource(R.string.rs_align_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Dimens.l),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.xs)) {
            val opts = listOf(
                TextAlignment.START to R.string.rs_align_start,
                TextAlignment.JUSTIFY to R.string.rs_align_justify,
                TextAlignment.CENTER to R.string.rs_align_center,
            )
            opts.forEachIndexed { i, (a, label) ->
                SegmentedButton(selected = s.align == a, onClick = { vm.updateText { it.copy(align = a) } }, shape = SegmentedButtonDefaults.itemShape(i, opts.size)) { Text(stringResource(label)) }
            }
        }
    }

    CollapsibleSection(
        title = stringResource(R.string.rs_group_margins),
        query = query,
        summary = stringResource(R.string.rs_group_margins_summary),
        icon = Icons.Outlined.Margin,
    ) {
        PreferenceSlider(
            stringResource(R.string.rs_margin_h), s.marginHorizontal.toFloat(), 0f..80f,
            { v -> vm.updateText { it.copy(marginHorizontal = v.toInt()) } },
            { "${it.toInt()}" }, steps = 15, summary = stringResource(R.string.rs_margin_h_summary),
        )
        PreferenceSlider(
            stringResource(R.string.rs_margin_v), s.marginVertical.toFloat(), 0f..96f,
            { v -> vm.updateText { it.copy(marginVertical = v.toInt()) } },
            { "${it.toInt()}" }, steps = 11, summary = stringResource(R.string.rs_margin_v_summary),
        )
    }

    CollapsibleSection(
        title = stringResource(R.string.rs_group_style),
        query = query,
        summary = stringResource(R.string.rs_group_style_summary),
        icon = Icons.AutoMirrored.Outlined.MenuBook,
    ) {
        PreferenceSwitch(
            stringResource(R.string.rs_hyphenation), s.hyphenation,
            { v -> vm.updateText { it.copy(hyphenation = v) } },
            summary = stringResource(R.string.rs_hyphenation_summary),
        )
        PreferenceSwitch(
            stringResource(R.string.rs_publisher), s.publisherStyles,
            { v -> vm.updateText { it.copy(publisherStyles = v) } },
            summary = stringResource(R.string.rs_publisher_summary),
        )
        PreferenceSwitch(
            stringResource(R.string.rs_vertical), s.verticalText,
            { v -> vm.updateText { it.copy(verticalText = v) } },
            summary = stringResource(R.string.rs_vertical_summary),
        )
        PreferenceSwitch(
            stringResource(R.string.rs_rtl), s.rtl,
            { v -> vm.updateText { it.copy(rtl = v) } },
            summary = stringResource(R.string.rs_rtl_summary),
        )
        PreferenceSwitch(
            stringResource(R.string.rs_saved_words), s.highlightSavedWords,
            { v -> vm.updateText { it.copy(highlightSavedWords = v) } },
            summary = stringResource(R.string.rs_saved_words_summary),
        )
    }

    CollapsibleSection(
        title = stringResource(R.string.rs_group_navigation),
        query = query,
        summary = stringResource(R.string.rs_group_navigation_summary),
        icon = Icons.Filled.SwapHoriz,
        initiallyExpanded = true,
    ) {
        PreferenceSwitch(
            stringResource(R.string.rs_paged), s.paged,
            { v -> vm.updateText { it.copy(paged = v) } },
            summary = stringResource(if (s.paged) R.string.rs_paged_on else R.string.rs_paged_off),
        )
        if (s.paged && !s.verticalText) {
            PreferenceSwitch(
                stringResource(R.string.rs_dual_landscape), s.dualPageLandscape,
                { v -> vm.updateText { it.copy(dualPageLandscape = v) } },
                summary = stringResource(R.string.rs_dual_landscape_summary),
            )
            PreferenceChoice(
                stringResource(R.string.rs_page_anim),
                listOf(
                    PageTurnAnimation.SLIDE to stringResource(R.string.rs_page_anim_slide),
                    PageTurnAnimation.FADE to stringResource(R.string.rs_page_anim_fade),
                    PageTurnAnimation.NONE to stringResource(R.string.rs_page_anim_none),
                ),
                s.pageAnimation,
                { v -> vm.updateText { it.copy(pageAnimation = v) } },
            )
        }
        PreferenceSwitch(
            stringResource(R.string.rs_chapter_buttons), s.chapterButtons,
            { v -> vm.updateText { it.copy(chapterButtons = v) } },
            summary = stringResource(R.string.rs_chapter_buttons_summary),
        )
        TextButton(onClick = { vm.resetTextLayout() }, modifier = Modifier.padding(start = Dimens.s, top = Dimens.s)) {
            Icon(Icons.Default.RestartAlt, null, Modifier.size(18.dp))
            Spacer(Modifier.width(Dimens.s))
            Text(stringResource(R.string.rs_reset_layout))
        }
    }
}

/** Paper and ink: the reading theme, the custom colours and the night theme with its schedule. */
@Composable
private fun ThemeSection(text: TextReaderSettings, vm: ReaderViewModel, query: String) {
    CollapsibleSection(
        title = stringResource(R.string.rs_group_theme),
        query = query,
        summary = stringResource(R.string.rs_group_theme_summary),
        icon = Icons.Outlined.Palette,
        initiallyExpanded = true,
    ) {
        ThemePicker(text, { t -> vm.updateText { it.copy(theme = t) } }, title = "")
        if (text.theme == ReaderTheme.CUSTOM) CustomColors(text, vm)
    }
    CollapsibleSection(
        title = stringResource(R.string.rs_group_night),
        query = query,
        summary = stringResource(R.string.rs_group_night_summary),
        icon = Icons.Outlined.DarkMode,
    ) {
        PreferenceSwitch(
            stringResource(R.string.rs_auto_night), text.autoNight,
            { v -> vm.updateText { it.copy(autoNight = v) } },
            summary = stringResource(R.string.rs_auto_night_summary),
        )
        if (text.autoNight) {
            PreferenceChoice(
                stringResource(R.string.rs_auto_night_mode),
                listOf(
                    app.yomu.domain.model.AutoNightMode.SYSTEM to stringResource(R.string.rs_auto_night_system),
                    app.yomu.domain.model.AutoNightMode.SCHEDULE to stringResource(R.string.rs_auto_night_schedule),
                ),
                text.autoNightMode,
                { v -> vm.updateText { it.copy(autoNightMode = v) } },
            )
            if (text.autoNightMode == app.yomu.domain.model.AutoNightMode.SCHEDULE) {
                PreferenceSlider(
                    stringResource(R.string.rs_night_from), text.nightFromHour.toFloat(), 0f..23f,
                    { v -> vm.updateText { it.copy(nightFromHour = v.toInt()) } },
                    { "%02d:00".format(it.toInt()) }, steps = 23,
                )
                PreferenceSlider(
                    stringResource(R.string.rs_night_to), text.nightToHour.toFloat(), 0f..23f,
                    { v -> vm.updateText { it.copy(nightToHour = v.toInt()) } },
                    { "%02d:00".format(it.toInt()) }, steps = 23,
                )
            }
            ThemePicker(
                text,
                { t -> vm.updateText { it.copy(nightTheme = t) } },
                themes = listOf(ReaderTheme.GRAY, ReaderTheme.DARK, ReaderTheme.BLACK),
                selected = text.nightTheme,
                title = stringResource(R.string.rs_night_theme),
            )
        }
    }
}

/** Font size and theme: the two things changed most often, always at hand above the tabs. */
@Composable
internal fun QuickTextBar(s: TextReaderSettings, vm: ReaderViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.xs), verticalAlignment = Alignment.CenterVertically) {
        val smaller = stringResource(R.string.rs_font_smaller)
        val larger = stringResource(R.string.rs_font_larger)
        FilledTonalIconButton(
            onClick = { vm.updateText { it.copy(fontSizePercent = (it.fontSizePercent - 10).coerceAtLeast(50)) } },
            enabled = s.fontSizePercent > 50,
            modifier = Modifier.semantics { contentDescription = smaller },
        ) { Text("A−", style = MaterialTheme.typography.labelLarge, modifier = Modifier.clearAndSetSemantics {}) }
        Text(
            "${s.fontSizePercent}%",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp),
        )
        FilledTonalIconButton(
            onClick = { vm.updateText { it.copy(fontSizePercent = (it.fontSizePercent + 10).coerceAtMost(250)) } },
            enabled = s.fontSizePercent < 250,
            modifier = Modifier.semantics { contentDescription = larger },
        ) { Text("A+", style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics {}) }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
            TextPresets.quickThemes.forEach { t ->
                val selected = s.theme == t
                val name = stringResource(t.label())
                Box(
                    Modifier.size(32.dp)
                        .background(Color(t.bg), CircleShape)
                        .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { vm.updateText { it.copy(theme = t) } }
                        .semantics { contentDescription = name; role = Role.RadioButton; this.selected = selected },
                    contentAlignment = Alignment.Center,
                ) { Text("A", color = Color(t.fg), style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

/** Built-in typography presets and the ones saved by the user. */
@Composable
private fun PresetsRow(s: TextReaderSettings, saved: List<TextPreset>, vm: ReaderViewModel) {
    var saving by remember { mutableStateOf(false) }
    Text(stringResource(R.string.rs_presets), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = Dimens.l, top = Dimens.m))
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Dimens.l), horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
        TextPresets.builtIn.forEach { p ->
            FilterChip(
                selected = TextPresets.matches(s, p.settings),
                onClick = { vm.updateText { cur -> TextPresets.apply(cur, p.settings) } },
                label = { Text(stringResource(p.label)) },
            )
        }
        saved.forEach { p ->
            val deleteLabel = stringResource(R.string.rs_preset_delete, p.name)
            InputChip(
                selected = TextPresets.matches(s, p.settings, withColors = true),
                onClick = { vm.applyPreset(p) },
                label = { Text(p.name) },
                trailingIcon = {
                    Icon(Icons.Default.Close, deleteLabel, Modifier.size(18.dp).clickable(role = Role.Button) { vm.deletePreset(p.name) })
                },
            )
        }
        AssistChip(onClick = { saving = true }, label = { Text(stringResource(R.string.rs_preset_save)) }, leadingIcon = { Icon(Icons.Default.Add, null) })
    }
    if (saving) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text(stringResource(R.string.rs_preset_save_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.rs_preset_save_summary), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Dimens.m))
                    OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text(stringResource(R.string.rs_preset_name)) })
                }
            },
            confirmButton = { TextButton(onClick = { vm.savePreset(name); saving = false }, enabled = name.isNotBlank()) { Text(stringResource(CoreR.string.save)) } },
            dismissButton = { TextButton(onClick = { saving = false }) { Text(stringResource(CoreR.string.cancel)) } },
        )
    }
}

/** Paper and ink of the custom theme, with a warning when the text would be hard to read. */
@Composable
private fun CustomColors(s: TextReaderSettings, vm: ReaderViewModel) {
    Text(stringResource(R.string.rs_custom_background), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = Dimens.l, top = Dimens.s))
    SwatchRow(TextPresets.backgrounds, s.customBackground) { c -> vm.updateText { it.copy(customBackground = c) } }
    Text(stringResource(R.string.rs_custom_text), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = Dimens.l, top = Dimens.s))
    SwatchRow(TextPresets.inks, s.customText) { c -> vm.updateText { it.copy(customText = c) } }
    if (TextPresets.contrast(s.customBackground, s.customText) < 3.0) {
        Text(
            stringResource(R.string.rs_low_contrast),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = Dimens.l, vertical = Dimens.xs),
        )
    }
}

@Composable
private fun SwatchRow(colors: List<Long>, selected: Long, onSelect: (Long) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Dimens.l, vertical = Dimens.s), horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
        colors.forEach { c ->
            val isSelected = c == selected
            val hex = "#%06X".format(c and 0xFFFFFF)
            Box(
                Modifier.size(36.dp)
                    .background(Color(c), CircleShape)
                    .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onSelect(c) }
                    .semantics { contentDescription = hex; role = Role.RadioButton; this.selected = isSelected },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Default.Check, null, tint = if (Color(c).luminance() > 0.5f) Color.Black else Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun fontLabel(f: String): String = when (f) {
    "serif" -> stringResource(R.string.rs_font_serif)
    "sans-serif" -> stringResource(R.string.rs_font_sans)
    "monospace" -> stringResource(R.string.rs_font_mono)
    "georgia" -> "Georgia"
    "literata" -> "Literata"
    "roboto" -> "Roboto"
    else -> f.substringBeforeLast('.')
}

@Composable
fun ThemePicker(
    s: TextReaderSettings,
    onSelect: (ReaderTheme) -> Unit,
    themes: List<ReaderTheme> = ReaderTheme.entries,
    selected: ReaderTheme = s.theme,
    title: String = stringResource(R.string.rs_theme),
) {
    if (title.isNotBlank()) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = Dimens.l, top = Dimens.m))
    }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Dimens.l, vertical = Dimens.s), horizontalArrangement = Arrangement.spacedBy(Dimens.m)) {
        themes.forEach { t ->
            val bg = Color(if (t == ReaderTheme.CUSTOM) s.customBackground else t.bg)
            val fg = Color(if (t == ReaderTheme.CUSTOM) s.customText else t.fg)
            val name = stringResource(t.label())
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onSelect(t) }.semantics { contentDescription = name; role = Role.RadioButton; this.selected = selected == t },
            ) {
                Box(
                    Modifier.size(48.dp).background(bg, CircleShape).border(if (selected == t) 3.dp else 1.dp, if (selected == t) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected == t) Icon(Icons.Default.Check, null, tint = fg) else Text("Aa", color = fg)
                }
                Text(name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

fun ReaderTheme.label(): Int = when (this) {
    ReaderTheme.LIGHT -> R.string.theme_light
    ReaderTheme.SEPIA -> R.string.theme_sepia
    ReaderTheme.GRAY -> R.string.theme_gray
    ReaderTheme.DARK -> R.string.theme_dark
    ReaderTheme.BLACK -> R.string.theme_black
    ReaderTheme.CUSTOM -> R.string.theme_custom
}

/** PDF: the layout, the crop of empty margins and the look of the scan. */
@Composable
private fun PdfSettingsContent(s: PdfSettings, vm: ReaderViewModel, query: String) {
    CollapsibleSection(
        title = stringResource(R.string.rs_pdf_group_layout),
        query = query,
        summary = stringResource(R.string.rs_pdf_group_layout_summary),
        icon = Icons.AutoMirrored.Outlined.MenuBook,
        initiallyExpanded = true,
    ) {
        Text(stringResource(R.string.rs_pdf_mode), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = Dimens.l, top = Dimens.m))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.xs)) {
            val opts = listOf(
                PdfMode.SCROLL to R.string.rs_pdf_scroll,
                PdfMode.PAGED to R.string.rs_pdf_paged,
                PdfMode.DUAL to R.string.rs_pdf_dual,
            )
            opts.forEachIndexed { i, (m, label) ->
                SegmentedButton(selected = s.mode == m, onClick = { vm.updatePdf { it.copy(mode = m) } }, shape = SegmentedButtonDefaults.itemShape(i, opts.size)) { Text(stringResource(label)) }
            }
        }
        PreferenceSwitch(stringResource(R.string.rs_pdf_night), s.night, { v -> vm.updatePdf { it.copy(night = v) } }, summary = stringResource(R.string.rs_pdf_night_summary))
        PreferenceSwitch(stringResource(R.string.rs_pdf_reflow), s.reflow, { v -> vm.updatePdf { it.copy(reflow = v) } }, summary = stringResource(R.string.rs_pdf_reflow_summary))
        PreferenceSwitch(stringResource(R.string.rs_pdf_zoom_lock), s.zoomLock, { v -> vm.updatePdf { it.copy(zoomLock = v) } }, summary = stringResource(R.string.rs_zoom_lock_summary))
    }
    CollapsibleSection(
        title = stringResource(R.string.rs_pdf_group_crop),
        query = query,
        summary = stringResource(R.string.rs_pdf_group_crop_summary),
        icon = Icons.Outlined.Margin,
    ) {
        PreferenceSwitch(stringResource(R.string.rs_pdf_crop), s.autoCrop, { v -> vm.updatePdf { it.copy(autoCrop = v) } }, summary = stringResource(R.string.rs_pdf_crop_summary))
        if (!s.autoCrop) {
            val crop = s.manualCrop
            val update: (app.yomu.domain.model.CropMargins) -> Unit = { m -> vm.updatePdf { it.copy(manualCrop = m) } }
            PreferenceSlider(stringResource(R.string.rs_pdf_crop_left), crop.left * 100, 0f..25f, { v -> update(crop.copy(left = v / 100f)) }, { "${it.toInt()}%" }, steps = 25)
            PreferenceSlider(stringResource(R.string.rs_pdf_crop_right), crop.right * 100, 0f..25f, { v -> update(crop.copy(right = v / 100f)) }, { "${it.toInt()}%" }, steps = 25)
            PreferenceSlider(stringResource(R.string.rs_pdf_crop_top), crop.top * 100, 0f..25f, { v -> update(crop.copy(top = v / 100f)) }, { "${it.toInt()}%" }, steps = 25)
            PreferenceSlider(stringResource(R.string.rs_pdf_crop_bottom), crop.bottom * 100, 0f..25f, { v -> update(crop.copy(bottom = v / 100f)) }, { "${it.toInt()}%" }, steps = 25)
        }
    }
    CollapsibleSection(
        title = stringResource(R.string.rs_pdf_group_image),
        query = query,
        summary = stringResource(R.string.rs_pdf_group_image_summary),
        icon = Icons.Outlined.Palette,
    ) {
        PreferenceSlider(
            stringResource(R.string.rs_pdf_contrast), s.contrast, 0.5f..2f,
            { v -> vm.updatePdf { it.copy(contrast = Math.round(v * 10) / 10f) } },
            { "%.1f".format(it) }, steps = 15, summary = stringResource(R.string.rs_pdf_contrast_summary),
        )
        PreferenceSwitch(
            stringResource(R.string.rs_pdf_grayscale), s.grayscale,
            { v -> vm.updatePdf { it.copy(grayscale = v) } },
            summary = stringResource(R.string.rs_pdf_grayscale_summary),
        )
    }
}

@Composable
private fun MangaSettingsContent(s: MangaSettings, vm: ReaderViewModel, query: String) {
    MangaPageSettings(s, { transform -> vm.updateManga(transform) }, query = query)
}

/** Screen brightness, the warm filter, the status line and the window behaviour. */
@Composable
private fun DisplaySettings(c: CommonReaderSettings, vm: ReaderViewModel, showFooter: Boolean, query: String) {
    CollapsibleSection(
        title = stringResource(R.string.rs_group_screen),
        query = query,
        summary = stringResource(R.string.rs_group_screen_summary),
        icon = Icons.Default.BrightnessMedium,
        initiallyExpanded = true,
    ) {
        PreferenceSwitch(stringResource(R.string.rs_system_brightness), c.brightness < 0f, { v -> vm.updateCommon { it.copy(brightness = if (v) -1f else 0.5f) } }, icon = Icons.Default.BrightnessMedium)
        if (c.brightness >= 0f) {
            PreferenceSlider(
                stringResource(R.string.rs_brightness), c.brightness, 0.01f..1f,
                { v -> vm.updateCommon { it.copy(brightness = v) } },
                { "${(it * 100).toInt()}%" }, summary = stringResource(R.string.rs_brightness_summary),
            )
        }
        PreferenceSlider(
            stringResource(R.string.rs_warm), c.warmFilter, 0f..0.8f,
            { v -> vm.updateCommon { it.copy(warmFilter = v) } },
            { "${(it * 100).toInt()}%" }, icon = Icons.Default.WbTwilight, summary = stringResource(R.string.rs_warm_summary),
        )
        PreferenceChoice(
            stringResource(R.string.rs_timeout),
            ScreenTimeout.entries.map { it to timeoutLabel(it) },
            c.screenTimeout,
            { v -> vm.updateCommon { it.copy(screenTimeout = v) } },
        )
        PreferenceChoice(
            stringResource(R.string.rs_orientation),
            listOf(
                ReaderOrientation.AUTO to stringResource(R.string.rs_or_auto),
                ReaderOrientation.PORTRAIT to stringResource(R.string.rs_or_portrait),
                ReaderOrientation.LANDSCAPE to stringResource(R.string.rs_or_landscape),
                ReaderOrientation.LOCKED to stringResource(R.string.rs_or_locked),
            ),
            c.orientation,
            { v -> vm.updateCommon { it.copy(orientation = v) } },
        )
        PreferenceSwitch(stringResource(R.string.rs_fullscreen), c.fullscreen, { v -> vm.updateCommon { it.copy(fullscreen = v) } }, summary = stringResource(R.string.rs_fullscreen_summary))
        PreferenceSwitch(stringResource(R.string.rs_cutout), c.drawUnderCutout, { v -> vm.updateCommon { it.copy(drawUnderCutout = v) } }, summary = stringResource(R.string.rs_cutout_summary))
        PreferenceSlider(
            stringResource(R.string.rs_autohide), c.autoHideMenuSeconds.toFloat(), 0f..10f,
            { v -> vm.updateCommon { it.copy(autoHideMenuSeconds = v.toInt()) } },
            { if (it < 1f) "—" else "${it.toInt()} s" }, steps = 9, summary = stringResource(R.string.rs_autohide_summary),
        )
    }
    if (showFooter) {
        CollapsibleSection(
            title = stringResource(R.string.rs_group_status),
            query = query,
            summary = stringResource(R.string.rs_group_status_summary),
            icon = Icons.AutoMirrored.Filled.Subject,
        ) {
            PreferenceSwitch(stringResource(R.string.rs_footer), c.footer, { v -> vm.updateCommon { it.copy(footer = v) } }, summary = stringResource(R.string.rs_footer_summary))
            if (c.footer) {
                PreferenceSwitch(stringResource(R.string.rs_footer_clock), c.footerClock, { v -> vm.updateCommon { it.copy(footerClock = v) } }, summary = stringResource(R.string.rs_footer_clock_summary))
                PreferenceSwitch(stringResource(R.string.rs_footer_chapter), c.footerChapter, { v -> vm.updateCommon { it.copy(footerChapter = v) } }, summary = stringResource(R.string.rs_footer_chapter_summary))
            }
        }
    }
}

@Composable
private fun timeoutLabel(t: ScreenTimeout): String = when (t) {
    ScreenTimeout.SYSTEM -> stringResource(R.string.rs_timeout_system)
    ScreenTimeout.ALWAYS -> stringResource(R.string.rs_timeout_always)
    else -> stringResource(R.string.rs_timeout_min, t.minutes)
}

/** Taps, edge swipes, hardware keys, the word lookup and automatic scrolling. */
@Composable
private fun ControlSettings(content: ReaderContent, c: CommonReaderSettings, m: MangaSettings, wordTap: WordTapGesture, vm: ReaderViewModel, query: String) {
    if (content is ReaderContent.Manga) {
        MangaControlSettings(m, { transform -> vm.updateManga(transform) }, onPreviewZones = vm::previewTapZones, query = query)
        return
    }
    CollapsibleSection(
        title = stringResource(R.string.rs_group_taps),
        query = query,
        summary = stringResource(R.string.rs_group_taps_summary),
        icon = Icons.Outlined.TouchApp,
        initiallyExpanded = true,
    ) {
        PreferenceSwitch(stringResource(R.string.rs_tap_turn), c.tapToTurn, { v -> vm.updateCommon { it.copy(tapToTurn = v) } }, summary = stringResource(R.string.rs_tap_turn_summary))
        if (content is ReaderContent.Text) {
            Text(stringResource(R.string.rs_tap_zones), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = Dimens.l, top = Dimens.s))
            OptionTiles(
                TapZoneLayout.entries.map { layout ->
                    TileOption(layout, zoneLabel(layout), preview = { _ -> TapZonesDiagram(layout, false, false, sideFraction = 0.25f) })
                },
                c.textTapZones,
                { v -> vm.updateCommon { it.copy(textTapZones = v) } },
            )
        }
    }
    CollapsibleSection(
        title = stringResource(R.string.rs_group_edges),
        query = query,
        summary = stringResource(R.string.rs_group_edges_summary),
        icon = Icons.Filled.SwapHoriz,
    ) {
        PreferenceSwitch(stringResource(R.string.rs_edge_gestures), c.edgeGestures, { v -> vm.updateCommon { it.copy(edgeGestures = v) } }, summary = stringResource(R.string.rs_edge_gestures_summary))
        PreferenceSwitch(stringResource(R.string.rs_edge_brightness), c.edgeBrightness, { v -> vm.updateCommon { it.copy(edgeBrightness = v) } }, enabled = c.edgeGestures)
        PreferenceSwitch(stringResource(R.string.rs_edge_warm), c.edgeWarm, { v -> vm.updateCommon { it.copy(edgeWarm = v) } }, enabled = c.edgeGestures)
    }
    CollapsibleSection(
        title = stringResource(R.string.rs_group_keys),
        query = query,
        summary = stringResource(R.string.rs_group_keys_summary),
        icon = null,
    ) {
        PreferenceSwitch(stringResource(R.string.rs_volume_keys), c.volumeKeys, { v -> vm.updateCommon { it.copy(volumeKeys = v) } }, summary = stringResource(R.string.rs_volume_keys_summary))
    }
    if (content is ReaderContent.Text) {
        CollapsibleSection(
            title = stringResource(R.string.rs_group_word),
            query = query,
            summary = stringResource(
                when (wordTap) {
                    WordTapGesture.DOUBLE_TAP -> R.string.rs_group_word_summary
                    WordTapGesture.SINGLE_TAP -> R.string.rs_group_word_summary_single
                    WordTapGesture.OFF -> R.string.rs_group_word_summary_off
                },
            ),
            icon = Icons.Filled.TextFields,
        ) {
            PreferenceChoice(
                stringResource(R.string.rs_word_tap),
                listOf(
                    WordTapGesture.DOUBLE_TAP to stringResource(R.string.rs_word_double),
                    WordTapGesture.SINGLE_TAP to stringResource(R.string.rs_word_single),
                    WordTapGesture.OFF to stringResource(R.string.rs_word_off),
                ),
                wordTap,
                { v -> vm.setWordTap(v) },
            )
        }
    }
    CollapsibleSection(
        title = stringResource(R.string.rs_group_scroll),
        query = query,
        summary = stringResource(R.string.rs_group_scroll_summary),
        icon = Icons.Outlined.Speed,
    ) {
        PreferenceSlider(
            stringResource(R.string.rs_autoscroll_speed), c.autoScrollSpeed.toFloat(), 1f..100f,
            { v -> vm.updateCommon { it.copy(autoScrollSpeed = v.toInt()) } },
            { it.toInt().toString() }, summary = stringResource(R.string.rs_autoscroll_speed_summary),
        )
    }
}
