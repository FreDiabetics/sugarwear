package app.sugarlicious.mobile

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/** Cold UI observation only; no background work starts when the graph is created. */
internal class DashboardDependencies
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        val repository = DashboardStateRepository(context)
        val clock =
            flow {
                while (true) {
                    emit(System.currentTimeMillis())
                    delay(delayUntilNextGraphMinute(System.currentTimeMillis()))
                }
            }
    }

/** Retains the existing feature-owned scope, cancelled by its ViewModel onCleared. */
@ViewModelScoped
internal class MobileSourceSettingsDependencies
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val repository: MobileSourceSettingsStore = MobileSourceSettingsRepository(context, scope)
    }

@Module
@InstallIn(ViewModelComponent::class)
internal object MobileViewModelModule {
    @Provides
    fun settings(
        @ApplicationContext context: Context,
    ): MobileSettingsStore = MobileSettingsRepository(context)

    @Provides
    fun treatments(
        @ApplicationContext context: Context,
    ): CloudEndpointTreatmentSettingsStore = CloudEndpointTreatmentSettingsRepository(context)
}

internal class AppearanceRepositoryFactory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun create(target: AppearanceColorTarget): AppearanceSettingsRepository = AndroidAppearanceSettingsRepository(context, target = target)
    }

internal class WidgetRepositoryFactory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun create(widgetId: Int, kind: ConfigurableWidgetKind, useDefaults: Boolean): WidgetConfigurationRepository =
            AndroidWidgetConfigurationRepository(context, widgetId, kind, useDefaults)
    }
