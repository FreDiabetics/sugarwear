package app.sugarwear

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped

/** One command adapter shared by the retained pairing model and its Activity test seams. */
@Module
@InstallIn(ActivityRetainedComponent::class)
internal object PairingModule {
    @Provides
    @ActivityRetainedScoped
    fun adapter(
        @ApplicationContext context: Context,
    ): AndroidCgmPairingCommands = AndroidCgmPairingCommands(context)

    @Provides
    fun commands(adapter: AndroidCgmPairingCommands): CgmPairingCommands = adapter
}

@Module
@InstallIn(ViewModelComponent::class)
internal object CollectorViewModelModule {
    @Provides
    fun overview(
        @ApplicationContext context: Context,
    ): CgmOverviewRepository = AndroidCgmOverviewRepository(context)

    @Provides
    fun appearance(
        @ApplicationContext context: Context,
    ): CgmAppearanceStore = CgmAppearanceStore(context)

    @Provides
    fun vigil(
        @ApplicationContext context: Context,
    ): CgmDirectToWatchSettingsStore = CgmDirectToWatchSettingsStore(context)

    @Provides
    fun access(
        @ApplicationContext context: Context,
    ): CgmSettingsAccessRepository = CgmSettingsAccessRepository(context)

    @Provides
    fun status(
        @ApplicationContext context: Context,
    ): CgmSystemStatusRepository = CgmSystemStatusRepository(context)
}
