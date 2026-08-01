package dev.haasele.koma.shared.core

import kotlinx.serialization.json.Json

/** Lenient on purpose: configs written by an older app version must keep loading after upgrades. */
val KomaJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}
