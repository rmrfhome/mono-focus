package com.monofocus.app.domain

fun shouldActivateGrayscale(
    foregroundPackage: String?,
    selectedPackages: Set<String>,
): Boolean = foregroundPackage != null && foregroundPackage in selectedPackages
