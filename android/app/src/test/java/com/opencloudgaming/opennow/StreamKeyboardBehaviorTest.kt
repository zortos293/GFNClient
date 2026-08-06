package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamKeyboardBehaviorTest {
    @Test
    fun emptyNewDraftHasNothingToType() {
        assertEquals(StreamKeyboardApplyAction.None, streamKeyboardApplyAction(null, ""))
    }

    @Test
    fun newDraftIsTypedIntoTheRemoteField() {
        assertEquals(StreamKeyboardApplyAction.Type, streamKeyboardApplyAction(null, "hello"))
    }

    @Test
    fun unchangedMirroredTextIsNotDuplicated() {
        assertEquals(StreamKeyboardApplyAction.None, streamKeyboardApplyAction("hello", "hello"))
    }

    @Test
    fun editedMirroredTextReplacesTheRemoteField() {
        assertEquals(StreamKeyboardApplyAction.Replace, streamKeyboardApplyAction("hello", "hello there"))
    }

    @Test
    fun clearingMirroredTextClearsTheRemoteField() {
        assertEquals(StreamKeyboardApplyAction.Replace, streamKeyboardApplyAction("hello", ""))
    }
}
