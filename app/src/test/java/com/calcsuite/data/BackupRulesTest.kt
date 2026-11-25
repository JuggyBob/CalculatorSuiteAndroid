package com.calcsuite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses the shipped backup rule resources and checks that the DataStore files
 * holding cleartext history and the AI credential are actually excluded.
 */
class BackupRulesTest {

    private fun resource(name: String): File {
        val candidates = listOf(
            File("src/main/res/xml/$name"),
            File("app/src/main/res/xml/$name"),
            File(System.getProperty("user.dir"), "src/main/res/xml/$name"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("Could not find $name (looked in ${candidates.joinToString()})")
    }

    private fun document(name: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        return factory.newDocumentBuilder().parse(resource(name))
    }

    private fun collectExcludes(node: Node, out: MutableList<Pair<String, String>>) {
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val element = child as Element
            if (element.tagName == "exclude") {
                out += element.getAttribute("domain") to element.getAttribute("path")
            }
            collectExcludes(element, out)
        }
    }

    private fun exclusions(doc: Document, section: String? = null): List<Pair<String, String>> {
        val scope = if (section == null) {
            doc.documentElement
        } else {
            doc.getElementsByTagName(section).item(0)
                ?: throw AssertionError("Missing <$section> section")
        }
        val out = mutableListOf<Pair<String, String>>()
        collectExcludes(scope, out)
        return out
    }

    private fun assertDataStoreExcluded(rules: List<Pair<String, String>>) {
        assertEquals("Expected three exclusions, got $rules", 3, rules.size)
        assertTrue(
            "Every exclusion must use the file domain (filesDir): $rules",
            rules.all { it.first == "file" },
        )
        val paths = rules.map { it.second }.toSet()
        assertTrue("History store is not excluded: $rules", "datastore/calcsuite.preferences_pb" in paths)
        assertTrue("AI store is not excluded: $rules", "datastore/calcsuite_ai.preferences_pb" in paths)
        assertTrue("Datastore directory is not excluded: $rules", "datastore" in paths)
    }

    @Test fun legacyBackupRulesExcludeBothDataStores() {
        val doc = document("backup_rules.xml")
        assertEquals(0, doc.getElementsByTagName("include").length)
        assertDataStoreExcluded(exclusions(doc))
    }

    @Test fun extractionRulesExcludeDataStoresForCloudAndDeviceTransfer() {
        val doc = document("data_extraction_rules.xml")
        assertEquals(0, doc.getElementsByTagName("include").length)
        for (section in listOf("cloud-backup", "device-transfer")) {
            assertDataStoreExcluded(exclusions(doc, section))
        }
    }
}
