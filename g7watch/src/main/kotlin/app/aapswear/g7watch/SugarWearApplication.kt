package app.aapswear.g7watch

class SugarWearApplication : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        app.aapswear.uishared.ProductMigrationLifecycle
            .requireReady(this)
    }
}
