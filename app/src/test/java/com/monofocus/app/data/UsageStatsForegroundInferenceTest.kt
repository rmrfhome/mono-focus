package com.monofocus.app.data

import android.app.usage.UsageEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageStatsForegroundInferenceTest {
    @Test
    fun resumedAppBecomesForeground() {
        val foreground = inferForegroundPackageFromEvents(
            previousPackage = null,
            ownPackageName = "com.monofocus.app",
            events = listOf(
                UsageEventSnapshot(
                    packageName = "com.example.social",
                    eventType = UsageEvents.Event.ACTIVITY_RESUMED,
                ),
            ),
        )

        assertEquals("com.example.social", foreground)
    }

    @Test
    fun keepsCurrentForegroundPackageAcrossLonePauseEvent() {
        val foreground = inferForegroundPackageFromEvents(
            previousPackage = "com.example.social",
            ownPackageName = "com.monofocus.app",
            events = listOf(
                UsageEventSnapshot(
                    packageName = "com.example.social",
                    eventType = UsageEvents.Event.ACTIVITY_PAUSED,
                ),
            ),
        )

        assertEquals("com.example.social", foreground)
    }

    @Test
    fun keepsPackageForegroundDuringInPackageActivityTransition() {
        val foreground = inferForegroundPackageFromEvents(
            previousPackage = null,
            ownPackageName = "com.monofocus.app",
            events = listOf(
                UsageEventSnapshot(
                    packageName = "com.google.android.calendar",
                    className = "com.android.calendar.AllInOneActivity",
                    eventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    eventTimeMillis = 1_000L,
                ),
                UsageEventSnapshot(
                    packageName = "com.google.android.calendar",
                    className = "com.google.android.calendar.launch.oobe.WhatsNewFullScreen",
                    eventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    eventTimeMillis = 2_000L,
                ),
                UsageEventSnapshot(
                    packageName = "com.google.android.calendar",
                    className = "com.android.calendar.AllInOneActivity",
                    eventType = UsageEvents.Event.ACTIVITY_PAUSED,
                    eventTimeMillis = 3_000L,
                ),
            ),
        )

        assertEquals("com.google.android.calendar", foreground)
    }

    @Test
    fun keepsForegroundPackageAcrossLoneActivityStop() {
        val foreground = inferForegroundPackageFromEvents(
            previousPackage = null,
            ownPackageName = "com.monofocus.app",
            events = listOf(
                UsageEventSnapshot(
                    packageName = "com.example.social",
                    className = "com.example.social.MainActivity",
                    eventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    eventTimeMillis = 1_000L,
                ),
                UsageEventSnapshot(
                    packageName = "com.example.social",
                    className = "com.example.social.MainActivity",
                    eventType = UsageEvents.Event.ACTIVITY_PAUSED,
                    eventTimeMillis = 2_000L,
                ),
            ),
        )

        assertEquals("com.example.social", foreground)
    }

    @Test
    fun replacesPausedPackageWhenAnotherPackageResumes() {
        val foreground = inferForegroundPackageFromEvents(
            previousPackage = "com.example.social",
            ownPackageName = "com.monofocus.app",
            events = listOf(
                UsageEventSnapshot(
                    packageName = "com.example.social",
                    className = "com.example.social.MainActivity",
                    eventType = UsageEvents.Event.ACTIVITY_PAUSED,
                    eventTimeMillis = 1_000L,
                ),
                UsageEventSnapshot(
                    packageName = "com.example.launcher",
                    className = "com.example.launcher.LauncherActivity",
                    eventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    eventTimeMillis = 2_000L,
                ),
            ),
        )

        assertEquals("com.example.launcher", foreground)
    }

    @Test
    fun carriesResumedActivityAcrossPollingWindows() {
        val state = ForegroundUsageEventState()

        val firstWindow = state.updateFromEvents(
            ownPackageName = "com.monofocus.app",
            events = listOf(
                UsageEventSnapshot(
                    packageName = "com.google.android.calendar",
                    className = "com.android.calendar.event.LaunchInfoActivity",
                    eventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    eventTimeMillis = 1_000L,
                ),
                UsageEventSnapshot(
                    packageName = "com.google.android.calendar",
                    className = "com.android.calendar.event.LaunchInfoActivity",
                    eventType = UsageEvents.Event.ACTIVITY_PAUSED,
                    eventTimeMillis = 2_000L,
                ),
                UsageEventSnapshot(
                    packageName = "com.google.android.calendar",
                    className = "com.google.android.calendar.launch.oobe.WhatsNewFullScreen",
                    eventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    eventTimeMillis = 3_000L,
                ),
            ),
        )

        val overlappingWindow = state.updateFromEvents(
            ownPackageName = "com.monofocus.app",
            events = listOf(
                UsageEventSnapshot(
                    packageName = "com.google.android.calendar",
                    className = "com.android.calendar.event.LaunchInfoActivity",
                    eventType = UsageEvents.Event.ACTIVITY_STOPPED,
                    eventTimeMillis = 4_000L,
                ),
            ),
        )

        val finalWindow = state.updateFromEvents(
            ownPackageName = "com.monofocus.app",
            events = listOf(
                UsageEventSnapshot(
                    packageName = "com.google.android.calendar",
                    className = "com.google.android.calendar.launch.oobe.WhatsNewFullScreen",
                    eventType = UsageEvents.Event.ACTIVITY_PAUSED,
                    eventTimeMillis = 5_000L,
                ),
            ),
        )

        assertEquals("com.google.android.calendar", firstWindow)
        assertEquals("com.google.android.calendar", overlappingWindow)
        assertEquals("com.google.android.calendar", finalWindow)
    }

    @Test
    fun ignoresOwnPackageAsForeground() {
        val foreground = inferForegroundPackageFromEvents(
            previousPackage = "com.example.social",
            ownPackageName = "com.monofocus.app",
            events = listOf(
                UsageEventSnapshot(
                    packageName = "com.monofocus.app",
                    eventType = UsageEvents.Event.ACTIVITY_RESUMED,
                ),
            ),
        )

        assertNull(foreground)
    }

    @Test
    fun keepsPreviousForegroundWhenNoEventsArrive() {
        val foreground = inferForegroundPackageFromEvents(
            previousPackage = "com.example.social",
            ownPackageName = "com.monofocus.app",
            events = emptyList(),
        )

        assertEquals("com.example.social", foreground)
    }

    @Test
    fun appliesEventsByTimestampWhenInputIsOutOfOrder() {
        val foreground = inferForegroundPackageFromEvents(
            previousPackage = null,
            ownPackageName = "com.monofocus.app",
            events = listOf(
                UsageEventSnapshot(
                    packageName = "com.example.social",
                    eventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    eventTimeMillis = 2_000L,
                ),
                UsageEventSnapshot(
                    packageName = "com.example.social",
                    eventType = UsageEvents.Event.ACTIVITY_PAUSED,
                    eventTimeMillis = 1_000L,
                ),
            ),
        )

        assertEquals("com.example.social", foreground)
    }

    @Test
    fun laterResumeWinsOverEarlierForegroundPackage() {
        val foreground = inferForegroundPackageFromEvents(
            previousPackage = null,
            ownPackageName = "com.monofocus.app",
            events = listOf(
                UsageEventSnapshot(
                    packageName = "com.example.reader",
                    eventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    eventTimeMillis = 1_000L,
                ),
                UsageEventSnapshot(
                    packageName = "com.example.social",
                    eventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    eventTimeMillis = 2_000L,
                ),
            ),
        )

        assertEquals("com.example.social", foreground)
    }
}
