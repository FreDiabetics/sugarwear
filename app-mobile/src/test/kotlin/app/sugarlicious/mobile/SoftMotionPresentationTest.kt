package app.sugarlicious.mobile

import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SoftMotionPresentationTest {
    @Test fun `age pill uses white bold numbers with secondary labels like delta`() {
        val primary = androidx.compose.ui.graphics.Color.White
        val secondary = androidx.compose.ui.graphics.Color.Gray
        val label = softMotionAgeText("65m", primary, secondary)
        assertEquals("vor 1 Std. 5 Min.", label.text)
        val numbers = label.spanStyles.filter { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold }
        assertEquals(listOf("1", "5"), numbers.map { label.text.substring(it.start, it.end) })
        assertTrue(numbers.all { it.item.color == primary })
        assertEquals(
            secondary,
            label.spanStyles
                .first()
                .item.color,
        )
    }

    @Test fun `header consumes canonical phone and watch states without treating stale data as current`() {
        val now = 1_800_000_000_000L
        for (source in listOf(app.sugarlicious.model.DataSourceId.ANDROID_APS, app.sugarlicious.model.DataSourceId.DEXCOM_G7_WATCH)) {
            val fresh =
                app.sugarlicious.model.TherapyDisplayState(
                    receivedAtEpochMs = now,
                    source = source,
                    glucose = app.sugarlicious.model.GlucoseState(120.0, app.sugarlicious.model.GlucoseUnit.MG_DL, measuredAtEpochMs = now, source = source),
                )
            assertTrue(softMotionHeaderStatus(fresh, now).hasValue)
            assertTrue(softMotionHeaderStatus(fresh, now).waveActive)
            val stale = softMotionHeaderStatus(fresh, now + 13 * 60_000L)
            assertTrue(stale.hasValue)
            assertFalse(stale.waveActive)
            assertNotNull(stale.label)
            val future = fresh.copy(glucose = fresh.glucose!!.copy(measuredAtEpochMs = now + 10 * 60_000L))
            assertFalse(softMotionHeaderStatus(future, now).hasValue)
        }
        assertFalse(softMotionHeaderStatus(null, now).hasValue)
        assertFalse(softMotionHeaderStatus(null, now).waveActive)
    }

    @Test fun `age remains explicit through hours days and unavailable data`() {
        assertEquals("vor 1 Min.", softMotionAge("1m"))
        assertEquals("vor 1 Std. 5 Min.", softMotionAge("65m"))
        assertEquals("vor 1 Tag", softMotionAge("1440m"))
        assertEquals("vor 2 Tagen", softMotionAge("2880m"))
        assertEquals("\u2014", softMotionAge("\u2014"))
        assertEquals("\u2014", softMotionAge("-1m"))
    }

    @Test fun `secondary scale labels keep the configured hue and only reduce emphasis`() {
        val selected = Color.argb(200, 210, 180, 150)
        assertEquals(selected, mobileScaleLabelColor(selected, true))
        val secondary = mobileScaleLabelColor(selected, false)
        assertEquals(Color.red(selected), Color.red(secondary))
        assertTrue(Color.alpha(secondary) < Color.alpha(selected))
    }
}
