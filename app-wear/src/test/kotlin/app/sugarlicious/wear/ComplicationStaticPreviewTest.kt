package app.sugarlicious.wear

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.GoalProgressComplicationData
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.parser.StaticPreviewDataParser
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.sugarlicious.complications.AllProviders
import app.sugarlicious.complications.DateComplication
import app.sugarlicious.complications.GlucoseGraphLargeComplication
import app.sugarlicious.complications.TirWeightedElementsComplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComplicationStaticPreviewTest {
    @Test
    fun `picker labels describe content without numeric codes or Android type jargon`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val labels = mutableSetOf<String>()
        publicProviders().forEach { provider ->
            val info = context.packageManager.getServiceInfo(ComponentName(context, provider), 0)
            val label = info.loadLabel(context.packageManager).toString()
            assertTrue("Ambiguous duplicate picker label: $label", labels.add(label))
            assertFalse(label, Regex("^(\\d)|Short Text|Long Text|Ranged Value|Small Image|Goal Progress|Weighted Elements").containsMatchIn(label))
        }
    }

    private fun publicProviders(): List<Class<*>> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return AllProviders.classes.filter { provider ->
            !context.packageManager
                .getServiceInfo(ComponentName(context, provider), PackageManager.GET_META_DATA)
                .metaData
                .getBoolean("android.support.wearable.complications.HIDDEN", false)
        }
    }

    private val expectedTypes =
        listOf(
            ComplicationType.SHORT_TEXT,
            ComplicationType.LONG_TEXT,
            ComplicationType.RANGED_VALUE,
            ComplicationType.SHORT_TEXT,
            ComplicationType.LONG_TEXT,
            ComplicationType.RANGED_VALUE,
            ComplicationType.SHORT_TEXT,
            ComplicationType.LONG_TEXT,
            ComplicationType.SHORT_TEXT,
            ComplicationType.LONG_TEXT,
            ComplicationType.SHORT_TEXT,
            ComplicationType.SHORT_TEXT,
            ComplicationType.LONG_TEXT,
            ComplicationType.SMALL_IMAGE,
            ComplicationType.SMALL_IMAGE,
            ComplicationType.PHOTO_IMAGE,
            ComplicationType.SHORT_TEXT,
            ComplicationType.SHORT_TEXT,
            ComplicationType.SHORT_TEXT,
            ComplicationType.SHORT_TEXT,
            ComplicationType.SHORT_TEXT,
            ComplicationType.RANGED_VALUE,
            ComplicationType.SHORT_TEXT,
            ComplicationType.SHORT_TEXT,
            ComplicationType.RANGED_VALUE,
            ComplicationType.SHORT_TEXT,
            ComplicationType.RANGED_VALUE,
            ComplicationType.SHORT_TEXT,
            ComplicationType.LONG_TEXT,
            ComplicationType.SHORT_TEXT,
            ComplicationType.LONG_TEXT,
            ComplicationType.SHORT_TEXT,
            ComplicationType.MONOCHROMATIC_IMAGE,
            ComplicationType.SHORT_TEXT,
            ComplicationType.RANGED_VALUE,
            ComplicationType.SHORT_TEXT,
            ComplicationType.GOAL_PROGRESS,
            ComplicationType.WEIGHTED_ELEMENTS,
            ComplicationType.RANGED_VALUE,
            ComplicationType.SHORT_TEXT,
            ComplicationType.SHORT_TEXT,
            ComplicationType.SHORT_TEXT,
            ComplicationType.SHORT_TEXT,
        )

    @Test
    fun `all providers expose their declared dynamic preview type`() {
        assertEquals(43, publicProviders().size)
        assertEquals(publicProviders().size, expectedTypes.size)

        publicProviders().zip(expectedTypes).forEach { (provider, expectedType) ->
            val service = buildService(provider)
            assertEquals(provider.simpleName, expectedType, service.getPreviewData(expectedType)!!.type)
        }
    }

    @Test
    fun `picker resources are provider specific and match dynamic fields`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val staticResourceIds = mutableSetOf<Int>()
        val providerIconIds = mutableSetOf<Int>()

        publicProviders().zip(expectedTypes).forEach { (provider, expectedType) ->
            val component = ComponentName(context, provider)
            val serviceInfo = context.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)
            val iconId = serviceInfo.iconResource
            assertNotEquals("Missing provider icon for ${provider.simpleName}", 0, iconId)
            assertFalse("Shared provider icon for ${provider.simpleName}", providerIconIds.add(iconId).not())

            val supportsStaticParser =
                provider != GlucoseGraphLargeComplication::class.java &&
                    provider != TirWeightedElementsComplication::class.java
            val previewResourceId = serviceInfo.metaData?.getInt(STATIC_PREVIEW_KEY) ?: 0

            if (!supportsStaticParser) {
                assertEquals("Unsupported static type must use its dynamic provider preview", 0, previewResourceId)
                assertNull(StaticPreviewDataParser.parsePreviewData(context, component))
                return@forEach
            }

            assertNotEquals("Missing static preview for ${provider.simpleName}", 0, previewResourceId)
            assertFalse("Shared static preview for ${provider.simpleName}", staticResourceIds.add(previewResourceId).not())

            val staticPreview = StaticPreviewDataParser.parsePreviewData(context, component)
            assertNotNull("Unparseable static preview for ${provider.simpleName}", staticPreview)
            val staticData = staticPreview!![expectedType]
            assertNotNull("Wrong static type for ${provider.simpleName}", staticData)
            assertEquals(expectedType, staticData!!.type)

            val dynamicData = buildService(provider).getPreviewData(expectedType)!!
            assertMatchingFields(provider, staticData, dynamicData)
        }

        assertEquals(41, staticResourceIds.size)
        assertEquals(43, providerIconIds.size)
    }

    private fun assertMatchingFields(
        provider: Class<*>,
        staticData: ComplicationData,
        dynamicData: ComplicationData,
    ) {
        if (provider == DateComplication::class.java) {
            assertEquals(3, title(staticData)!!.length)
            text(staticData)!!.toInt()
        } else {
            assertEquals(
                "Text mismatch for ${provider.simpleName}",
                normalizedPreviewText(text(dynamicData)),
                normalizedPreviewText(text(staticData)),
            )
            assertEquals("Title mismatch for ${provider.simpleName}", title(dynamicData), title(staticData))
        }
        assertEquals(
            "Monochromatic image mismatch for ${provider.simpleName}",
            hasMonochromaticImage(dynamicData),
            hasMonochromaticImage(staticData),
        )
        assertEquals(
            "Small image mismatch for ${provider.simpleName}",
            dynamicData is SmallImageComplicationData,
            staticData is SmallImageComplicationData,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildService(provider: Class<*>): SuspendingComplicationDataSourceService =
        Robolectric
            .buildService(
                provider as Class<out SuspendingComplicationDataSourceService>,
            ).create()
            .get()

    private fun text(data: ComplicationData): String? =
        when (data) {
            is ShortTextComplicationData -> value(data.text)
            is LongTextComplicationData -> value(data.text)
            is RangedValueComplicationData -> data.text?.let(::value)
            is GoalProgressComplicationData -> data.text?.let(::value)
            else -> null
        }

    private fun title(data: ComplicationData): String? =
        when (data) {
            is ShortTextComplicationData -> data.title?.let(::value)
            is LongTextComplicationData -> data.title?.let(::value)
            is RangedValueComplicationData -> data.title?.let(::value)
            is GoalProgressComplicationData -> data.title?.let(::value)
            else -> null
        }

    private fun value(text: ComplicationText): String = text.getTextAt(ApplicationProvider.getApplicationContext<Context>().resources, Instant.now()).toString()

    private fun normalizedPreviewText(value: String?): String? {
        if (value?.endsWith('%') != true) return value
        return value.dropLast(1).toFloatOrNull()?.let { "${it.roundToInt()}%" } ?: value
    }

    private fun hasMonochromaticImage(data: ComplicationData): Boolean =
        when (data) {
            is ShortTextComplicationData -> data.monochromaticImage != null
            is LongTextComplicationData -> data.monochromaticImage != null
            is RangedValueComplicationData -> data.monochromaticImage != null
            is GoalProgressComplicationData -> data.monochromaticImage != null
            is MonochromaticImageComplicationData -> true
            else -> false
        }

    private companion object {
        const val STATIC_PREVIEW_KEY = "com.google.android.wearable.complications.STATIC_PREVIEW_DATA"
    }
}
