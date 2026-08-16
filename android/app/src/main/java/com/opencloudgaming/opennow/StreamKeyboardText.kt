package com.opencloudgaming.opennow

/** A minimal remote edit that keeps the host field aligned with the locally mirrored draft. */
internal sealed interface StreamKeyboardEdit {
    data object None : StreamKeyboardEdit
    data class Append(val text: String) : StreamKeyboardEdit
    data class Backspace(val count: Int) : StreamKeyboardEdit
    data class Replace(val text: String) : StreamKeyboardEdit
}

internal fun streamKeyboardEdit(syncedText: String?, draft: String): StreamKeyboardEdit {
    val previous = syncedText.orEmpty()
    return when {
        previous == draft -> StreamKeyboardEdit.None
        draft.startsWith(previous) -> StreamKeyboardEdit.Append(draft.removePrefix(previous))
        previous.startsWith(draft) -> StreamKeyboardEdit.Backspace(
            previous.codePointCount(draft.length, previous.length),
        )
        else -> StreamKeyboardEdit.Replace(draft)
    }
}
