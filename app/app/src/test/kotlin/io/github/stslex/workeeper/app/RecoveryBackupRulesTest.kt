// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

internal class RecoveryBackupRulesTest {

    @Test
    fun `restore protocol DataStore is excluded from legacy cloud and device transfer`() {
        val legacy = parseXml("backup_rules.xml")
        assertExactRestoreStateExclude(legacy.documentElement)

        val extraction = parseXml("data_extraction_rules.xml")
        val cloud = extraction.getElementsByTagName("cloud-backup").item(0) as? Element
        val transfer = extraction.getElementsByTagName("device-transfer").item(0) as? Element
        assertNotNull(cloud, "API-31 cloud-backup rules must be explicit")
        assertNotNull(transfer, "API-31 device-transfer rules must be explicit")
        assertExactRestoreStateExclude(checkNotNull(cloud))
        assertExactRestoreStateExclude(checkNotNull(transfer))
    }

    @Test
    fun `FileProvider exposes only exercise images and narrow recovery share`() {
        val paths = parseXml("file_provider_paths.xml").documentElement
        val children = paths.childElements()

        assertEquals(
            listOf("exercise_images" to "exercise_images/"),
            children
                .filter { it.tagName == "files-path" }
                .map { it.getAttribute("name") to it.getAttribute("path") },
        )
        assertEquals(
            listOf("recovery_share" to "recovery_share/"),
            children
                .filter { it.tagName == "cache-path" }
                .map { it.getAttribute("name") to it.getAttribute("path") },
        )
        assertTrue(
            children.none { it.tagName != "files-path" && it.tagName != "cache-path" },
            "no broad root, files, noBackup, or external provider path may be added",
        )
    }

    private fun assertExactRestoreStateExclude(parent: Element) {
        assertEquals(
            listOf("file" to RESTORE_STATE_DATASTORE_PATH),
            parent.childElements()
                .filter { it.tagName == "exclude" }
                .map { it.getAttribute("domain") to it.getAttribute("path") },
        )
    }

    private fun parseXml(name: String) = DocumentBuilderFactory
        .newInstance()
        .newDocumentBuilder()
        .parse(xmlFile(name))

    private fun xmlFile(name: String): File = sequenceOf(
        File("src/main/res/xml/$name"),
        File("app/app/src/main/res/xml/$name"),
    ).firstOrNull(File::isFile)
        ?: error("missing Android XML source: $name")

    private fun Element.childElements(): List<Element> = buildList {
        val nodes = childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node is Element) add(node)
        }
    }

    private companion object {
        const val RESTORE_STATE_DATASTORE_PATH =
            "datastore/restore_state_prefs.preferences_pb"
    }
}
