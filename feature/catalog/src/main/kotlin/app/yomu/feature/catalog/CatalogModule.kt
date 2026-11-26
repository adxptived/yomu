package app.yomu.feature.catalog

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Parameters of [SourceBrowseViewModel]; [tab] is a [BrowseTab] name to open with. */
data class BrowseArgs(val sourceId: Long, val query: String? = null, val navUrl: String? = null, val navTitle: String? = null, val tab: String? = null)

/** Parameters of [GlobalSearchViewModel]; [migrateFrom] switches it to migration mode. */
data class SearchArgs(val query: String? = null, val migrateFrom: Long? = null)

val catalogModule = module {
    viewModel { CatalogHomeViewModel(androidContext(), get(), get(), get(), get(), get()) }
    viewModel { (args: BrowseArgs) -> SourceBrowseViewModel(args.sourceId, args.query, args.navUrl, args.navTitle, args.tab, androidContext(), get(), get(), get()) }
    viewModel { (args: SearchArgs) -> GlobalSearchViewModel(args.query, args.migrateFrom, get(), get(), get()) }
    viewModel { (entryId: Long) -> TitleViewModel(entryId, get(), get(), get(), get(), get(), get(), get(), get()) }
    // C3: the public page of a tracker title; the route passes the service id and the tracker's own title id.
    viewModel { (service: Int, remoteId: Long) ->
        TrackerTitleViewModel(
            app.yomu.domain.tracking.TrackService.of(service) ?: app.yomu.domain.tracking.TrackService.ANILIST,
            remoteId,
            get(),
        )
    }
    viewModel { ExtensionsViewModel(androidContext(), get(), get(), get()) }
    viewModel { SourceConnectionViewModel(androidContext(), get(), get(), get(), get(), get()) }
    viewModel { UpcomingViewModel(androidContext(), get(), get()) }
}
