package app.ndplabs.phonefade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

/** Static, bounded landscape. Coordinates describe a 360 x 280 illustration plane.
 * Hills and the road use quiet tonal shades; a canvas-colored veil protects the reading area.
 * No state, animation, timers, bitmap mockup, or display-setting access lives here.
 */
@Composable
internal fun QuietRoadLandscape(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val dark = colors == QuietRoadDark
    val paper = colors.primaryContainer
    val distant = if (dark) Color(0xFF355448) else Color(0xFFD0DFD2)
    val hill = if (dark) Color(0xFF3C5F53) else Color(0xFFB8CCBF)
    val near = if (dark) Color(0xFF304A40) else Color(0xFFD3DECC)
    val road = if (dark) Color(0xFF406679) else Color(0xFFB4D4E2)
    val lane = if (dark) Color(0xFF93B7C7) else Color(0xFFF6F3EC)
    Canvas(modifier.fillMaxWidth().height(280.dp)) {
        scale(size.width / 360f, size.height / 280f, pivot = Offset.Zero) {
            val cloud = Path().apply {
                moveTo(270f, 51f); cubicTo(281f, 41f, 283f, 40f, 292f, 44f)
                cubicTo(296f, 27f, 315f, 27f, 320f, 43f)
                cubicTo(336f, 37f, 343f, 48f, 345f, 53f); close()
            }
            drawPath(cloud, distant)
            drawPath(Path().apply {
                moveTo(135f, 180f); cubicTo(197f, 123f, 238f, 142f, 272f, 98f)
                cubicTo(304f, 56f, 335f, 99f, 360f, 70f)
                lineTo(360f, 280f); lineTo(135f, 280f); close()
            }, distant)
            drawPath(Path().apply {
                moveTo(106f, 246f); cubicTo(184f, 171f, 231f, 198f, 295f, 139f)
                cubicTo(321f, 113f, 341f, 101f, 360f, 106f)
                lineTo(360f, 280f); lineTo(106f, 280f); close()
            }, hill)
            drawPath(Path().apply {
                moveTo(182f, 280f); cubicTo(248f, 200f, 283f, 217f, 360f, 202f)
                lineTo(360f, 280f); close()
            }, near)
            // Curved road broadens toward the foreground, with a paper shoulder.
            val ribbon = Path().apply {
                moveTo(285f, 290f); cubicTo(248f, 219f, 260f, 181f, 368f, 134f)
                lineTo(368f, 172f); cubicTo(290f, 207f, 302f, 241f, 347f, 290f); close()
            }
            drawPath(ribbon, lane, style = Stroke(width = 5f))
            drawPath(ribbon, road)
            drawPath(Path().apply {
                moveTo(316f, 286f); cubicTo(268f, 218f, 282f, 196f, 365f, 153f)
            }, lane, style = Stroke(width = 2.5f, cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f))))
            for ((x, y, h) in listOf(Triple(238f, 164f, 22f), Triple(251f, 168f, 30f), Triple(264f, 170f, 24f))) {
                val tree = Path().apply {
                    moveTo(x, y); lineTo(x + 4f, y - h)
                    lineTo(x + 8f, y); close()
                }
                drawPath(tree, if (dark) Color(0xFF638675) else Color(0xFF8FAE9D))
            }
        }
        drawRect(Brush.horizontalGradient(0f to paper, .48f to paper,
            .82f to paper.copy(alpha = .30f), 1f to paper.copy(alpha = .08f)))
        drawRect(Brush.verticalGradient(0f to Color.Transparent, .78f to Color.Transparent, 1f to paper))
    }
}
