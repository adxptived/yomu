package app.yomu.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomu.core.ui.components.CollapsibleSection
import app.yomu.core.ui.components.OptionTiles
import app.yomu.core.ui.components.PreferenceChoice
import app.yomu.core.ui.components.PreferenceItem
import app.yomu.core.ui.components.PreferenceSlider
import app.yomu.core.ui.components.PreferenceSwitch
import app.yomu.core.ui.components.SearchField
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
import app.yomu.feature.reader.manga.MangaControlSettings
import app.yomu.feature.reader.manga.MangaPageSettings
import app.yomu.feature.reader.manga.TapZonesDiagram
import app.yomu.feature.reader.manga.zoneLabel
import app.yomu.core.ui.R as CoreR

/**
 * The full reading settings: preview, search across every tab, and groups that keep the frequent choices small and
 * push exact numbers into "Exact values". Changes are applied live, as a preview on the page behind the sheet.
 *
 * Closing with "Done" keeps them. Back, a tap outside or "Revert changes" returns the settings that were in use when the
 * sheet opened — an unfinished experiment is not saved by accident.
 */
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
    /** What the page actually shows now (auto-night included), for the sample card. */
    previewText: TextReaderSettings = text,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    // The settings as they were when the sheet opened: "Revert changes" and Back go back here.
    val openText = remember { text }
    val openManga = remember { manga }
    val openPdf = remember { pdf }
    val openCommon = remember { common }
    fun revertAndClose() {
        viewModel.revertSettings(
            text = openText.takeIf { content is ReaderContent.Text },
            manga = openManga.takeIf { content is ReaderContent.Manga },
            pdf = openPdf.takeIf { content is ReaderContent.Pdf },
            common = openCommon,
        )
        onDismiss()
    }
    val searching = query.isNotBlank()
    val settingsScroll = rememberScrollState()
    // A tab switch starts at its own top: the previous tab's offset would otherwise hide the first setting.
    LaunchedEffect(tab, searching) { settingsScroll.scrollTo(0) }
    YomuSheet(onDismiss = { revertAndClose() }) {
        // A bounded sheet with one weighted scroll region: the title, search, tabs and footer always stay reachable,
        // on a short phone and at a large system font, instead of pushing the list off the screen.
        Column(Modifier.fillMaxWidth().heightIn(max = 760.dp).fillMaxHeight(0.94f)) {
            Row(Modifier.fillMaxWidth().padding(start = Dimens.l, end = 4.dp, top = Dimens.s), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reader_settings),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (canUndo) {
                    TextButton(onClick = viewModel::undoLastChange) { Text(stringResource(R.string.rs_undo_change)) }
                }
            }
            SearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.rs_search_settings),
                modifier = Modifier.padding(horizontal = Dimens.l, vertical = Dimens.xs),
            )
            if (!searching) {
                PrimaryTabRow(selectedTabIndex = tab) {
                    YomuTab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(if (content is ReaderContent.Text) R.string.rs_tab_text else if (content is ReaderContent.Pdf) R.string.rs_tab_pdf else R.string.rs_tab_manga)) })
                    YomuTab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.rs_tab_display)) })
                    YomuTab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.rs_tab_controls)) })
                }
            }
            Column(Modifier.weight(1f).verticalScroll(settingsScroll)) {
                if (content is ReaderContent.Text && !searching) {
                    ReaderPreview(previewText)
                    PresetsRow(text, presets, viewModel)
                }
                if (viewModel.bookId > 0 || viewModel.isOnline) {
                    PreferenceSwitch(stringResource(R.string.rs_per_book), perBook, viewModel::setPerBook, summary = stringResource(R.string.rs_per_book_summary))
                }
                if (searching) {
                    // A query searches all three tabs: a setting in another tab must not look absent.
                    if (!hasMatches(query, content)) {
                        Text(
                            stringResource(R.string.rs_search_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Dimens.l, vertical = Dimens.m),
                        )
                    }
                    when (content) {
                        is ReaderContent.Text -> {
                            TextSettings(text, viewModel, query)
                            ThemeSection(text, viewModel, query)
                            DisplaySettings(common, viewModel, showFooter = true, query = query)
                            ControlSettings(content, common, manga, wordTap, viewModel, query)
                        }
                        is ReaderContent.Pdf -> {
                            PdfSettingsContent(pdf, viewModel, query)
                            DisplaySettings(common, viewModel, showFooter = true, query = query)
                            ControlSettings(content, common, manga, wordTap, viewModel, query)
                        }
                        is ReaderContent.Manga -> {
                            MangaSettingsContent(manga, viewModel, query)
                            DisplaySettings(common, viewModel, showFooter = false, query = query)
                            ControlSettings(content, common, manga, wordTap, viewModel, query)
                        }
                    }
                } else {
                    when (tab) {
                        0 -> when (content) {
                            is ReaderContent.Text -> TextSettings(text, viewModel, query)
                            is ReaderContent.Pdf -> PdfSettingsContent(pdf, viewModel, query)
                            is ReaderContent.Manga -> MangaSettingsContent(manga, viewModel, query)
                        }
                        1 -> {
                            if (content is ReaderContent.Text) ThemeSection(text, viewModel, query)
                            DisplaySettings(common, viewModel, showFooter = content !is ReaderContent.Manga, query = query)
                        }
                        else -> ControlSettings(content, common, manga, wordTap, viewModel, query)
                    }
                }
                Spacer(Modifier.height(Dimens.l))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.s), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { revertAndClose() }) { Text(stringResource(R.string.rs_revert)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.rs_done)) }
            }
        }
    }
}

