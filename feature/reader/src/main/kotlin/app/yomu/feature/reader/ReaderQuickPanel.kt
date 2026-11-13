package app.yomu.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomu.core.ui.components.PreferenceItem
import app.yomu.core.ui.components.PreferenceSwitch
import app.yomu.core.ui.components.TextInputDialog
import app.yomu.core.ui.theme.Dimens
import app.yomu.domain.model.CommonReaderSettings
import app.yomu.domain.model.MangaDirection
import app.yomu.domain.model.MangaSettings
import app.yomu.domain.model.PdfMode
import app.yomu.domain.model.PdfSettings
import app.yomu.domain.model.ScreenTimeout
import app.yomu.domain.model.TextReaderSettings
import app.yomu.core.ui.R as CoreR

/** Screen timeout after switching "keep the screen on" off again. */
internal val DEFAULT_TIMEOUT = ScreenTimeout.MIN_10

/** Flips "keep the screen on": [ScreenTimeout.ALWAYS] versus the usual timeout. */
internal fun CommonReaderSettings.withKeepAwake(on: Boolean): CommonReaderSettings =
    copy(screenTimeout = if (on) ScreenTimeout.ALWAYS else if (screenTimeout == ScreenTimeout.ALWAYS) DEFAULT_TIMEOUT else screenTimeout)

/**
 * The compact "View" panel above the bottom bar: the changes made most often while reading — text size, font, theme and
 * the page mode for books; direction, size and spacing for comics; layout and night view for PDF — plus brightness and
 * the warm filter. Everything else lives behind "All reading settings", so the panel stays short and the page stays
 * visible while the effect is judged.
 */
@Composable
internal fun ReaderQuickPanel(
    content: ReaderContent?,
    common: CommonReaderSettings,
    text: TextReaderSettings?,
    manga: MangaSettings?,
    pdf: PdfSettings?,
    vm: ReaderViewModel,
    onOpenAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = Dimens.l, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reader_quick_display), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(CoreR.string.close)) }
            }
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                when {
                    text != null && content is ReaderContent.Text -> TextQuick(text, vm)
                    manga != null && content is ReaderContent.Manga -> MangaQuick(manga, vm)
                    pdf != null && content is ReaderContent.Pdf -> PdfQuick(pdf, vm)
                }
                CommonQuick(common, vm)
            }
            val canUndo by vm.canUndo.collectAsStateWithLifecycle()
            Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.s), verticalAlignment = Alignment.CenterVertically) {
                if (canUndo) {
                    TextButton(onClick = vm::undoLastChange) { Text(stringResource(R.string.rs_undo_change)) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenAll) {
                    Icon(Icons.Default.Settings, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.rs_open_all), Modifier.padding(start = Dimens.s))
                }
            }
        }
    }
}

@Composable
private fun TextQuick(s: TextReaderSettings, vm: ReaderViewModel) {
    FontSizeRow(s, vm)
    var fontsOpen by remember { mutableStateOf(false) }
    PreferenceItem(
        title = stringResource(R.string.rs_font_current, fontLabelText(s.fontFamily)),
        icon = Icons.Filled.TextFields,
        onClick = { fontsOpen = true },
    )
    ThemePicker(s, { t -> vm.updateText { it.copy(theme = t) } }, themes = TextPresets.quickThemes, title = "")
    Text(
        stringResource(R.string.rs_mode),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = Dimens.l, top = Dimens.s),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.xs)) {
        val options = listOf(true to R.string.rs_mode_paged, false to R.string.rs_mode_scroll)
        options.forEachIndexed { i, (paged, label) ->
            SegmentedButton(
                selected = s.paged == paged,
                onClick = { vm.updateText { it.copy(paged = paged) } },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(stringResource(label)) }
        }
    }
    if (fontsOpen) BookFontDialog(s.fontFamily, vm) { fontsOpen = false }
}

@Composable
private fun MangaQuick(s: MangaSettings, vm: ReaderViewModel) {
    Text(
        stringResource(R.string.rs_mode),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = Dimens.l, top = Dimens.s),
    )
    app.yomu.core.ui.components.OptionTiles(
        listOf(
            app.yomu.core.ui.components.TileOption(MangaDirection.LTR, stringResource(R.string.rs_dir_ltr)),
            app.yomu.core.ui.components.TileOption(MangaDirection.RTL, stringResource(R.string.rs_dir_rtl)),
            app.yomu.core.ui.components.TileOption(MangaDirection.VERTICAL, stringResource(R.string.rs_dir_vertical)),
            app.yomu.core.ui.components.TileOption(MangaDirection.WEBTOON, stringResource(R.string.rs_dir_webtoon)),
        ),
        s.direction,
        { d -> vm.updateManga { it.copy(direction = d) } },
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.l), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(if (s.direction == MangaDirection.WEBTOON) R.string.rs_manga_strip_hint else R.string.rs_manga_paged_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
    if (s.direction == MangaDirection.WEBTOON) {
        PreferenceSwitch(
            stringResource(app.yomu.feature.reader.manga.R.string.mr_webtoon_gaps), s.webtoonGaps,
            { v -> vm.updateManga { it.copy(webtoonGaps = v) } },
        )
    } else {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.xs)) {
            val options = listOf(
                app.yomu.domain.model.ScaleType.FIT_SCREEN to R.string.rs_scale_screen,
                app.yomu.domain.model.ScaleType.FIT_WIDTH to R.string.rs_scale_width,
            )
            options.forEachIndexed { i, (scale, label) ->
                SegmentedButton(
                    selected = s.scale == scale,
                    onClick = { vm.updateManga { it.copy(scale = scale) } },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(stringResource(label)) }
            }
        }
    }
    var brightness by remember(s.brightness) { mutableFloatStateOf(s.brightness) }
    Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.l), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.BrightnessMedium, null, Modifier.size(20.dp))
        Slider(
            value = brightness,
            onValueChange = { brightness = it },
            onValueChangeFinished = { vm.updateManga { it.copy(brightness = brightness) } },
            valueRange = -0.5f..0.5f,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text("${(brightness * 100).toInt()}", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PdfQuick(s: PdfSettings, vm: ReaderViewModel) {
    Text(
        stringResource(R.string.rs_pdf_representation),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = Dimens.l, top = Dimens.s),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.xs)) {
        val options = listOf(false to R.string.rs_pdf_original, true to R.string.rs_pdf_text)
        options.forEachIndexed { i, (reflow, label) ->
            SegmentedButton(
                selected = s.reflow == reflow,
                onClick = { vm.updatePdf { it.copy(reflow = reflow) } },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(stringResource(label)) }
        }
    }
    if (!s.reflow) {
        Text(
            stringResource(R.string.rs_pdf_mode),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = Dimens.l, top = Dimens.s),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.xs)) {
            val options = listOf(
                PdfMode.SCROLL to R.string.rs_pdf_scroll,
                PdfMode.PAGED to R.string.rs_pdf_paged,
                PdfMode.DUAL to R.string.rs_pdf_dual,
            )
            options.forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = s.mode == mode,
                    onClick = { vm.updatePdf { it.copy(mode = mode) } },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(stringResource(label)) }
            }
        }
    }
    PreferenceSwitch(stringResource(R.string.rs_pdf_night), s.night, { v -> vm.updatePdf { it.copy(night = v) } })
}

