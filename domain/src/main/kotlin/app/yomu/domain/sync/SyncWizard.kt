package app.yomu.domain.sync

/** The steps of the sync wizard, in order. */
enum class SyncStep { BACKEND, INPUT, PROBE, CATEGORIES, SCHEDULE, PREVIEW, READY }

/** What a failed sync needs from the user. */
enum class SyncFailure { AUTH, NETWORK, STORAGE, BATTERY, OTHER }

/** The categories of the sync; at least one must stay on. */
enum class SyncCategory { PROGRESS, CATALOG, ANNOTATIONS, BOOKMARKS, WORDS, FILES, GLOSSARY }

/** How a failure is recovered: what to check, and whether retrying in place can help. */
data class SyncRecovery(val failure: SyncFailure, val retryNow: Boolean, val advice: String) {
    val needsUser: Boolean get() = !retryNow
}

/** One status line for the sync screen instead of scattered flags. */
data class SyncStatusSummary(
    val lastSuccessAt: Long = 0,
    val error: String? = null,
    val conflicts: Int = 0,
    val running: Boolean = false,
) {
    val healthy: Boolean get() = error == null && conflicts == 0
    val hasRun: Boolean get() = lastSuccessAt > 0 || error != null

    /** A partial result is explicitly not "ready": conflicts keep it from being healthy. */
    val partial: Boolean get() = error == null && conflicts > 0
}

/**
 * The sync setup as a small state machine: a step cannot be skipped, a probe must pass before categories are offered,
 * and a preview must be confirmed before the first run. It is pure so the transitions are testable without a server.
 */
data class SyncWizardState(
    val step: SyncStep = SyncStep.BACKEND,
    val backend: String? = null,
    val inputReady: Boolean = false,
    val probePassed: Boolean = false,
    val categories: Set<SyncCategory> = SyncCategory.entries.toSet(),
    val scheduleHours: Int = 6,
    val previewConfirmed: Boolean = false,
    val failure: SyncFailure? = null,
)

object SyncWizard {
    fun start(configured: Boolean): SyncWizardState =
        if (configured) SyncWizardState(step = SyncStep.INPUT, inputReady = true) else SyncWizardState()

    fun withBackend(state: SyncWizardState, backend: String): SyncWizardState =
        state.copy(backend = backend, step = SyncStep.INPUT, inputReady = false, probePassed = false, previewConfirmed = false)

    /** Any change at the input step invalidates a probe done with the old credentials. */
    fun withInput(state: SyncWizardState, ready: Boolean): SyncWizardState =
        if (state.inputReady == ready) state
        else state.copy(inputReady = ready, probePassed = false, previewConfirmed = false, failure = null)

    fun withProbe(state: SyncWizardState, passed: Boolean): SyncWizardState =
        state.copy(probePassed = passed, failure = if (passed) null else state.failure)

    fun toggleCategory(state: SyncWizardState, category: SyncCategory): SyncWizardState {
        val next = if (category in state.categories) state.categories - category else state.categories + category
        // A sync with no categories would do nothing; the last one cannot be turned off.
        return if (next.isEmpty()) state else state.copy(categories = next)
    }

    fun withSchedule(state: SyncWizardState, hours: Int): SyncWizardState = state.copy(scheduleHours = hours.coerceIn(0, 168))

    fun confirmPreview(state: SyncWizardState): SyncWizardState = state.copy(previewConfirmed = true)

    fun failure(state: SyncWizardState, failure: SyncFailure?): SyncWizardState = state.copy(failure = failure)

    /**
     * Whether the current step is complete. The UI uses this to enable the Next button and the wizard itself uses it
     * in [advance], so a step can never be skipped by tapping through it.
     */
    fun canAdvance(state: SyncWizardState): Boolean = when (state.step) {
        SyncStep.BACKEND -> state.backend != null
        SyncStep.INPUT -> state.inputReady
        SyncStep.PROBE -> state.probePassed
        SyncStep.CATEGORIES -> state.categories.isNotEmpty()
        SyncStep.SCHEDULE -> true
        SyncStep.PREVIEW -> state.previewConfirmed
        SyncStep.READY -> false
    }

