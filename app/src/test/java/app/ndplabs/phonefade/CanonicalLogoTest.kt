package app.ndplabs.phonefade

import java.io.File
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class CanonicalLogoTest {
    private val root get() = if (File("src/main/res").exists()) File("..") else File(".")
    private fun resource(name: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File(root, "app/src/main/res/drawable/$name.xml"))
    private val androidNs = "http://schemas.android.com/apk/res/android"

    @Test fun launcherForegroundIsExactFamilyVectorWithUniformSafePlacement() {
        val family = resource("ic_offramp_family")
        val launcher = resource("ic_launcher_foreground")
        val placement = launcher.getElementsByTagName("group").item(0) as Element
        assertEquals("108", launcher.documentElement.getAttributeNS(androidNs, "viewportWidth"))
        assertEquals("108", launcher.documentElement.getAttributeNS(androidNs, "viewportHeight"))
        assertEquals("0.1875", placement.getAttributeNS(androidNs, "scaleX"))
        assertEquals(placement.getAttributeNS(androidNs, "scaleX"), placement.getAttributeNS(androidNs, "scaleY"))
        val x = placement.getAttributeNS(androidNs, "translateX").toDouble()
        val y = placement.getAttributeNS(androidNs, "translateY").toDouble()
        val width = 256 * .1875; val height = 266 * .1875
        assertEquals(48.0, width, 0.0)
        assertEquals(54.0, x + width / 2, 0.0); assertEquals(54.0, y + height / 2, 0.0)
        assertTrue(x >= 21 && y >= 21 && x + width <= 87 && y + height <= 87)
        for (tag in listOf("path", "clip-path")) {
            val original = family.getElementsByTagName(tag); val converted = launcher.getElementsByTagName(tag)
            assertEquals(original.length, converted.length)
            for (index in 0 until original.length) {
                for (key in listOf("pathData", "fillColor", "strokeColor", "strokeWidth", "strokeLineCap"))
                    assertEquals((original.item(index) as Element).getAttributeNS(androidNs, key),
                        (converted.item(index) as Element).getAttributeNS(androidNs, key))
            }
        }
    }
    @Test fun monochromeCutsOutCanonicalRoadUnionAndRetainsCanonicalLaneMarks() {
        val family = resource("ic_offramp_family").getElementsByTagName("path")
        val mono = resource("ic_launcher_monochrome")
        val clips = mono.getElementsByTagName("clip-path")
        assertEquals(3, clips.length)
        for ((index, originalIndex) in listOf(1 to 1, 2 to 7)) {
            val clip = clips.item(index) as Element
            assertEquals("", clip.getAttributeNS(androidNs, "fillType"))
            assertEquals("M-100,-100 V400 H400 V-100 Z " + (family.item(originalIndex) as Element).getAttributeNS(androidNs, "pathData"),
                clip.getAttributeNS(androidNs, "pathData"))
        }
        val monochromePaths = mono.getElementsByTagName("path")
        val expected = (0 until family.length).map { family.item(it) as Element }
            .filterIndexed { index, element -> index == 0 || element.getAttributeNS(androidNs, "strokeLineCap") == "round" }
        assertEquals(expected.size, monochromePaths.length)
        for (index in expected.indices) {
            val path = monochromePaths.item(index) as Element
            assertEquals(expected[index].getAttributeNS(androidNs, "pathData"), path.getAttributeNS(androidNs, "pathData"))
            val ink = path.getAttributeNS(androidNs, if (index == 0) "fillColor" else "strokeColor")
            assertEquals("#FFFFFFFF", ink)
        }
    }
    @Test fun androidVectorPreservesCanonicalPathsClipColorsAndStrokeGeometry() {
        val root = if (File("src/main/res").exists()) File("..") else File(".")
        val source = File(root, "design/assets/offramp-primary.svg")
        val hash = MessageDigest.getInstance("SHA-256").digest(source.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals("486aff70b2b4c2ae7b3cafa10da8822beca150e52b35bd54fff9f6ff7268ffe7", hash)
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val svg = factory.newDocumentBuilder().parse(source)
        val vector = factory.newDocumentBuilder().parse(File(root, "app/src/main/res/drawable/ic_offramp_family.xml"))
        val svgPaths = svg.getElementsByTagName("path")
        val androidPaths = vector.getElementsByTagName("path")
        assertEquals(svgPaths.length - 1, androidPaths.length) // Clip is a separate Android element.
        val androidNs = "http://schemas.android.com/apk/res/android"
        val clip = vector.getElementsByTagName("clip-path").item(0) as Element
        assertEquals((svgPaths.item(0) as Element).getAttribute("d"), clip.getAttributeNS(androidNs, "pathData"))
        val svgRoot = svg.documentElement
        assertEquals("0 0 256 266", svgRoot.getAttribute("viewBox"))
        assertEquals("256", vector.documentElement.getAttributeNS(androidNs, "viewportWidth"))
        assertEquals("266", vector.documentElement.getAttributeNS(androidNs, "viewportHeight"))
        for (index in 0 until androidPaths.length) {
            val original = svgPaths.item(index + 1) as Element
            val converted = androidPaths.item(index) as Element
            assertEquals(original.getAttribute("d"), converted.getAttributeNS(androidNs, "pathData"))
            for ((svgKey, androidKey) in listOf("fill" to "fillColor", "stroke" to "strokeColor",
                "stroke-width" to "strokeWidth", "stroke-linecap" to "strokeLineCap")) {
                val own = original.getAttribute(svgKey)
                val inherited = if (own.isNotEmpty()) own else (original.parentNode as Element).getAttribute(svgKey)
                assertEquals(if (inherited == "none") "" else inherited, converted.getAttributeNS(androidNs, androidKey))
            }
        }
    }
}
