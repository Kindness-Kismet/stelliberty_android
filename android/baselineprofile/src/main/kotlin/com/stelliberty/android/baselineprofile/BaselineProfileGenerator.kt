package com.stelliberty.android.baselineprofile

import android.os.Build
import android.os.SystemClock
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test

class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(
        packageName = BuildConfig.TARGET_APP_ID,
        includeInStartupProfile = true,
    ) {
        pressHome()
        device.grantNotificationPermission()
        device.resetToInterpreted()
        startActivityAndWait()
        device.waitForIdle()
        SystemClock.sleep(PROFILE_SAVER_DELAY_MS)
    }

    @Test
    fun tabs() = rule.collect(
        packageName = BuildConfig.TARGET_APP_ID,
        includeInStartupProfile = false,
    ) {
        pressHome()
        device.grantNotificationPermission()
        device.resetToInterpreted()
        startActivityAndWait()
        device.waitForIdle()
        repeat(TAB_COUNT) { index ->
            device.scrollContent()
            if (index < TAB_COUNT - 1) device.swipeToNextTab()
        }
    }

    private fun UiDevice.grantNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        executeShellCommand("pm grant ${BuildConfig.TARGET_APP_ID} android.permission.POST_NOTIFICATIONS")
    }

    private fun UiDevice.resetToInterpreted() {
        executeShellCommand("cmd package compile -f -m verify ${BuildConfig.TARGET_APP_ID}")
    }

    private fun UiDevice.scrollContent() {
        val x = displayWidth / 2
        swipe(x, displayHeight * 3 / 4, x, displayHeight / 4, SWIPE_STEPS)
        settle()
        swipe(x, displayHeight / 4, x, displayHeight * 3 / 4, SWIPE_STEPS)
        settle()
    }

    private fun UiDevice.swipeToNextTab() {
        val y = displayHeight / 2
        swipe(displayWidth * 4 / 5, y, displayWidth / 5, y, SWIPE_STEPS)
        settle()
    }

    private fun UiDevice.settle() {
        waitForIdle()
        SystemClock.sleep(SETTLE_MS)
    }

    private companion object {
        const val TAB_COUNT = 4
        const val SWIPE_STEPS = 10
        const val SETTLE_MS = 600L

        const val PROFILE_SAVER_DELAY_MS = 7000L
    }
}
