package com.fsck.k9.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateStartupProfile() {
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), APP_START_TIMEOUT_MILLIS)

            val messageList = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, "message_list")),
                APP_START_TIMEOUT_MILLIS,
            )
                ?: device.findObject(By.res(PACKAGE_NAME, "message_list_compose_view"))
            checkNotNull(messageList) {
                "Message list not found. Configure a release-like account before generating startup profiles."
            }
            messageList.fling(Direction.DOWN)
            device.waitForIdle()
        }
    }
}

internal const val PACKAGE_NAME = "com.fsck.k9"
private const val APP_START_TIMEOUT_MILLIS = 10_000L