/**
 * A paragraph in the book's own type: the effect of size, spacing, font and colours is judged here, not on a row of
 * numbers. Shown with the theme colours of the page, so a dark theme previews dark as well.
 */
@Composable
private fun ReaderPreview(s: TextReaderSettings) {
    Surface(
        color = Color(s.background),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.xs),
    ) {
        Column(Modifier.padding(Dimens.m)) {
            Text(
                stringResource(R.string.rs_preview),
                style = MaterialTheme.typography.labelSmall,
                color = Color(s.foreground).copy(alpha = 0.6f),
            )
            Text(
                stringResource(R.string.rs_preview_text),
                style = androidx.compose.ui.text.TextStyle(
                    color = Color(s.foreground),
                    fontFamily = previewFamily(s.fontFamily),
                    fontSize = (18f * s.fontSizePercent / 100f).sp,
                    lineHeight = (18f * s.fontSizePercent / 100f * s.lineHeight).sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight(s.fontWeight),
                    letterSpacing = s.letterSpacing.em,
                    textAlign = when (s.align) {
                        TextAlignment.START -> TextAlign.Start
                        TextAlignment.JUSTIFY -> TextAlign.Justify
                        TextAlignment.CENTER -> TextAlign.Center
                    },
                ),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Dimens.xs),
            )
        }
    }
}

