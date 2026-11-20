package app.sugarwear

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Foreground-only presentation of real alarm episodes. The coordinator owns all
 * alarm decisions; this class only observes saved active alarms for open UI.
 * No background Activity launch, polling, wake lock or second alarm engine.
 */
internal class CgmForegroundAlarmPresenter(
    private val application: Application,
) :
    Application.ActivityLifecycleCallbacks,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val preferences = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val seenEpisodes = mutableSetOf<String>()
    private var resumedActivity: Activity? = null

    override fun onActivityResumed(activity: Activity) {
        // Never stack alarm screens on top of an existing alarm screen.
        if (activity is CgmAlarmActivity || activity.packageName != application.packageName) return
        resumedActivity = activity
        // Alarms that occurred while SugarWear was backgrounded remain available
        // through the notification; merely opening the app must not retrigger them.
        seenEpisodes.clear()
        seenEpisodes.addAll(activeNotices().map(::cgmAlarmEpisodeKey))
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumedActivity === activity) {
            resumedActivity = null
            preferences.unregisterOnSharedPreferenceChangeListener(this)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key != STATE_KEY && key != NOTICES_KEY) return
        // SharedPreferences callbacks may run during the coordinator's commit.
        // Defer until the entire alarm state and pending-notice snapshot is saved.
        mainHandler.post {
            val activity = resumedActivity ?: return@post
            if (activity.isFinishing || activity.isDestroyed) return@post
            val alarm =
                CgmForegroundAlarmPresentation.pickNext(seenEpisodes, activeNotices()) ?: return@post
            activity.startActivity(
                CgmAlarmActivity
                    .intent(activity, alarm.type)
                    .putExtra("episode", cgmAlarmEpisodeKey(alarm)),
            )
        }
    }

    private fun activeNotices(): List<SugarWearAlarmInstance> =
        CgmAlarmCoordinator.pendingNotices(application).filter {
            CgmAlarmCoordinator.isActive(application, it.type)
        }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit

    companion object {
        private const val PREFS = "g7_cgm_alarm_state"
        private const val STATE_KEY = "state_v2"
        private const val NOTICES_KEY = "pending_notices_v2"
    }
}

/** Highest priority fresh episode wins; repeats and CGM updates never reopen it. */
internal object CgmForegroundAlarmPresentation {
    fun pickNext(
        seenEpisodes: MutableSet<String>,
        activeNotices: List<SugarWearAlarmInstance>,
    ): SugarWearAlarmInstance? {
        val newAlarms = activeNotices.filter { cgmAlarmEpisodeKey(it) !in seenEpisodes }
        seenEpisodes.clear()
        seenEpisodes.addAll(activeNotices.map(::cgmAlarmEpisodeKey))
        return newAlarms.maxByOrNull { SugarWearAlarmDefaults.priority(it.type) }
    }
}
