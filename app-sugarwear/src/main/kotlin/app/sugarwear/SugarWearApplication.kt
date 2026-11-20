package app.sugarwear

@dagger.hilt.android.HiltAndroidApp
class SugarWearApplication : android.app.Application() {
    private val alarmPresenter by lazy { CgmForegroundAlarmPresenter(this) }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(alarmPresenter)
        app.sugarlicious.uishared.ProductMigrationLifecycle
            .requireReady(this)
    }
}
