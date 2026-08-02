package dev.haasele.koma.app.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand built vectors for the few glyphs the core Material icon set does not ship, which keeps
 * the app off the pinned extended icon artifact.
 */
object KomaIcons {

    val Pause: ImageVector by lazy {
        icon("Pause") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 5f); lineTo(10f, 5f); lineTo(10f, 19f); lineTo(6f, 19f); close()
                moveTo(14f, 5f); lineTo(18f, 5f); lineTo(18f, 19f); lineTo(14f, 19f); close()
            }
        }
    }

    val Pulse: ImageVector by lazy {
        icon("Pulse") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(2f, 12.5f); lineTo(6.5f, 12.5f); lineTo(9f, 6f); lineTo(13f, 18f)
                lineTo(15.5f, 12.5f); lineTo(22f, 12.5f); lineTo(22f, 11.2f); lineTo(16.3f, 11.2f)
                lineTo(13.2f, 4.2f); lineTo(9.2f, 15.4f); lineTo(7.3f, 11.2f); lineTo(2f, 11.2f); close()
            }
        }
    }

    val Layers: ImageVector by lazy {
        icon("Layers") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f); lineTo(21f, 8f); lineTo(12f, 13f); lineTo(3f, 8f); close()
                moveTo(3f, 12f); lineTo(5.2f, 13.2f); lineTo(12f, 17f); lineTo(18.8f, 13.2f)
                lineTo(21f, 12f); lineTo(21f, 13.6f); lineTo(12f, 18.6f); lineTo(3f, 13.6f); close()
                moveTo(3f, 16.4f); lineTo(12f, 21.4f); lineTo(21f, 16.4f); lineTo(21f, 18f)
                lineTo(12f, 23f); lineTo(3f, 18f); close()
            }
        }
    }

    val Wrench: ImageVector by lazy {
        icon("Wrench") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20.5f, 6.2f); lineTo(17.3f, 9.4f); lineTo(14.6f, 6.7f); lineTo(17.8f, 3.5f)
                curveTo(15.6f, 2.8f, 13.1f, 3.3f, 11.4f, 5.1f)
                curveTo(9.6f, 6.8f, 9.1f, 9.4f, 9.9f, 11.6f)
                lineTo(3.2f, 18.3f); lineTo(5.7f, 20.8f); lineTo(12.4f, 14.1f)
                curveTo(14.6f, 14.9f, 17.2f, 14.4f, 18.9f, 12.6f)
                curveTo(20.7f, 10.9f, 21.2f, 8.4f, 20.5f, 6.2f); close()
            }
        }
    }

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()
}
