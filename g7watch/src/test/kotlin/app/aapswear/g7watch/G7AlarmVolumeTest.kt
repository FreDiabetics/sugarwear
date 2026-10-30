package app.aapswear.g7watch

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
class G7AlarmVolumeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audio get() = context.getSystemService(AudioManager::class.java)

    @Before fun setup() {
        G7AlarmVolume.restore(context)
        context
            .getSharedPreferences("g7_alarm_volume", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 2, 0)
    }

    @Test fun `default respects system alarm volume`() {
        G7AlarmVolume.beforeSound(context)
        assertEquals(2, audio.getStreamVolume(AudioManager.STREAM_ALARM))
    }

    @Test fun `override restores original after playback window without changing media stream`() {
        val media = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        G7AlarmVolume.configure(context, true, 100)
        G7AlarmVolume.beforeSound(context)
        assertEquals(audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), audio.getStreamVolume(AudioManager.STREAM_ALARM))
        assertEquals(media, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31))
        assertEquals(2, audio.getStreamVolume(AudioManager.STREAM_ALARM))
    }

    @Test fun `overlapping alarms keep original volume and extend restoration`() {
        G7AlarmVolume.configure(context, true, 100)
        G7AlarmVolume.beforeSound(context)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        G7AlarmVolume.beforeSound(context)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        assertEquals(audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), audio.getStreamVolume(AudioManager.STREAM_ALARM))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        assertEquals(2, audio.getStreamVolume(AudioManager.STREAM_ALARM))
    }

    @Test fun `manual volume adjustment wins over restoration`() {
        G7AlarmVolume.configure(context, true, 100)
        G7AlarmVolume.beforeSound(context)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 1, 0)
        G7AlarmVolume.restore(context)
        assertEquals(1, audio.getStreamVolume(AudioManager.STREAM_ALARM))
    }

    @Test fun `disabling override immediately restores volume`() {
        G7AlarmVolume.configure(context, true, 100)
        G7AlarmVolume.beforeSound(context)
        G7AlarmVolume.configure(context, false)
        assertEquals(2, audio.getStreamVolume(AudioManager.STREAM_ALARM))
    }
}
