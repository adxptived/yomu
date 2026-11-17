package app.yomu.feature.catalog

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yomu.core.common.ScreenErrors
import app.yomu.data.audio.AudiobookStore
import app.yomu.data.catalog.CatalogRepository
import app.yomu.data.library.ImportOutcome
import app.yomu.data.library.ImportService
import app.yomu.data.source.SourceManager
import app.yomu.source.ConnectionKind
import app.yomu.source.ConnectionProbe
import app.yomu.source.ProbeResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-shot results of the wizard. */
sealed interface SourceConnectionEvent {
    /** The connection was saved and is usable: the screen closes and the caller may open it. */
    data class Saved(val sourceId: Long?, val title: String) : SourceConnectionEvent
}

/**
 * The connection wizard's owner.
 *
 * The checks and the save run here, not in the composable: a rotation during the probe must not lose it, and a second
 * tap on "Add" must not save the same catalogue twice ([saving] guards the whole flow).
 */
class SourceConnectionViewModel(
    private val context: Context,
    private val catalog: CatalogRepository,
    private val sources: SourceManager,
    private val audiobooks: AudiobookStore,
    private val subscriptions: app.yomu.data.audio.AudioSubscriptionRepository,
    private val importer: ImportService,
) : ViewModel() {
    private val _state = MutableStateFlow(SourceConnectionFlow.initial)
    val state: StateFlow<SourceConnectionState> = _state

    private val _events = Channel<SourceConnectionEvent>(Channel.BUFFERED)
    val events: Flow<SourceConnectionEvent> = _events.receiveAsFlow()

    /** One request at a time: a second check would race the first answer into the screen. */
    private var checkJob: kotlinx.coroutines.Job? = null
    private var saveJob: kotlinx.coroutines.Job? = null

    fun chooseContent(content: ConnectionContent) = _state.update { SourceConnectionFlow.chooseContent(it, content) }

    fun chooseMethod(method: ConnectionMethod) = _state.update { SourceConnectionFlow.chooseMethod(it, method) }

    fun editAddress(address: String? = null, username: String? = null, password: String? = null) = _state.update {
        SourceConnectionFlow.editAddress(it, address ?: it.address, username ?: it.username, password ?: it.password)
    }

    fun back() = _state.update { SourceConnectionFlow.back(it) }

    /** Checks the address and advances to the preview only when the document was recognised. */
    fun check() {
        if (checkJob?.isActive == true) return
        val next = SourceConnectionFlow.startCheck(_state.value)
        _state.value = next
        val probing = next.step as? ConnectionStep.Probing ?: return
        // An audio link is resolved by the audio importer itself (YouTube, a podcast feed, a file): here only the
        // address is validated, so a working link is not rejected because its server calls it octet-stream.
        if (next.method == ConnectionMethod.AUDIO_LINK) {
            val invalid = ConnectionProbe.validateUrl(probing.address)
            _state.update {
                SourceConnectionFlow.checked(
                    it,
                    invalid ?: ProbeResult.Recognised(ConnectionKind.AUDIO),
                )
            }
            return
        }
        checkJob = viewModelScope.launch(ScreenErrors) {
            val result = catalog.probeConnection(
                url = probing.address,
                username = next.username.ifBlank { null },
                password = next.password.ifBlank { null },
                supported = supported(next.method),
            )
            _state.update { SourceConnectionFlow.checked(it, result) }
        }
    }

    /** Saves the checked connection exactly once; a second call while saving is ignored. */
    fun save() {
        if (saveJob?.isActive == true) return
        val next = SourceConnectionFlow.startSave(_state.value) ?: return
        _state.value = next
        saveJob = viewModelScope.launch(ScreenErrors) {
            try {
                val id = when (next.method) {
                    ConnectionMethod.OPDS -> {
                        // The probe already answered for this address; the source is saved with the same name the
                        // preview showed.
                        sources.addOpds(
                            name = SourceConnectionFlow.suggestedName(next),
                            url = next.checkedAddress,
                            username = next.username.ifBlank { null },
                            password = next.password.ifBlank { null },
                        )
                    }
                    ConnectionMethod.AUDIO_LINK -> {
                        when (val outcome = audiobooks.addLink(next.checkedAddress)) {
                            is ImportOutcome.Failed -> throw outcome.error
                            else -> null
                        }
                    }
                    ConnectionMethod.RSS -> {
                        // A subscription keeps the address and updates the book; the feed was already parsed by the
                        // check, and `subscribe` parses it once more before anything is written.
                        subscriptions.subscribe(next.checkedAddress, SourceConnectionFlow.suggestedName(next))
                            .getOrElse { throw it }
                            .let { null }
                    }
                    else -> return@launch
                }
                val title = SourceConnectionFlow.suggestedName(next)
                _state.update { SourceConnectionFlow.saved(it) }
                _events.send(SourceConnectionEvent.Saved(id, title))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { SourceConnectionFlow.saveFailed(it, e) }
            }
        }
    }

    /**
     * The files the user picked: imported through the same service the library uses, so the report, the duplicate check
     * and the copy/link decision are the same. The result names every outcome.
     */
    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val current = _state.value
        _state.value = SourceConnectionFlow.importing(current)
        viewModelScope.launch(ScreenErrors) {
            persist(uris)
            val outcomes = runCatching { importer.import(uris) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrElse { listOf(ImportOutcome.Failed(uris.first().toString(), it)) }
            _state.update { SourceConnectionFlow.imported(it, outcomes) }
        }
    }

    /** A picked folder becomes one image book, the same way the library's import sheet makes one. */
    fun importFolder(tree: Uri) {
        val current = _state.value
        _state.value = SourceConnectionFlow.importing(current)
        viewModelScope.launch(ScreenErrors) {
            persist(listOf(tree))
            val folderName = runCatching { androidx.documentfile.provider.DocumentFile.fromTreeUri(context, tree)?.name }
                .getOrNull()
                .orEmpty()
                .ifBlank { "Folder" }
            val outcome = runCatching { importer.importFolder(tree, folderName) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrElse { ImportOutcome.Failed(tree.toString(), it) }
            _state.update { SourceConnectionFlow.imported(it, listOf(outcome)) }
        }
    }

    private fun persist(uris: List<Uri>) {
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }.recoverCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    /** The document kinds a save can accept for [method]; an audio link is not probed (the audio importer resolves it). */
    private fun supported(method: ConnectionMethod?): Set<ConnectionKind> = when (method) {
        ConnectionMethod.OPDS -> setOf(ConnectionKind.OPDS)
        ConnectionMethod.RSS -> setOf(ConnectionKind.RSS)
        ConnectionMethod.AUDIO_LINK -> setOf(ConnectionKind.AUDIO, ConnectionKind.RSS, ConnectionKind.OPDS)
        else -> emptySet()
    }
}
