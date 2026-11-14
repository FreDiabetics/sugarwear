package app.sugarwear

import android.content.Context
import android.media.AudioManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CgmAlarmVolumeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audio get() = context.getSystemService(AudioManager::class.java)

    @Before fun setup() {
        CgmAlarmVolume.restore(context)
        context
            .getSharedPreferences("g7_alarm_volume", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 2, 0)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        context.getSystemService(android.app.NotificationManager::class.java).setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    @Test fun `silent mode is temporarily audible and original state is restored`() {
        CgmAlarmVolume.configureSilentOverride(context, true)
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        val media = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        CgmAlarmVolume.beforeSound(context)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
        org.junit.Assert.assertTrue(audio.getStreamVolume(AudioManager.STREAM_ALARM) > 0)
        assertEquals(media, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31))
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
        assertEquals(0, audio.getStreamVolume(AudioManager.STREAM_ALARM))
    }

    @Test fun `manual ringer mode changes win over silent override restoration`() {
        CgmAlarmVolume.configureSilentOverride(context, true)
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        CgmAlarmVolume.beforeSound(context)
        audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        CgmAlarmVolume.restore(context)
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, audio.ringerMode)
    }

    @Test fun `total silence policy is never disabled by an override`() {
        val notifications = context.getSystemService(android.app.NotificationManager::class.java)
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        notifications.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_NONE)
        CgmAlarmVolume.configure(context, true, 100)
        CgmAlarmVolume.beforeSound(context)
        assertEquals(android.app.NotificationManager.INTERRUPTION_FILTER_NONE, notifications.currentInterruptionFilter)
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
        assertEquals(2, audio.getStreamVolume(AudioManager.STREAM_ALARM))
    }

    @Test fun `disabled silent override leaves a muted watch unchanged`() {
        CgmAlarmVolume.configureSilentOverride(context, false)
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        assertEquals(CgmAlarmVolumeResult.SYSTEM_VOLUME, CgmAlarmVolume.beforeSound(context))
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
        assertEquals(0, audio.getStreamVolume(AudioManager.STREAM_ALARM))
    }

    @Test fun `overlapping silent alarms keep the original mode until the last window ends`() {
        CgmAlarmVolume.configureSilentOverride(context, true)
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        CgmAlarmVolume.beforeSound(context)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        CgmAlarmVolume.beforeSound(context)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
    }

    @Test fun `silent override requires explicit opt in`() {
        org.junit.Assert.assertFalse(CgmAlarmVolume.silentOverrideEnabled(context))
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        assertEquals(CgmAlarmVolumeResult.SYSTEM_VOLUME, CgmAlarmVolume.beforeSound(context))
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
        assertEquals(0, audio.getStreamVolume(AudioManager.STREAM_ALARM))
    }

    @Test fun `default respects system alarm volume`() {
        CgmAlarmVolume.beforeSound(context)
        assertEquals(2, audio.getStreamVolume(AudioManager.STREAM_ALARM))
    }

    @Test fun `override restores original after playback window without changing media stream`() {
        val media = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        CgmAlarmVolume.configure(context, true, 100)
        CgmAlarmVolume.beforeSound(context)
        assertEquals(audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), audio.getStreamVolume(AudioManager.STREAM_ALARM))
        assertEquals(media, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31))
        assertEquals(2, audio.getStreamVolume(AudioManager.STREAM_ALARM))
    }

    @Test fun `overlapping alarms keep original volume and extend restoration`() {
        CgmAlarmVolume.configure(context, true, 100)
        CgmAlarmVolume.beforeSound(context)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        CgmAlarmVolume.beforeSound(context)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        assertEquals(audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), audio.getStreamVolume(AudioManager.STREAM_ALARM))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        assertEquals(2, audio.getStreamVolume(AudioManager.STREAM_ALARM))
    }

    @Test fun `manual volume adjustment wins over restoration`() {
        CgmAlarmVolume.configure(context, true, 100)
        CgmAlarmVolume.beforeSound(context)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 1, 0)
        CgmAlarmVolume.restore(context)
        assertEquals(1, audio.getStreamVolume(AudioManager.STREAM_ALARM))
    }

    @Test fun `disabling override immediately restores volume`() {
        CgmAlarmVolume.configure(context, true, 100)
        CgmAlarmVolume.beforeSound(context)
        CgmAlarmVolume.configure(context, false)
        assertEquals(2, audio.getStreamVolume(AudioManager.STREAM_ALARM))
    }
}
