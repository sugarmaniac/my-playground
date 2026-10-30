package com.sosyaloyun.shared

import kotlinx.serialization.json.Json

/** Mobil uygulama ile server'ın API gövdelerini aynı kurallarla okuyup yazdığı JSON yapılandırması. */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
