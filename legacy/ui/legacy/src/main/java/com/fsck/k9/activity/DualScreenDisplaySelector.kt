package com.fsck.k9.activity

import android.hardware.display.DisplayManager
import android.view.Display

internal class DualScreenDisplaySelector(
    private val displayManager: DisplayManager,
    private val supportedProfiles: List<DualScreenDeviceProfile> = DualScreenDeviceProfiles.supported,
) {
    init {
        require(supportedProfiles.isNotEmpty())
    }

    fun findEligibleSecondaryDisplay(activityDisplayId: Int?): DualScreenDisplayTarget? {
        val eligibleProfiles = supportedProfiles.filter { profile -> profile.primaryDisplayId == activityDisplayId }
        if (eligibleProfiles.isEmpty()) return null

        val eligibleTargets = displayManager.getDisplays(
            DisplayManager.DISPLAY_CATEGORY_PRESENTATION,
        ).mapNotNull { display ->
            if (display.state != Display.STATE_ON || display.displayId == activityDisplayId) return@mapNotNull null

            eligibleProfiles.firstOrNull { profile ->
                profile.matchesSecondaryDisplay(
                    width = display.mode.physicalWidth,
                    height = display.mode.physicalHeight,
                    rotation = display.rotation,
                )
            }?.let { profile -> DualScreenDisplayTarget(display, profile) }
        }

        return eligibleTargets.firstOrNull { target ->
            target.display.displayId == target.deviceProfile.preferredSecondaryDisplayId
        } ?: eligibleTargets.firstOrNull()
    }
}

internal data class DualScreenDisplayTarget(
    val display: Display,
    val deviceProfile: DualScreenDeviceProfile,
)
