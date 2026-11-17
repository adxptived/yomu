package app.yomu.feature.catalog

import app.yomu.source.ConnectionKind
import app.yomu.source.ProbeResult

/** What the user wants to read. The first question of the wizard. */
enum class ConnectionContent { BOOKS, COMICS, AUDIO, FILES }

/**
 * How it gets into the app.
 *
 * [OPDS], [AUDIO_LINK] and [RSS] are saved by the wizard itself; the others are hand-offs to the screens that already
 * do the job (the extension list, the library's import sheet). A method that is not implemented yet says so instead of
 * looking like a connection that silently does nothing.
 */
enum class ConnectionMethod(val implemented: Boolean) {
    OPDS(true),
    AUDIO_LINK(true),
    RSS(true),
    EXTENSION(false),
    FILES(false),
    FOLDER(false),
}

/** Why the wizard could not finish. A failed check and a failed save are different causes with different advice. */
sealed interface ConnectionFailure {
    data class Check(val result: ProbeResult) : ConnectionFailure
    data class Save(val error: Throwable) : ConnectionFailure
}

/** One step of the wizard. */
sealed interface ConnectionStep {
    data object Content : ConnectionStep
    data object Method : ConnectionStep
    data class Address(val method: ConnectionMethod) : ConnectionStep
    data class Probing(val address: String) : ConnectionStep
    data class Preview(val result: ProbeResult.Recognised) : ConnectionStep
    data object Saving : ConnectionStep
    data object Done : ConnectionStep
    data class Failed(val reason: ConnectionFailure) : ConnectionStep

    /** Files are being imported through the library's own import service. */
    data object Importing : ConnectionStep

    /** The import report: added, already in the library, failed. */
    data class Imported(val added: Int, val duplicates: Int, val failed: Int) : ConnectionStep

    /** The chosen method is a hand-off: the screen offers the existing flow instead of a form. */
    data class HandOff(val method: ConnectionMethod) : ConnectionStep
}

/**
 * The wizard's state. It holds no Android types, so every transition is a unit test; the password stays in this object
 * only for the length of the flow and is never written to the saved state.
 */
data class SourceConnectionState(
    val step: ConnectionStep = ConnectionStep.Content,
    val content: ConnectionContent? = null,
    val method: ConnectionMethod? = null,
    val address: String = "",
    val username: String = "",
    val password: String = "",
    /** The address whose preview is shown; saving repeats exactly this one. */
    val checkedAddress: String = "",
    val checkedTitle: String? = null,
    /** The document the address answered with; kept so Back from a save returns to its preview. */
    val checked: ProbeResult.Recognised? = null,
    val saveError: ConnectionFailure? = null,
)

/**
 * The transitions of the wizard.
 *
 * Back never saves a half-working source: it returns to the previous question, and from the first question closes the
 * wizard.
 */
object SourceConnectionFlow {
    val initial = SourceConnectionState()

    fun chooseContent(state: SourceConnectionState, content: ConnectionContent): SourceConnectionState =
        state.copy(step = ConnectionStep.Method, content = content, method = null)

    fun chooseMethod(state: SourceConnectionState, method: ConnectionMethod): SourceConnectionState = state.copy(
        step = if (method.implemented) ConnectionStep.Address(method) else ConnectionStep.HandOff(method),
        method = method,
        saveError = null,
    )

    fun editAddress(
        state: SourceConnectionState,
        address: String = state.address,
        username: String = state.username,
        password: String = state.password,
    ): SourceConnectionState = state.copy(address = address, username = username, password = password, saveError = null)

    /**
     * The address is checked only when it can be: a blank field or a non-http address never reaches the network. The
     * caller starts the request when this returns [ConnectionStep.Probing].
     */
    fun startCheck(state: SourceConnectionState): SourceConnectionState {
        val address = state.address.trim()
        if (address.isEmpty()) return state.copy(step = ConnectionStep.Failed(ConnectionFailure.Check(ProbeResult.InvalidUrl("empty"))), checkedAddress = "")
        return state.copy(step = ConnectionStep.Probing(address), checkedAddress = address)
    }

    /** The result of the check: only a recognised document goes on to the preview. */
    fun checked(state: SourceConnectionState, result: ProbeResult): SourceConnectionState = when (result) {
        is ProbeResult.Recognised -> state.copy(
            step = ConnectionStep.Preview(result),
            checkedTitle = result.title,
            checked = result,
            saveError = null,
        )
        else -> state.copy(step = ConnectionStep.Failed(ConnectionFailure.Check(result)), checked = null)
    }

