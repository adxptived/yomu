package app.yomu.feature.library

import androidx.compose.ui.geometry.Offset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The game wheel picks a sector from the direction of the drag, not from hitting a button. */
class RadialSectorTest {
    private val center = Offset(100f, 100f)
    private val deadZone = 30f

    @Test
    fun fourActionsGoClockwiseFromTheTop() {
        assertEquals(0, radialSector(4, center, Offset(100f, 10f), deadZone))
        assertEquals(1, radialSector(4, center, Offset(190f, 100f), deadZone))
        assertEquals(2, radialSector(4, center, Offset(100f, 190f), deadZone))
        assertEquals(3, radialSector(4, center, Offset(10f, 100f), deadZone))
        // Up and to the right is still the first sector.
        assertEquals(0, radialSector(4, center, Offset(150f, 30f), deadZone))
        // Down and to the left is the fourth.
        assertEquals(3, radialSector(4, center, Offset(30f, 150f), deadZone))
    }

    @Test
    fun sevenActionsSpreadEvenlyAroundTheRing() {
        // -90° (top), then every 51.4° clockwise.
        assertEquals(0, radialSector(7, center, Offset(100f, 10f), deadZone))
        assertEquals(2, radialSector(7, center, Offset(190f, 100f), deadZone))
        assertEquals(4, radialSector(7, center, Offset(100f, 190f), deadZone))
        // Almost straight left lands on the sixth sector (the seventh points down-left).
        assertEquals(5, radialSector(7, center, Offset(10f, 110f), deadZone))
    }

    @Test
    fun theMiddleIsNoChoiceAndCancels() {
        assertNull(radialSector(7, center, Offset(100f, 100f), deadZone))
        assertNull(radialSector(7, center, Offset(115f, 110f), deadZone))
        // Just outside the dead zone already counts.
        assertEquals(0, radialSector(7, center, Offset(100f, 60f), deadZone))
    }

    @Test
    fun oneActionFillsTheWholeRing() {
        assertEquals(0, radialSector(1, center, Offset(100f, 10f), deadZone))
        assertEquals(0, radialSector(1, center, Offset(10f, 100f), deadZone))
        assertNull(radialSector(0, center, Offset(10f, 100f), deadZone))
    }

    /** The ring stays under the finger in the middle of the screen and near an edge it moves just enough to fit. */
    @Test
    fun theWheelStaysOnScreenWithoutLosingTheFinger() {
        val radius = 200f
        val button = 100f
        val margin = 20f
        val reach = radius + button / 2 + margin

        val middle = wheelCenter(Offset(360f, 640f), radius, button, margin, 720f, 1280f)
        assertEquals(360f, middle.x)
        assertEquals(640f, middle.y)

        val corner = wheelCenter(Offset(10f, 10f), radius, button, margin, 720f, 1280f)
        assertEquals(reach, corner.x)
        assertEquals(reach, corner.y)

        val bottomRight = wheelCenter(Offset(700f, 1270f), radius, button, margin, 720f, 1280f)
        assertEquals(720f - reach, bottomRight.x)
        assertEquals(1280f - reach, bottomRight.y)

        // A short screen cannot fit the whole ring: it is centred instead of pushed off both sides.
        val tiny = wheelCenter(Offset(10f, 10f), radius, button, margin, 300f, 300f)
        assertEquals(150f, tiny.x)
        assertEquals(150f, tiny.y)
    }
}
