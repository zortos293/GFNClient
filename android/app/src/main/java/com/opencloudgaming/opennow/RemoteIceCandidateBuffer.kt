package com.opencloudgaming.opennow

import org.webrtc.IceCandidate

/** Owned by the native lifecycle executor, alongside the peer and its remote description. */
internal class RemoteIceCandidateBuffer {
    private val pending = ArrayDeque<IceCandidate>()
    private var remoteDescriptionReady = false

    fun receive(candidate: IceCandidate, deliver: (IceCandidate) -> Unit) {
        if (remoteDescriptionReady) {
            deliver(candidate)
            return
        }
        if (pending.size == MAX_PENDING_CANDIDATES) pending.removeFirst()
        pending.addLast(candidate)
    }

    fun onRemoteDescriptionSet(deliver: (IceCandidate) -> Unit) {
        remoteDescriptionReady = true
        while (pending.isNotEmpty()) deliver(pending.removeFirst())
    }

    fun clear() {
        pending.clear()
        remoteDescriptionReady = false
    }

    private companion object {
        const val MAX_PENDING_CANDIDATES = 120
    }
}
