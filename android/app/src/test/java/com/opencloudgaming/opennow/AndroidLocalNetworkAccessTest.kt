package com.opencloudgaming.opennow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidLocalNetworkAccessTest {
    @Test
    fun localNetworkPermissionStartsAtAndroid17() {
        assertFalse(androidLocalNetworkPermissionRequired(36))
        assertTrue(androidLocalNetworkPermissionRequired(37))
    }
}
