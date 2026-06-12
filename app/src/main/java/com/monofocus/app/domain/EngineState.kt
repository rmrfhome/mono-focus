package com.monofocus.app.domain

enum class EngineStopReason {
    EngineDisabled,
    PermissionsMissing,
    NoSelectedApps,
    RuleUnavailable,
    InternalError,
}
