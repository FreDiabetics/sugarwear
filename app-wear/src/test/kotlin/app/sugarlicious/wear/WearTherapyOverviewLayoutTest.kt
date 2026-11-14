package app.sugarlicious.wear

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WearTherapyOverviewLayoutTest {
    @Test
    fun `long precise therapy values fit the ring at normal and large system text`() {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        listOf(1f, 2f).forEach { scale ->
            val context =
                base.createConfigurationContext(
                    android.content.res
                        .Configuration(base.resources.configuration)
                        .apply { fontScale = scale },
                )
            val root = LayoutInflater.from(context).inflate(R.layout.activity_wear, null)
            configureTherapyOverviewLayout(root, 226)
            val values = listOf(R.id.wear_iob, R.id.wear_cob, R.id.wear_basal).map { root.findViewById<TextView>(it) }
            values.forEach { it.text = "123.45U" }
            val width = (226 * context.resources.displayMetrics.density).toInt()
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, 1600)
            values.forEach { value ->
                assertEquals("123.45U", value.text.toString())
                assertEquals(1, value.layout.lineCount)
                assertEquals(value.text.length, value.layout.getLineEnd(0))
                assertTrue("precise value must fit at font scale $scale", value.paint.measureText(value.text.toString()) <= value.width + 1f)
            }
        }
    }

    @Test
    fun `visible auxiliary IOB stays outside the core ring and preserves centered value`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = LayoutInflater.from(context).inflate(R.layout.activity_wear, null)
        configureTherapyOverviewLayout(root, 226)
        val secondary = root.findViewById<TextView>(R.id.wear_iob_secondary)
        secondary.text = "Bolus 1.25U"
        secondary.visibility = View.VISIBLE
        val value = root.findViewById<TextView>(R.id.wear_iob)
        val ring = root.findViewById<View>(R.id.wear_iob_progress)
        assertSame(ring.parent, value.parent)
        assertNotSame(ring.parent, secondary.parent)
        root.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 480, 1600)
        assertEquals(ring.height / 2f, value.top + value.height / 2f, context.resources.displayMetrics.density)
        assertEquals(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 14f, context.resources.displayMetrics), value.textSize, .1f)
    }

    @Test
    fun `large system text stacks therapy cards and grows their rings`() {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        val configuration =
            android.content.res
                .Configuration(base.resources.configuration)
                .apply { fontScale = 2f }
        val context = base.createConfigurationContext(configuration)
        val root = LayoutInflater.from(context).inflate(R.layout.activity_wear, null)
        configureTherapyOverviewLayout(root, 226)
        assertEquals(LinearLayout.VERTICAL, root.findViewById<LinearLayout>(R.id.wear_therapy_row).orientation)
        assertEquals(LinearLayout.VERTICAL, root.findViewById<LinearLayout>(R.id.wear_basal_card).orientation)
        val ring = root.findViewById<View>(R.id.wear_iob_progress).parent as View
        assertEquals((132 * context.resources.displayMetrics.density).toInt(), ring.layoutParams.width)
        root.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 480, 480)
        val details = root.findViewById<TextView>(R.id.wear_basal_base)
        assertTrue(details.width > 0)
    }

    @Test
    fun `empty secondary rows do not pull therapy values above ring centers`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = LayoutInflater.from(context).inflate(R.layout.activity_wear, null)
        listOf(R.id.wear_iob to "2.14U", R.id.wear_cob to "0g", R.id.wear_basal to "0.39U/h").forEach { (id, value) -> root.findViewById<TextView>(id).text = value }
        root.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 480, 1600)
        listOf(R.id.wear_iob to R.id.wear_iob_progress, R.id.wear_cob to R.id.wear_cob_progress, R.id.wear_basal to R.id.wear_basal_progress).forEach { (valueId, ringId) ->
            val value = root.findViewById<TextView>(valueId)
            val ring = root.findViewById<View>(ringId)
            val valueBounds = Rect(0, 0, value.width, value.height).also { (root as ViewGroup).offsetDescendantRectToMyCoords(value, it) }
            val ringBounds = Rect(0, 0, ring.width, ring.height).also { (root as ViewGroup).offsetDescendantRectToMyCoords(ring, it) }
            assertEquals("value $valueId should be vertically centered inside its ring", ringBounds.exactCenterY(), valueBounds.exactCenterY(), context.resources.displayMetrics.density)
        }
    }

    @Test
    fun `basal details fit a small round watch without losing rate or timing`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = LayoutInflater.from(context).inflate(R.layout.activity_wear, null)
        val text = root.findViewById<TextView>(R.id.wear_basal_secondary)
        configureTherapyOverviewLayout(root, 192)
        text.text = "3,4U/h 120/300 m"
        val width = (192 * context.resources.displayMetrics.density).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, 1600)
        val layout = text.layout
        assertNotNull(layout)
        assertEquals(text.text.length, layout.getLineEnd(layout.lineCount - 1))
        (0 until layout.lineCount).forEach { assertTrue("line must stay inside its real column", layout.getLineWidth(it) <= text.width - text.compoundPaddingLeft - text.compoundPaddingRight + 1) }
    }

    @Test
    fun `IOB and COB are equal square cards side by side with Mobile sized rings`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = LayoutInflater.from(context).inflate(R.layout.activity_wear, null)
        val row = root.findViewById<LinearLayout>(R.id.wear_therapy_row)
        assertEquals(LinearLayout.HORIZONTAL, row.orientation)
        root.measure(View.MeasureSpec.makeMeasureSpec(454, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1500, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 454, 1500)
        val iob = root.findViewById<View>(R.id.wear_iob_card)
        val cob = root.findViewById<View>(R.id.wear_cob_card)
        assertEquals(iob.width, iob.height)
        assertEquals(iob.width, cob.width)
        assertEquals(cob.width, cob.height)
        assertEquals((66 * context.resources.displayMetrics.density).toInt(), root.findViewById<View>(R.id.wear_iob_progress).width)
    }
}
