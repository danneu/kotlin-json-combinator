package com.danneu.json.combinator

import com.eclipsesource.json.Json as MJson
import com.eclipsesource.json.JsonArray as MJsonArray

/**
 * Produce JsonValues from Kotlin values
 */
object Encoder {
    // STRING

    fun str(v: kotlin.String): JsonValue = JsonValue.String(v)

    // NUMBER

    fun num(v: kotlin.Short): JsonValue = JsonValue.Long(v.toLong())

    fun num(v: kotlin.Int): JsonValue = JsonValue.Long(v.toLong())

    fun num(v: kotlin.Long): JsonValue = JsonValue.Long(v)

    fun num(v: kotlin.Float): JsonValue = JsonValue.Double(v.toDouble())

    fun num(v: kotlin.Double): JsonValue = JsonValue.Double(v)

    // BOOLEAN

    fun bool(v: kotlin.Boolean): JsonValue = JsonValue.Boolean(v)

    // NULL

    val `null`: JsonValue = JsonValue.Null

    // OBJECT

    fun obj(pairs: Iterable<Pair<String, JsonValue>>): JsonValue = JsonValue.Object(pairs)

    fun obj(pairs: Sequence<Pair<String, JsonValue>>) = obj(pairs.asIterable())

    fun obj(vararg pairs: Pair<String, JsonValue>) = obj(pairs.asIterable())

    fun obj(map: Map<String, JsonValue>) = obj(map.entries.map { it.toPair() })

    // ARRAY

    fun array(values: Iterable<JsonValue>): JsonValue = JsonValue.Array(values)

    fun array(vararg values: JsonValue) = array(values.asList())

    fun array(values: Sequence<JsonValue>) = array(values.asIterable())
}

