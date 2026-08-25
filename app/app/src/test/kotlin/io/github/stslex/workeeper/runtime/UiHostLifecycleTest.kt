// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import android.app.Activity
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The host-teardown predicate `ComponentActivity` used before the §8.7 owner re-parenting: only
 * the LAST destroy counts, and a configuration recreate is never one.
 */
internal class UiHostLifecycleTest {

    private val attachments = UiHostAttachments()

    private fun host(): Any = Any()

    @Test
    fun `a configuration recreate is never a permanent teardown`() {
        val activity = host()
        attachments.attached(activity)

        assertFalse(attachments.detached(activity, changingConfigurations = true))
    }

    @Test
    fun `the last permanent destroy is a teardown`() {
        val activity = host()
        attachments.attached(activity)

        assertTrue(attachments.detached(activity, changingConfigurations = false))
    }

    @Test
    fun `a second live host vetoes the first host's destroy`() {
        val first = host()
        val second = host()
        attachments.attached(first)
        attachments.attached(second)

        assertFalse(
            attachments.detached(first, changingConfigurations = false),
            "clearing here would empty the store the surviving host is composing",
        )
        assertTrue(attachments.detached(second, changingConfigurations = false))
    }

    @Test
    fun `an unmatched detach cannot bias the baseline into clearing`() {
        // Identity accounting, not a counter: a missed attach degrades to a no-op remove.
        val known = host()
        attachments.attached(known)

        assertFalse(attachments.detached(host(), changingConfigurations = false))
        assertTrue(attachments.detached(known, changingConfigurations = false))
    }

    @Test
    fun `the tracker reads the Activity's own changingConfigurations flag`() {
        val destroys = mutableListOf<String>()
        val tracker = UiHostLifecycleTracker { destroys += "host-gone" }
        val recreated = mockk<Activity> { every { isChangingConfigurations } returns true }
        val finished = mockk<Activity> { every { isChangingConfigurations } returns false }

        tracker.onActivityCreated(recreated, null)
        tracker.onActivityDestroyed(recreated)
        assertEquals(emptyList<String>(), destroys, "a recreate must not clear the tree")

        tracker.onActivityCreated(finished, null)
        tracker.onActivityDestroyed(finished)
        assertEquals(listOf("host-gone"), destroys)
    }
}