    fun startSave(state: SourceConnectionState): SourceConnectionState? = when {
        state.step !is ConnectionStep.Preview -> null
        state.checkedAddress.isBlank() -> null
        else -> state.copy(step = ConnectionStep.Saving, saveError = null)
    }

    fun saved(state: SourceConnectionState): SourceConnectionState = state.copy(step = ConnectionStep.Done)

    /** A picked file set or folder starts importing; the report replaces the step when it finishes. */
    fun importing(state: SourceConnectionState): SourceConnectionState = state.copy(step = ConnectionStep.Importing)

    /** The import report: the same numbers the library's import shows. */
    fun imported(state: SourceConnectionState, outcomes: List<app.yomu.data.library.ImportOutcome>): SourceConnectionState =
        state.copy(
            step = ConnectionStep.Imported(
                added = outcomes.count { it is app.yomu.data.library.ImportOutcome.Added },
                duplicates = outcomes.count { it is app.yomu.data.library.ImportOutcome.Duplicate },
                failed = outcomes.count { it is app.yomu.data.library.ImportOutcome.Failed },
            ),
        )

    /** A save that failed after the check (the source disappeared, the database refused): the address can be checked again. */
    fun saveFailed(state: SourceConnectionState, error: Throwable): SourceConnectionState = state.copy(
        step = ConnectionStep.Failed(ConnectionFailure.Save(error)),
        saveError = ConnectionFailure.Save(error),
    )

    /**
     * One step back. A failure returns to the address it failed on; a preview returns to the address too, so the user
     * can correct it without starting over.
     */
    fun back(state: SourceConnectionState): SourceConnectionState = when (state.step) {
        ConnectionStep.Content -> state
        ConnectionStep.Method -> state.copy(step = ConnectionStep.Content, content = null)
        is ConnectionStep.Address -> state.copy(step = ConnectionStep.Method, method = null)
        is ConnectionStep.HandOff -> state.copy(step = ConnectionStep.Method, method = null)
        is ConnectionStep.Probing -> state.copy(step = ConnectionStep.Address(state.method ?: ConnectionMethod.OPDS))
        is ConnectionStep.Preview -> state.copy(step = ConnectionStep.Address(state.method ?: ConnectionMethod.OPDS))
        ConnectionStep.Saving -> state.checked?.let { state.copy(step = ConnectionStep.Preview(it)) } ?: state
        is ConnectionStep.Failed -> state.copy(step = ConnectionStep.Address(state.method ?: ConnectionMethod.OPDS))
        ConnectionStep.Importing -> state.copy(step = ConnectionStep.HandOff(state.method ?: ConnectionMethod.FILES))
        is ConnectionStep.Imported -> state.copy(step = ConnectionStep.HandOff(state.method ?: ConnectionMethod.FILES))
        ConnectionStep.Done -> state
    }

    /** The default name of a saved connection: the document's own title, otherwise the host of the address. */
    fun suggestedName(state: SourceConnectionState): String =
        state.checkedTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?: state.checkedAddress.substringAfter("://", state.checkedAddress).substringBefore('/').takeIf { it.isNotBlank() }
            ?: state.checkedAddress

    /** What the first step offers for [content]. */
    fun methodsFor(content: ConnectionContent?): List<ConnectionMethod> = when (content) {
        ConnectionContent.BOOKS -> listOf(ConnectionMethod.OPDS, ConnectionMethod.FILES, ConnectionMethod.FOLDER)
        ConnectionContent.COMICS -> listOf(ConnectionMethod.OPDS, ConnectionMethod.EXTENSION, ConnectionMethod.FILES, ConnectionMethod.FOLDER)
        ConnectionContent.AUDIO -> listOf(ConnectionMethod.AUDIO_LINK, ConnectionMethod.RSS)
        ConnectionContent.FILES -> listOf(ConnectionMethod.FILES, ConnectionMethod.FOLDER)
        null -> emptyList()
    }

    /** The content a saved connection serves, for the preview line. */
    fun kindLabel(method: ConnectionMethod?): ConnectionKind? = when (method) {
        ConnectionMethod.OPDS -> ConnectionKind.OPDS
        ConnectionMethod.AUDIO_LINK -> ConnectionKind.AUDIO
        ConnectionMethod.RSS -> ConnectionKind.RSS
        else -> null
    }
}
