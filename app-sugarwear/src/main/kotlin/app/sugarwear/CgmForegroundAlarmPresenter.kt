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
) : Application.ActivityLifecycleCallbacks,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val preferences = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val seenEpisodes = mutableSetOf<String>()
    private val appVisibility = CgmAlarmAppVisibility()
    private var resumedActivity: Activity? = null
    private var alarmLaunchPending = false

    override fun onActivityResumed(activity: Activity) {
        if (activity.packageName != application.packageName) return
        // Keep the seen-episode ledger while an alarm screen covers the foreground app.
        // A second alarm triggered in that interval must still be presented on return.
        if (activity is CgmAlarmActivity) {
            alarmLaunchPending = false
            return
        }
        resumedActivity = activity
        alarmLaunchPending = false
        if (appVisibility.consumeBaselineRequired()) {
            // A cold foreground entry must not reopen old background alarm episodes.
            seenEpisodes.clear()
            seenEpisodes.addAll(activeNotices().map(::cgmAlarmEpisodeKey))
        }
        preferences.registerOnSharedPreferenceChangeListener(this)
        // Also catch alarms saved while no ordinary Activity was resumed (e.g. when
        // a previous alarm screen was covering the UI or during navigation).
        mainHandler.post(::presentNextIfNeeded)
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
        mainHandler.post(::presentNextIfNeeded)
    }

    private fun presentNextIfNeeded() {
        if (alarmLaunchPending) return
        val activity = resumedActivity ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        val previousSeen = seenEpisodes.toSet()
        val alarm = CgmForegroundAlarmPresentation.pickNext(seenEpisodes, activeNotices()) ?: return
        alarmLaunchPending = true
        try {
            activity.startActivity(
                CgmAlarmActivity
                    .intent(activity, alarm.type)
                    .putExtra("episode", cgmAlarmEpisodeKey(alarm)),
            )
        } catch (error: RuntimeException) {
            // A rejected launch must not mark this alarm as already presented.
            seenEpisodes.clear()
            seenEpisodes.addAll(previousSeen)
            alarmLaunchPending = false
            android.util.Log.e("CgmForegroundAlarms", "Cannot present foreground alarm", error)
        }
    }

    private fun activeNotices(): List<SugarWearAlarmInstance> =
        CgmAlarmCoordinator.pendingNotices(application).filter {
            CgmAlarmCoordinator.isActive(application, it.type)
        }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) {
        if (activity.packageName == application.packageName) appVisibility.onStarted()
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityStopped(activity: Activity) {
        if (activity.packageName == application.packageName) appVisibility.onStopped()
    }

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

/**
 * Baseline existing alarm episodes only when the app actually returns from the
 * background. Navigation and the full-screen alarm Activity remain inside one
 * foreground session and must not consume newly triggered episodes.
 */
internal class CgmAlarmAppVisibility {
    private var startedActivities = 0
    private var baselineRequired = true

    fun onStarted() {
        if (startedActivities == 0) baselineRequired = true
        startedActivities++
    }

    fun onStopped() {
        if (startedActivities > 0) startedActivities--
    }

    fun consumeBaselineRequired(): Boolean {
        val required = baselineRequired
        baselineRequired = false
        return required
    }
}
