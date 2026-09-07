package com.fsck.k9.activity

import android.view.View
import net.thunderbird.feature.navigation.drawer.api.R

internal object SmartDualScreenLayoutConfigurator {
    fun apply(rootView: View, deviceProfile: DualScreenDeviceProfile) {
        val drawerContent = checkNotNull(rootView.findViewById<View>(R.id.navigation_drawer_content))
        drawerContent.layoutParams = drawerContent.layoutParams.apply {
            height = deviceProfile.logicalViewportHeight
        }
    }
}
