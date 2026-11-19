package app.sugarlicious.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucoseSample
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LongTermGlucoseArchiveTest {
    @Test fun `account switch removes only that source including invalid boundaries after reopening`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.deleteDatabase("measured_glucose_archive.db")
            val now = 1_800_000_000_000L
            val retained = GlucoseSample(100.0, now, source = DataSourceId.ANDROID_APS)
            LongTermGlucoseArchive(context).use { archive ->
                archive.record(listOf(retained, retained.copy(source = DataSourceId.DEXCOM_SHARE), retained.copy(source = DataSourceId.DEXCOM_SHARE, measuredAtEpochMs = now - 60_000, quality = CgmQuality.INVALID)), now)
                archive.removeSource(DataSourceId.DEXCOM_SHARE)
            }
            LongTermGlucoseArchive(context).use { archive ->
                assertEquals(listOf(retained), archive.read(now - 600_000, now))
            }
        }

    @Test fun `archive reopens retaining ninety days of valid history`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.deleteDatabase("measured_glucose_archive.db")
            val now = 1_800_000_000_000L
            val day = 86_400_000L
            val old = GlucoseSample(105.0, now - 89 * day)
            LongTermGlucoseArchive(context).use { archive ->
                archive.record(listOf(old, old, GlucoseSample(120.0, now - 93 * day), GlucoseSample(120.0, now + day), GlucoseSample(120.0, now - day, quality = CgmQuality.INVALID)), now)
            }
            LongTermGlucoseArchive(context).use { archive ->
                assertEquals(listOf(old), archive.read(now - 90 * day, now).filter { it.quality == CgmQuality.VALID })
                assertEquals(1, archive.read(now - 7 * day, now).count { it.quality == CgmQuality.INVALID })
                archive.record(emptyList(), now + 4 * day)
                assertTrue(archive.read(now - 90 * day, now + 4 * day).none { it.quality == CgmQuality.VALID })
            }
        }

    @Test fun `unchanged ingestion does not wake archive observers`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.deleteDatabase("measured_glucose_archive.db")
            val now = 1_800_000_000_000L
            val sample = GlucoseSample(105.0, now)
            LongTermGlucoseArchive(context).use { archive ->
                archive.record(listOf(sample), now)
                val revision = LongTermGlucoseArchive.revision.value
                archive.record(listOf(sample), now)
                assertEquals(revision, LongTermGlucoseArchive.revision.value)
            }
        }

    @Test fun `invalid boundaries survive reopening deduplicate and clear on validated correction`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.deleteDatabase("measured_glucose_archive.db")
            val now = 1_800_000_000_000L
            val invalid = GlucoseSample(Double.NaN, now - 120_000, quality = CgmQuality.INVALID)
            LongTermGlucoseArchive(context).use { archive ->
                archive.record(listOf(GlucoseSample(100.0, now - 300_000), invalid, GlucoseSample(100.0, now)), now)
                val revision = LongTermGlucoseArchive.revision.value
                archive.record(listOf(invalid), now)
                assertEquals(revision, LongTermGlucoseArchive.revision.value)
            }
            LongTermGlucoseArchive(context).use { archive ->
                val restored = archive.read(now - 600_000, now)
                assertEquals(1, restored.count { it.quality == CgmQuality.INVALID && it.valueMgDl.isNaN() })
                archive.record(listOf(invalid.copy(valueMgDl = 100.0, quality = CgmQuality.VALID)), now)
                assertTrue(archive.read(now - 600_000, now).all { it.quality == CgmQuality.VALID })
            }
        }
}
