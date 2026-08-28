package com.jetgo.tv

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import com.jetgo.tv.util.AccessCodeChecker

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE)
class HeartbeatTest {

    @Test
    fun testHeartbeat() {
        ShadowLog.stream = System.out
        val projectId = "jetgo-f0127"
        val code = "TEST1234"
        // I will just execute it. If it fails, I'll see the error in Logcat since I modified it to log using Android Log,
        // but Robolectric might not print Android Logs by default. Let's just catch and print.
        AccessCodeChecker.sendHeartbeat(projectId, code, "test_device_id")
    }
}