    /** Moves forward only when the current step's condition is met; otherwise the state is returned unchanged. */
    fun advance(state: SyncWizardState): SyncWizardState =
        if (canAdvance(state)) nextStep(state) else state

    private fun nextStep(state: SyncWizardState): SyncWizardState = when (state.step) {
        SyncStep.BACKEND -> state.copy(step = SyncStep.INPUT)
        SyncStep.INPUT -> state.copy(step = SyncStep.PROBE)
        SyncStep.PROBE -> state.copy(step = SyncStep.CATEGORIES)
        SyncStep.CATEGORIES -> state.copy(step = SyncStep.SCHEDULE)
        SyncStep.SCHEDULE -> state.copy(step = SyncStep.PREVIEW)
        SyncStep.PREVIEW -> state.copy(step = SyncStep.READY)
        SyncStep.READY -> state
    }

    fun back(state: SyncWizardState): SyncWizardState = when (state.step) {
        SyncStep.BACKEND -> state
        SyncStep.INPUT -> state.copy(step = SyncStep.BACKEND)
        // Going back into the credentials invalidates the probe: the user may change them before continuing.
        SyncStep.PROBE -> state.copy(step = SyncStep.INPUT, probePassed = false, previewConfirmed = false, failure = null)
        SyncStep.CATEGORIES -> state.copy(step = SyncStep.PROBE)
        SyncStep.SCHEDULE -> state.copy(step = SyncStep.CATEGORIES)
        SyncStep.PREVIEW -> state.copy(step = SyncStep.SCHEDULE)
        SyncStep.READY -> state.copy(step = SyncStep.PREVIEW, previewConfirmed = false)
    }

    fun recovery(failure: SyncFailure): SyncRecovery = when (failure) {
        SyncFailure.AUTH -> SyncRecovery(failure, retryNow = false, advice = "Check the account or token, then test the connection again")
        SyncFailure.NETWORK -> SyncRecovery(failure, retryNow = true, advice = "The sync will retry when the connection is back")
        SyncFailure.STORAGE -> SyncRecovery(failure, retryNow = false, advice = "Free some space and start the sync again")
        SyncFailure.BATTERY -> SyncRecovery(failure, retryNow = false, advice = "Connect the charger or lower the battery restriction")
        SyncFailure.OTHER -> SyncRecovery(failure, retryNow = true, advice = "Review the error and try again")
    }
}

/**
 * Maps a sync error message to the recovery the user needs. This is deliberately a conservative, ordered text match:
 * the sync backends report failures as messages and exception class names, and a wrong bucket would suggest a useless
 * action ("retry" for a missing token). Unknown failures stay [SyncFailure.OTHER], which is honestly retryable.
 */
fun classifySyncFailure(message: String?): SyncFailure {
    val m = message.orEmpty().lowercase()
    if (m.isEmpty()) return SyncFailure.OTHER
    return when {
        // Authentication first: a 401/403 is an auth problem even though it is also an HTTP/network error.
        listOf("unauthor", "forbidden", "401", "403", "token", "credential", "auth", "sign in", "login", "авторизац", "токен", "парол").any { it in m } -> SyncFailure.AUTH
        listOf("no space", "enospc", "disk full", "storage full", "quota", "нет места", "диск заполнен", "x-cluster").any { it in m } -> SyncFailure.STORAGE
        listOf("battery", "батаре", "power save", "doze").any { it in m } -> SyncFailure.BATTERY
        listOf("offline", "network", "timeout", "timed out", "connect", "unreachable", "unknownhost", "dns", "socket", "http", "сеть", "соединен", "таймаут").any { it in m } -> SyncFailure.NETWORK
        else -> SyncFailure.OTHER
    }
}
