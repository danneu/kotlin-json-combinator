package com.danneu.json

import com.eclipsesource.json.WriterConfig.PRETTY_PRINT
import com.eclipsesource.json.Json as MJson
import com.eclipsesource.json.JsonObject as MJsonObject
import com.eclipsesource.json.JsonArray as MJsonArray
import com.eclipsesource.json.JsonValue as MJsonValue

/**
 * Represents a json-serializable value.
 *
 * Opaque type. Must use Encoder to create JsonValues.
 */
sealed class JsonValue {
    // COMMON

    abstract internal val internal: MJsonValue

    override fun toString() = internal.toString()

    fun toPrettyString(): kotlin.String = internal.toString(PRETTY_PRINT)

    // MEMBERS

    internal class Object(pairs: Iterable<Pair<kotlin.String, JsonValue>>): JsonValue() {
        override val internal = MJson.`object`().apply {
            pairs.forEach { (k, v) -> this.add(k, v.internal) }
        }
    }

    internal class Array(values: Iterable<JsonValue>): JsonValue() {
        override val internal = (MJson.array() as MJsonArray).apply {
            values.forEach { v -> this.add(v.internal) }
        }
    }

    internal class String(v: kotlin.String): JsonValue() {
        override val internal: MJsonValue = MJson.value(v)
    }

    internal class Long(v: kotlin.Long): JsonValue() {
        override val internal: MJsonValue = MJson.value(v)
    }

    internal class Double(v: kotlin.Double): JsonValue() {
        override val internal: MJsonValue = MJson.value(v)
    }

    internal class Boolean(v: kotlin.Boolean): JsonValue() {
        override val internal: MJsonValue = if (v) MJson.TRUE else MJson.FALSE
    }

    internal object Null: JsonValue() {
        override val internal: MJsonValue = MJson.NULL
    }
}

