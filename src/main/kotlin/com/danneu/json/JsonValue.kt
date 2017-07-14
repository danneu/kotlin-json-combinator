package com.danneu.json

import com.eclipsesource.json.WriterConfig
import com.eclipsesource.json.JsonValue as MJsonValue

enum class Whitespace { Minimal, Pretty }

/**
 * Opaque representation of a json value.
 *
 * - Decoders consume it.
 * - Encoders produce it.
 */
class JsonValue internal constructor(internal val underlying: MJsonValue) {
    override fun equals(other: Any?): Boolean {
        return other is JsonValue && underlying == other.underlying
    }

    override fun hashCode() = underlying.hashCode()

    override fun toString() = underlying.toString()

    fun toString(whitespace: Whitespace): String = when (whitespace) {
        Whitespace.Minimal ->
            underlying.toString(WriterConfig.MINIMAL)
        Whitespace.Pretty ->
            underlying.toString(WriterConfig.PRETTY_PRINT)
    }

    fun writeTo(writer: java.io.Writer, whitespace: Whitespace = Whitespace.Minimal) = when (whitespace) {
        Whitespace.Minimal ->
            underlying.writeTo(writer, WriterConfig.MINIMAL)
        Whitespace.Pretty ->
            underlying.writeTo(writer, WriterConfig.PRETTY_PRINT)
    }
}
