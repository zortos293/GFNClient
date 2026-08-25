package com.opencloudgaming.opennow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal const val ANDROID_LOCAL_NETWORK_PERMISSION_API = 37

internal fun androidLocalNetworkPermissionRequired(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    sdkInt >= ANDROID_LOCAL_NETWORK_PERMISSION_API

internal fun Context.hasAndroidLocalNetworkAccess(): Boolean =
    !androidLocalNetworkPermissionRequired() ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
        PackageManager.PERMISSION_GRANTED