/** Group titles and summaries of each content type, used to tell "nothing found" from a match in a group. */
private fun groupLabels(content: ReaderContent): List<Pair<Int, Int>> {
    val shared = listOf(
        R.string.rs_group_screen to R.string.rs_group_screen_summary,
        R.string.rs_group_status to R.string.rs_group_status_summary,
        R.string.rs_group_taps to R.string.rs_group_taps_summary,
        R.string.rs_group_edges to R.string.rs_group_edges_summary,
        R.string.rs_group_keys to R.string.rs_group_keys_summary,
        R.string.rs_group_word to R.string.rs_group_word_summary,
        R.string.rs_group_scroll to R.string.rs_group_scroll_summary,
    )
    return when (content) {
        is ReaderContent.Text -> listOf(
            R.string.rs_group_font to R.string.rs_group_font_summary,
            R.string.rs_group_spacing to R.string.rs_group_spacing_summary,
            R.string.rs_group_margins to R.string.rs_group_margins_summary,
            R.string.rs_group_style to R.string.rs_group_style_summary,
            R.string.rs_group_navigation to R.string.rs_group_navigation_summary,
            R.string.rs_exact_values to R.string.rs_exact_values_summary,
            R.string.rs_group_theme to R.string.rs_group_theme_summary,
            R.string.rs_group_night to R.string.rs_group_night_summary,
        ) + shared
        is ReaderContent.Pdf -> listOf(
            R.string.rs_pdf_group_layout to R.string.rs_pdf_group_layout_summary,
            R.string.rs_pdf_group_crop to R.string.rs_pdf_group_crop_summary,
            R.string.rs_pdf_group_image to R.string.rs_pdf_group_image_summary,
        ) + shared
        is ReaderContent.Manga -> listOf(
            app.yomu.feature.reader.manga.R.string.mr_mode to app.yomu.feature.reader.manga.R.string.mr_group_mode_summary,
            app.yomu.feature.reader.manga.R.string.mr_scale to app.yomu.feature.reader.manga.R.string.mr_group_scale_summary,
            app.yomu.feature.reader.manga.R.string.mr_background to app.yomu.feature.reader.manga.R.string.mr_group_background_summary,
            app.yomu.feature.reader.manga.R.string.mr_pages to app.yomu.feature.reader.manga.R.string.mr_group_pages_summary,
            app.yomu.feature.reader.manga.R.string.mr_image to app.yomu.feature.reader.manga.R.string.mr_group_image_summary,
            app.yomu.feature.reader.manga.R.string.mr_tap_zones to app.yomu.feature.reader.manga.R.string.mr_group_zones_summary,
        ) + shared
    }
}

@Composable
private fun hasMatches(query: String, content: ReaderContent): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return groupLabels(content).any { (title, summary) ->
        stringResource(title).contains(needle, ignoreCase = true) || stringResource(summary).contains(needle, ignoreCase = true)
    }
}

/**
 * Text settings in collapsible groups. The frequent choices are ready-made and signed ("Medium", "Loose", "Wide"),
 * the exact numbers live under "Exact values"; the font is one row that opens a list with samples. Only one control
 * changes the size — the same row as in the quick panel.
 */
