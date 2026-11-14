package app.sugarlicious.wear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.TherapyMetricAppearance
import app.sugarlicious.model.TherapyProgressAppearance
import app.sugarlicious.model.TherapyProgressMetric
import app.sugarlicious.protocol.WatchUiColors
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WearSettingsRepositoryTest {
    @Test fun `adapter uses existing display and tile stores with independent palettes`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val repository = AndroidWearSettingsRepository(context)
            repository.execute(AppearanceMode.DARK, WearSettingsWrite.Display(WearDisplayPreferences().copy(graphHours = 6)))
            repository.execute(AppearanceMode.DARK, WearSettingsWrite.TileColors(WearTileKind.GLUCOSE, WatchUiColors().copy(background = 0xff123456.toInt())))
            repository.execute(AppearanceMode.LIGHT, WearSettingsWrite.TileColors(WearTileKind.GLUCOSE, WatchUiColors().copy(background = 0xffabcdef.toInt())))
            repository.execute(AppearanceMode.DARK, WearSettingsWrite.TileContent(WearTileKind.GLUCOSE, WearTileContent.GRAPH))
            val therapy =
                TherapyProgressAppearance(
                    enabledMetrics = setOf(TherapyProgressMetric.COB),
                    groupScalePercent = 120,
                    metricOverrides =
                        TherapyProgressMetric.entries.associateWith { metric ->
                            TherapyMetricAppearance(ringThicknessPercent = if (metric == TherapyProgressMetric.COB) 130 else 100)
                        },
                )
            repository.execute(AppearanceMode.DARK, WearSettingsWrite.Therapy(therapy))
            repository.execute(AppearanceMode.LIGHT, WearSettingsWrite.ActivateAppearance)

            val dark = repository.load(AppearanceMode.DARK)
            val light = repository.load(null)
            assertEquals(6, dark.preferences.graphHours)
            assertEquals(WearDisplayPreferences.read(context, AppearanceMode.DARK), dark.preferences)
            assertEquals(0xff123456.toInt(), dark.tileColors.getValue(WearTileKind.GLUCOSE).background)
            assertEquals(0xffabcdef.toInt(), light.tileColors.getValue(WearTileKind.GLUCOSE).background)
            assertEquals(AppearanceMode.LIGHT, light.mode)
            assertEquals(WearTileContent.GRAPH, dark.tileContent.getValue(WearTileKind.GLUCOSE))
            assertEquals(therapy.validated(), dark.therapy)
            assertEquals(TherapyTileGeometryStore.read(context), dark.therapy)
        }
}
