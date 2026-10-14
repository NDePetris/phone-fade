package app.ndplabs.phonefade

import java.io.File
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class CanonicalLogoTest {
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