@Composable
private fun TextSettings(s: TextReaderSettings, vm: ReaderViewModel, query: String) {
    var fontOpen by remember { mutableStateOf(false) }
    val weightOptions =
        listOf(400 to R.string.rs_weight_regular, 500 to R.string.rs_weight_medium, 700 to R.string.rs_weight_bold)

    CollapsibleSection(
        title = stringResource(R.string.rs_group_font),
        query = query,
        summary = stringResource(R.string.rs_group_font_summary),
        icon = Icons.Filled.TextFields,
        initiallyExpanded = true,
    ) {
        PreferenceItem(
            title = stringResource(R.string.rs_font_current, fontLabelText(s.fontFamily)),
            icon = Icons.Filled.TextFields,
            onClick = { fontOpen = true },
        )
        FontSizeRow(s, vm)
        Text(
            stringResource(R.string.rs_weight),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = Dimens.l, top = Dimens.s),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.xs)) {
            weightOptions.forEachIndexed { i, (weight, label) ->
                SegmentedButton(
                    selected = s.fontWeight == weight,
                    onClick = { vm.updateText { it.copy(fontWeight = weight) } },
                    shape = SegmentedButtonDefaults.itemShape(i, weightOptions.size),
                ) { Text(stringResource(label)) }
            }
        }
        if (weightOptions.none { it.first == s.fontWeight }) {
            Text(
                "${stringResource(R.string.rs_weight_custom)} · ${s.fontWeight}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.l),
            )
        }
    }

    CollapsibleSection(
        title = stringResource(R.string.rs_group_spacing),
        query = query,
        summary = stringResource(R.string.rs_group_spacing_summary),
        icon = Icons.Outlined.FormatLineSpacing,
        initiallyExpanded = true,
    ) {
        Text(
            stringResource(R.string.rs_line_height),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = Dimens.l, top = Dimens.s),
        )
        val lineOptions =
            listOf(1.3f to R.string.rs_line_compact, 1.5f to R.string.rs_line_normal, 1.8f to R.string.rs_line_loose)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.xs)) {
            lineOptions.forEachIndexed { i, (value, label) ->
                SegmentedButton(
                    selected = kotlin.math.abs(s.lineHeight - value) < 0.02f,
                    onClick = { vm.updateText { it.copy(lineHeight = value) } },
                    shape = SegmentedButtonDefaults.itemShape(i, lineOptions.size),
                ) { Text(stringResource(label)) }
            }
        }
        if (lineOptions.none { kotlin.math.abs(s.lineHeight - it.first) < 0.02f }) {
            Text(
                "${stringResource(R.string.rs_custom_value)} · ${"%.2f×".format(s.lineHeight)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.l),
            )
        }
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
        val marginOptions = listOf(
            Triple(10, 16, R.string.rs_margins_narrow),
            Triple(20, 28, R.string.rs_margins_medium),
            Triple(32, 40, R.string.rs_margins_wide),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.xs)) {
            marginOptions.forEachIndexed { i, (h, v, label) ->
                SegmentedButton(
                    selected = s.marginHorizontal == h && s.marginVertical == v,
                    onClick = { vm.updateText { it.copy(marginHorizontal = h, marginVertical = v) } },
                    shape = SegmentedButtonDefaults.itemShape(i, marginOptions.size),
                ) { Text(stringResource(label)) }
            }
        }
        if (marginOptions.none { s.marginHorizontal == it.first && s.marginVertical == it.second }) {
            Text(
                stringResource(R.string.rs_custom_value) + " · ${s.marginHorizontal} / ${s.marginVertical}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.l),
            )
        }
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
        Text(
            stringResource(R.string.rs_mode),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = Dimens.l, top = Dimens.s),
        )
        OptionTiles(
            listOf(
                TileOption(true, stringResource(R.string.rs_mode_paged)),
                TileOption(false, stringResource(R.string.rs_mode_scroll)),
            ),
            s.paged,
            { paged -> vm.updateText { it.copy(paged = paged) } },
        )
        Text(
            stringResource(if (s.paged) R.string.rs_mode_paged_sum else R.string.rs_mode_scroll_sum),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Dimens.l, vertical = Dimens.xs),
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

    CollapsibleSection(
        title = stringResource(R.string.rs_exact_values),
        query = query,
        summary = stringResource(R.string.rs_exact_values_summary),
        icon = Icons.Outlined.FormatLineSpacing,
    ) {
        PreferenceSlider(
            stringResource(R.string.rs_weight), s.fontWeight.toFloat(), 300f..700f,
            { v -> vm.updateText { it.copy(fontWeight = (v / 100).toInt() * 100) } },
            { it.toInt().toString() }, steps = 3, summary = stringResource(R.string.rs_weight_summary),
        )
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

    if (fontOpen) BookFontDialog(s.fontFamily, vm) { fontOpen = false }
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
        Text(stringResource(R.string.rs_pdf_representation), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = Dimens.l, top = Dimens.m))
        OptionTiles(
            listOf(
                TileOption(false, stringResource(R.string.rs_pdf_original)),
                TileOption(true, stringResource(R.string.rs_pdf_text)),
            ),
            s.reflow,
            { reflow -> vm.updatePdf { it.copy(reflow = reflow) } },
        )
        Text(
            stringResource(if (s.reflow) R.string.rs_pdf_text_sum else R.string.rs_pdf_original_sum),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Dimens.l, vertical = Dimens.xs),
        )
        if (!s.reflow) {
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
        }
        PreferenceSwitch(stringResource(R.string.rs_pdf_night), s.night, { v -> vm.updatePdf { it.copy(night = v) } }, summary = stringResource(R.string.rs_pdf_night_summary))
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
            Text(
                stringResource(R.string.rs_pdf_manual_crop),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = Dimens.l, top = Dimens.s),
            )
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
