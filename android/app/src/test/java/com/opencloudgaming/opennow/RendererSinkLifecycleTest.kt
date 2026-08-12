package com.opencloudgaming.opennow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererSinkLifecycleTest {
    @Test
    fun repeatedSurfaceCallbacksQueueOnlyOneAttachAndDetach() {
        val lifecycle = RendererSinkLifecycle()

        assertTrue(lifecycle.requestAttach())
        assertFalse(lifecycle.requestAttach())
        assertTrue(lifecycle.isAttachRequested())

        assertTrue(lifecycle.requestDetach())
        assertFalse(lifecycle.requestDetach())
        assertFalse(lifecycle.isAttachRequested())
    }

    @Test
    fun recreatedSurfaceCanAttachAfterDetach() {
        val lifecycle = RendererSinkLifecycle()

        assertTrue(lifecycle.requestAttach())
        assertTrue(lifecycle.requestDetach())
        assertTrue(lifecycle.requestAttach())
        assertTrue(lifecycle.isAttachRequested())
    }
}
