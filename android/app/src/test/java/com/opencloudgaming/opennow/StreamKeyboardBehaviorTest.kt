package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamKeyboardBehaviorTest {
    @Test
    fun emptyNewDraftHasNothingToType() {
        assertEquals(StreamKeyboardEdit.None, streamKeyboardEdit(null, ""))
    }

    @Test
    fun newDraftIsAppendedToTheRemoteField() {
        assertEquals(StreamKeyboardEdit.Append("hello"), streamKeyboardEdit(null, "hello"))
    }

    @Test
    fun unchangedMirroredTextIsNotDuplicated() {
        assertEquals(StreamKeyboardEdit.None, streamKeyboardEdit("hello", "hello"))
    }

    @Test
    fun typingAtTheEndOnlyAppendsTheNewSuffix() {
        assertEquals(StreamKeyboardEdit.Append(" there"), streamKeyboardEdit("hello", "hello there"))
    }

    @Test
    fun deletingAtTheEndUsesBackspace() {
        assertEquals(StreamKeyboardEdit.Backspace(2), streamKeyboardEdit("hello", "hel"))
    }

    @Test
    fun deletingAnEmojiUsesOneRemoteBackspace() {
        assertEquals(StreamKeyboardEdit.Backspace(1), streamKeyboardEdit("hello 🙂", "hello "))
    }

    @Test
    fun editingInTheMiddleReplacesTheRemoteField() {
        assertEquals(StreamKeyboardEdit.Replace("hallo"), streamKeyboardEdit("hello", "hallo"))
    }
}
