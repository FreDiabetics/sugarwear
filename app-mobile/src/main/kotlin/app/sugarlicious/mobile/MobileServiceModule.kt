package app.sugarlicious.mobile

import android.app.Service
import app.sugarlicious.storage.TherapyStateStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.scopes.ServiceScoped

@Module
@InstallIn(ServiceComponent::class)
internal object MobileServiceModule {
    @Provides
    @ServiceScoped
    fun therapy(
        service: Service,
    ): TherapyStateStore = TherapyStateStore(service)
}
