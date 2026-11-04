package app.sugarwear

@dagger.hilt.android.HiltAndroidApp
class SugarWearApplication : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        app.sugarlicious.uishared.ProductMigrationLifecycle
            .requireReady(this)
    }
}
