package com.monofocus.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusDecisionsTest {
    @Test
    fun activatesForSelectedForegroundPackage() {
        assertTrue(
            shouldActivateGrayscale(
                foregroundPackage = "com.example.social",
                selectedPackages = setOf("com.example.social"),
            ),
        )
    }

    @Test
    fun doesNotActivateForUnselectedForegroundPackage() {
        assertFalse(
            shouldActivateGrayscale(
                foregroundPackage = "com.example.reader",
                selectedPackages = setOf("com.example.social"),
            ),
        )
    }

    @Test
    fun doesNotActivateForUnknownForegroundPackage() {
        assertFalse(
            shouldActivateGrayscale(
                foregroundPackage = null,
                selectedPackages = setOf("com.example.social"),
            ),
        )
    }

    @Test
    fun doesNotActivateForEmptySelection() {
        assertFalse(
            shouldActivateGrayscale(
                foregroundPackage = "com.example.social",
                selectedPackages = emptySet(),
            ),
        )
    }
}
