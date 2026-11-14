package app.sugarlicious.wear

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext

@Module
@InstallIn(ViewModelComponent::class)
internal object WearViewModelModule {
    @Provides
    fun overview(
        @ApplicationContext context: Context,
    ): WearOverviewRepository = AndroidWearOverviewRepository(context)

    @Provides
    fun settings(
        @ApplicationContext context: Context,
    ): WearSettingsRepository = AndroidWearSettingsRepository(context)
}
