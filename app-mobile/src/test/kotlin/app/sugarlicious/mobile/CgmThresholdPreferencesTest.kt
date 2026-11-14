package app.sugarlicious.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.CgmRangeClass
import app.sugarlicious.model.CgmThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CgmThresholdPreferencesTest {
    private val preferences =
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSharedPreferences("threshold_policy_test", Context.MODE_PRIVATE)

    @Test fun `defaults and valid policy persist canonically`() {
        preferences.edit().clear().commit()
        assertEquals(CgmThresholds.DEFAULT, CgmThresholdPreferences.read(preferences))
        val changed = CgmThresholds(veryHighMgDl = 260.0, highMgDl = 160.0, lowMgDl = 80.0, veryLowMgDl = 45.0)
        assertTrue(CgmThresholdPreferences.save(preferences, changed))
        assertEquals(changed, CgmThresholdPreferences.read(preferences))
        assertEquals(CgmRangeClass.HIGH, changed.classify(160.0))
    }

    @Test fun `invalid edit is rejected without changing stored values`() {
        preferences.edit().clear().commit()
        val original = CgmThresholds.DEFAULT
        assertTrue(CgmThresholdPreferences.save(preferences, original))
        assertFalse(CgmThresholdPreferences.save(preferences, original.copy(lowMgDl = 190.0)))
        assertEquals(original, CgmThresholdPreferences.read(preferences))
    }

    @Test fun `mmol conversion round trips through canonical mg dl`() {
        val displayed = thresholdForUi(180.0, DisplayUnitPreference.MMOL_L)
        assertEquals(10.0f, displayed, 0.001f)
        assertEquals(180.0, thresholdFromUi(displayed, DisplayUnitPreference.MMOL_L), 0.001)
    }
}
