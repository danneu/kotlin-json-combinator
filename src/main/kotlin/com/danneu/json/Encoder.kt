package com.danneu.json

import com.eclipsesource.json.Json as MJson
import com.eclipsesource.json.JsonArray as MJsonArray

/**
 * Produce JsonValues from Kotlin values
 */
object Encoder {
    // STRING

    fun str(v: kotlin.String) = JsonValue(MJson.value(v))

    // NUMBER

    fun num(v: kotlin.Short) = JsonValue(MJson.value(v.toLong()))

    fun num(v: kotlin.Int) = JsonValue(MJson.value(v.toLong()))

    fun num(v: kotlin.Long) = JsonValue(MJson.value(v))

    fun num(v: kotlin.Float) = JsonValue(MJson.value(v.toDouble()))

    fun num(v: kotlin.Double) = JsonValue(MJson.value(v))

    // BOOLEAN

    fun bool(v: kotlin.Boolean) = JsonValue(MJson.value(v))

    // NULL

    val `null`: JsonValue = JsonValue(MJson.NULL)

    // OBJECT

    fun obj(pairs: Iterable<Pair<String, JsonValue>>) = MJson.`object`().apply {
        for ((k, v) in pairs) this.add(k, v.underlying)
    }.let(::JsonValue)

    fun obj(pairs: Sequence<Pair<String, JsonValue>>) = obj(pairs.asIterable())

    fun obj(vararg pairs: Pair<String, JsonValue>) = obj(pairs.asIterable())

    fun obj(map: Map<String, JsonValue>) = obj(map.entries.map { it.toPair() })

    // ARRAY

    fun array(values: Iterable<JsonValue>) = (MJson.array() as MJsonArray).apply {
        for (v in values) this.add(v.underlying)
    }.let (::JsonValue)

    fun array(vararg values: JsonValue) = array(values.asList())

    fun array(values: Sequence<JsonValue>) = array(values.asIterable())
}

