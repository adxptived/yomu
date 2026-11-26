package app.yomu.feature.reader

import androidx.lifecycle.SavedStateHandle
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val readerModule = module {
    // In Compose, Koin resolves SavedStateHandle from CreationExtras and the window survives process death. Plain
    // Koin get() (used by the app smoke tests) has no CreationExtras, so fall back to an empty handle there.
    viewModel { (args: ReaderArgs) ->
        ReaderViewModel(
            args, androidContext(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            getOrNull<SavedStateHandle>() ?: SavedStateHandle(),
        )
    }
    viewModel { ReadingStatsViewModel(get(), get(), get()) }
    viewModel { (bookId: Long) -> DocxLayoutViewModel(bookId, androidContext(), get(), get(), get()) }
}
