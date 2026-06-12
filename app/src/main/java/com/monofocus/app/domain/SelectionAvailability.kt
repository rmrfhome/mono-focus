package com.monofocus.app.domain

internal fun hasSelectedAvailablePackage(
    selectedPackages: Set<String>,
    availablePackageNames: Set<String>,
): Boolean =
    selectedPackages.any { selectedPackage -> selectedPackage in availablePackageNames }

internal fun isSelectedPackageRemovalCandidate(
    engineEnabled: Boolean,
    removedPackageName: String?,
    isReplacing: Boolean,
    selectedPackages: Set<String>,
): Boolean {
    val removed = removedPackageName?.trim().orEmpty()
    return engineEnabled &&
        !isReplacing &&
        removed.isNotBlank() &&
        removed in selectedPackages
}

internal fun shouldDisableEngineAfterPackageRemoved(
    engineEnabled: Boolean,
    removedPackageName: String?,
    isReplacing: Boolean,
    selectedPackages: Set<String>,
    availablePackageNames: Set<String>,
): Boolean =
    isSelectedPackageRemovalCandidate(
        engineEnabled = engineEnabled,
        removedPackageName = removedPackageName,
        isReplacing = isReplacing,
        selectedPackages = selectedPackages,
    ) &&
        !hasSelectedAvailablePackage(
            selectedPackages = selectedPackages,
            availablePackageNames = availablePackageNames,
        )
