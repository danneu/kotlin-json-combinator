package com.danneu.json

import com.eclipsesource.json.WriterConfig.PRETTY_PRINT
import com.eclipsesource.json.JsonValue as MJsonValue

/**
 * Opaque representation of a json value.
 *
 * - Decoders consume it.
 * - Encoders produces it.
 */
class JsonValue internal constructor(internal val underlying: MJsonValue) {
    override fun toString() = underlying.toString()

    fun toPrettyString() = underlying.toString(PRETTY_PRINT)
}
