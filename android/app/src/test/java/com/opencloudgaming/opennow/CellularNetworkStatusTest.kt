package com.opencloudgaming.opennow

import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import org.junit.Assert.assertEquals
import org.junit.Test

class CellularNetworkStatusTest {
    @Test
    fun carrierOverridesUseDisplayGeneration() {
        assertEquals("5G+", cellularGenerationLabel(TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED))
        assertEquals("5G", cellularGenerationLabel(TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA))
        assertEquals("LTE+", cellularGenerationLabel(TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA))
    }

    @Test
    fun baseRadioTypesUseCompactLabels() {
        assertEquals("5G", cellularGenerationLabel(TelephonyManager.NETWORK_TYPE_NR, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE))
        assertEquals("LTE", cellularGenerationLabel(TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE))
        assertEquals("H+", cellularGenerationLabel(TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE))
        assertEquals("3G", cellularGenerationLabel(TelephonyManager.NETWORK_TYPE_UMTS, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE))
        assertEquals("E", cellularGenerationLabel(TelephonyManager.NETWORK_TYPE_EDGE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE))
    }
}
