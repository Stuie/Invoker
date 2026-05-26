package ie.stu.invoker.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JavaDetectorTest {

    private val detector = JavaDetector(detectPlatform())

    @Test
    fun `Java 8 legacy version string yields 8`() {
        assertEquals(8, detector.parseMajorVersion("1.8.0_201"))
        assertEquals(8, detector.parseMajorVersion("1.8.0_362"))
    }

    @Test
    fun `Java 11 onward versions yield major as the leading number`() {
        assertEquals(11, detector.parseMajorVersion("11.0.5"))
        assertEquals(17, detector.parseMajorVersion("17.0.1"))
        assertEquals(21, detector.parseMajorVersion("21.0.5"))
    }

    @Test
    fun `Java 21 with build and LTS suffix still yields 21`() {
        assertEquals(21, detector.parseMajorVersion("21+35-LTS"))
        assertEquals(21, detector.parseMajorVersion("21.0.5+11"))
    }

    @Test
    fun `garbage or empty input returns null`() {
        assertNull(detector.parseMajorVersion(""))
        assertNull(detector.parseMajorVersion("garbage"))
        assertNull(detector.parseMajorVersion("not-a-version"))
    }
}
