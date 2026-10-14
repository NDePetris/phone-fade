package app.ndplabs.phonefade

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class LauncherIconUiTest {
    @Test fun monochromeRoadAndBranchOverlapAreTransparent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icon = context.getDrawable(R.drawable.ic_launcher_monochrome)!!
        val bitmap = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 108, 108)
        icon.draw(Canvas(bitmap))
        fun alphaAt(x: Float, y: Float) = android.graphics.Color.alpha(bitmap.getPixel(
            (30 + x * .1875f).toInt(), (29.0625f + y * .1875f).toInt()))
        assertEquals(0, alphaAt(124f, 30f)) // Plain main road between dashes.
        assertEquals(0, alphaAt(124f, 180f)) // Main road/ramp overlap must not refill.
        assertTrue(alphaAt(45f, 115f) > 240) // Canonical arch ink remains.
    }
}
