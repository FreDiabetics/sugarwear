package app.sugarwear

import android.app.Service
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.scopes.ServiceScoped

/** Requested lazily by the service after its existing foreground promotion. */
@Module
@InstallIn(ServiceComponent::class)
internal object CollectorServiceModule {
    @Provides
    @ServiceScoped
    fun sensor(service: Service): CgmSensorStateStore = CgmSensorStateStore(service)

    @Provides
    @ServiceScoped
    fun credentials(service: Service): CgmCredentialStore = CgmCredentialStore(service)

    @Provides
    @ServiceScoped
    fun diagnostics(service: Service): CgmCollectorDiagnosticStore = CgmCollectorDiagnosticStore(service)
}
