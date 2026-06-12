package com.monofocus.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionAvailabilityTest {
    @Test
    fun detectsAvailableSelectedPackages() {
        assertTrue(
            hasSelectedAvailablePackage(
                selectedPackages = setOf("com.example.social"),
                availablePackageNames = setOf("com.example.reader", "com.example.social"),
            ),
        )
    }

    @Test
    fun disablesEngineWhenRemovedPackageWasOnlySelectedAvailableApp() {
        assertTrue(
            shouldDisableEngineAfterPackageRemoved(
                engineEnabled = true,
                removedPackageName = "com.example.social",
                isReplacing = false,
                selectedPackages = setOf("com.example.social"),
                availablePackageNames = emptySet(),
            ),
        )
    }

    @Test
    fun treatsSelectedPackageRemovalAsCandidateOnlyWhenEngineIsEnabled() {
        assertTrue(
            isSelectedPackageRemovalCandidate(
                engineEnabled = true,
                removedPackageName = " com.example.social ",
                isReplacing = false,
                selectedPackages = setOf("com.example.social"),
            ),
        )
    }

    @Test
    fun rejectsPackageRemovalCandidateWhenPackageIsNotSelected() {
        assertFalse(
            isSelectedPackageRemovalCandidate(
                engineEnabled = true,
                removedPackageName = "com.example.reader",
                isReplacing = false,
                selectedPackages = setOf("com.example.social"),
            ),
        )
    }

    @Test
    fun keepsEngineEnabledWhenAnotherSelectedAppIsStillAvailable() {
        assertFalse(
            shouldDisableEngineAfterPackageRemoved(
                engineEnabled = true,
                removedPackageName = "com.example.social",
                isReplacing = false,
                selectedPackages = setOf("com.example.social", "com.example.reader"),
                availablePackageNames = setOf("com.example.reader"),
            ),
        )
    }

    @Test
    fun ignoresUnselectedPackageRemoval() {
        assertFalse(
            shouldDisableEngineAfterPackageRemoved(
                engineEnabled = true,
                removedPackageName = "com.example.reader",
                isReplacing = false,
                selectedPackages = setOf("com.example.social"),
                availablePackageNames = setOf("com.example.social"),
            ),
        )
    }

    @Test
    fun ignoresPackageReplacementDuringAppUpdate() {
        assertFalse(
            shouldDisableEngineAfterPackageRemoved(
                engineEnabled = true,
                removedPackageName = "com.example.social",
                isReplacing = true,
                selectedPackages = setOf("com.example.social"),
                availablePackageNames = emptySet(),
            ),
        )
    }

    @Test
    fun ignoresRemovalWhenEngineIsDisabled() {
        assertFalse(
            shouldDisableEngineAfterPackageRemoved(
                engineEnabled = false,
                removedPackageName = "com.example.social",
                isReplacing = false,
                selectedPackages = setOf("com.example.social"),
                availablePackageNames = emptySet(),
            ),
        )
    }
}