@Composable
private fun CommonQuick(common: CommonReaderSettings, vm: ReaderViewModel) {
    Text(
        stringResource(R.string.rs_group_screen),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = Dimens.l, top = Dimens.s),
    )
    val brightnessLabel = stringResource(R.string.rs_brightness)
    var brightness by remember(common.brightness) { mutableFloatStateOf(if (common.brightness < 0f) 0.5f else common.brightness) }
    Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.l), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.BrightnessMedium, null, Modifier.size(20.dp))
        Slider(
            value = brightness,
            onValueChange = { brightness = it },
            onValueChangeFinished = { vm.updateCommon { it.copy(brightness = brightness.coerceAtLeast(0.01f)) } },
            enabled = common.brightness >= 0f,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { contentDescription = brightnessLabel },
        )
        FilterChip(
            selected = common.brightness < 0f,
            onClick = { vm.updateCommon { it.copy(brightness = if (it.brightness < 0f) 0.5f else -1f) } },
            label = { Text(stringResource(R.string.reader_brightness_auto)) },
        )
    }
    val warmLabel = stringResource(R.string.rs_warm)
    var warm by remember(common.warmFilter) { mutableFloatStateOf(common.warmFilter) }
    Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.l), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.WbTwilight, null, Modifier.size(20.dp))
        Slider(
            value = warm,
            onValueChange = { warm = it },
            onValueChangeFinished = { vm.updateCommon { it.copy(warmFilter = warm) } },
            valueRange = 0f..0.8f,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { contentDescription = warmLabel },
        )
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.l), horizontalArrangement = Arrangement.Start) {
        FilterChip(
            selected = common.screenTimeout == ScreenTimeout.ALWAYS,
            onClick = { vm.updateCommon { it.withKeepAwake(it.screenTimeout != ScreenTimeout.ALWAYS) } },
            label = { Text(stringResource(R.string.reader_keep_awake)) },
        )
    }
}

/**
 * Text size with one source of truth: A−, the current value and A+; tapping the value opens a field for an exact size.
 * The full sheet and the quick panel use the same row, so there is never a second slider with the same number.
 */
@Composable
internal fun FontSizeRow(s: TextReaderSettings, vm: ReaderViewModel, showLabel: Boolean = true) {
    var exact by remember { mutableStateOf(false) }
    val smaller = stringResource(R.string.rs_font_smaller)
    val larger = stringResource(R.string.rs_font_larger)
    val exactLabel = stringResource(R.string.rs_size_exact_title)
    if (showLabel) {
        Text(
            stringResource(R.string.rs_font_size),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = Dimens.l, top = Dimens.s),
        )
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.xs), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { vm.updateText { it.copy(fontSizePercent = (it.fontSizePercent - 10).coerceAtLeast(50)) } },
            enabled = s.fontSizePercent > 50,
            modifier = Modifier.semantics { contentDescription = smaller },
        ) { Text("A−", style = MaterialTheme.typography.labelLarge) }
        Text(
            "${s.fontSizePercent}%",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = Dimens.m)
                .semantics { contentDescription = exactLabel }
                .clickable { exact = true }
                .padding(vertical = Dimens.xs),
        )
        FilledTonalIconButton(
            onClick = { vm.updateText { it.copy(fontSizePercent = (it.fontSizePercent + 10).coerceAtMost(250)) } },
            enabled = s.fontSizePercent < 250,
            modifier = Modifier.semantics { contentDescription = larger },
        ) { Text("A+", style = MaterialTheme.typography.titleMedium) }
    }
    if (exact) {
        TextInputDialog(
            title = stringResource(R.string.rs_size_exact_title),
            initial = s.fontSizePercent.toString(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            onConfirm = { v -> v.toIntOrNull()?.let { size -> vm.updateText { it.copy(fontSizePercent = size.coerceIn(50, 250)) } } },
            onDismiss = { exact = false },
        )
    }
}
