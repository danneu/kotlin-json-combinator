package com.danneu.json

import com.danneu.result.Result
import com.danneu.result.flatMap
import com.danneu.result.getOrElse
import com.eclipsesource.json.Json
import java.io.Reader

class ParseException(message: String) : Exception(message)
class DecodeException(message: String) : Exception(message)

class Decoder <out T> (private val decode: (JsonValue) -> Result<T, String>) {
    operator fun invoke(value: JsonValue) = decode(value)

    fun <T2> andThen(f: (T) -> Decoder<T2>): Decoder<T2> = Decoder { jsonValue ->
        decode(jsonValue).flatMap { success ->
            f(success).let { nextDecoder ->
                nextDecoder(jsonValue)
            }
        }
    }

    fun <T2> map(f: (T) -> T2): Decoder<T2> = Decoder {
        decode(it).map(f)
    }

    fun mapError(f: (String) -> String): Decoder<T> = Decoder {
        decode(it).mapError(f)
    }

    companion object {
        // PARSING

        fun parse(reader: Reader): Result<JsonValue, String> = try {
            Result.ok(JsonValue(Json.parse(reader)))
        } catch (ex: com.eclipsesource.json.ParseException) {
            Result.err(ex.message!!)
        }

        fun parse(string: String) = try {
            Result.ok(JsonValue(Json.parse(string)))
        } catch (ex: com.eclipsesource.json.ParseException) {
            Result.err(ex.message!!)
        }

        fun parseOrThrow(reader: Reader) = parse(reader).getOrElse { message -> throw ParseException(message) }

        fun parseOrThrow(string: String) = parse(string).getOrElse { message -> throw ParseException(message) }

        // PARSING + DECODING

        fun <T> decode(reader: Reader, decoder: Decoder<T>): Result<T, String> {
            return parse(reader).flatMap { decoder(it) }
        }

        fun <T> decode(string: String, decoder: Decoder<T>): Result<T, String> {
            return parse(string).flatMap { decoder(it) }
        }

        fun <T> decodeOrThrow(reader: Reader, decoder: Decoder<T>): T {
            return parse(reader).flatMap { decoder(it) }.getOrElse { throw DecodeException(it) }
        }

        fun <T> decodeOrThrow(string: String, decoder: Decoder<T>): T {
            return parse(string).flatMap { decoder(it) }.getOrElse { throw DecodeException(it) }
        }

        // DECODERS

        fun <A> lazy(getDecoder: () -> Decoder<A>): Decoder<A> = Decoder { getDecoder().decode(it) }

        fun <A> get(key: String, decoder: Decoder<A>): Decoder<A> = Decoder {
            when {
                it.underlying.isObject ->
                    it.underlying.asObject().get(key)?.let { decoder(JsonValue(it))} ?:
                        Result.err("Expected field \"$key\" but it was missing")
                else ->
                    Result.err("Expected object but got ${it.underlying.javaClass.simpleName}")
            }
        }

        fun <A> get(keys: List<String>, decoder: Decoder<A>): Decoder<A> {
            return keys.foldRight(decoder, { key, acc -> get(key, acc) })
        }

        fun <A> getOrMissing(key: String, fallback: A, decoder: Decoder<A>): Decoder<A> = Decoder { jsonValue ->
            when {
                jsonValue.underlying.isObject ->
                    jsonValue.underlying.asObject().get(key)?.let { decoder(JsonValue(it))} ?:
                        Result.ok(fallback)
                else ->
                    Result.err("Expected object but got ${jsonValue.underlying.javaClass.simpleName}")
            }
        }

        fun <A> getOrMissing(keys: List<String>, fallback: A, decoder: Decoder<A>): Decoder<A> {
            return keys.foldRight(decoder, { key, acc -> getOrMissing(key, fallback, acc) })
        }

        val string: Decoder<String> = Decoder {
            when {
                it.underlying.isString ->
                    Result.ok(it.underlying.asString())
                else ->
                    Result.err("Expected String but got ${it.underlying.javaClass.simpleName}")
            }
        }

        val int: Decoder<Int> = Decoder {
            when {
                it.underlying.isNumber ->
                    Result.ok(it.underlying.asInt())
                else ->
                    Result.err("Expected Int but got ${it.underlying.javaClass.simpleName}")
            }
        }

        val float: Decoder<Float> = Decoder {
            when {
                it.underlying.isNumber ->
                    Result.ok(it.underlying.asFloat())
                else ->
                    Result.err("Expected Float but got ${it.underlying.javaClass.simpleName}")
            }
        }

        val double: Decoder<Double> = Decoder {
            when {
                it.underlying.isNumber ->
                    Result.ok(it.underlying.asDouble())
                else ->
                    Result.err("Expected Double but got ${it.underlying.javaClass.simpleName}")
            }
        }

        val long: Decoder<Long> = Decoder {
            when {
                it.underlying.isNumber ->
                    Result.ok(it.underlying.asLong())
                else ->
                    Result.err("Expected Long but got ${it.underlying.javaClass.simpleName}")
            }
        }


        val bool: Decoder<Boolean> = Decoder {
            when {
                it.underlying.isBoolean ->
                    Result.ok(it.underlying.asBoolean())
                else ->
                    Result.err("Expected Bool but got ${it.underlying.javaClass.simpleName}")
            }
        }

        fun <T> whenNull(default: T): Decoder<T> = Decoder {
            when {
                it.underlying.isNull ->
                    Result.ok(default)
                else ->
                    Result.err("Expected null but got ${it.underlying.javaClass.simpleName}")
            }
        }

        fun <T> nullable(decoder: Decoder<T>): Decoder<T?> = Decoder {
            when {
                it.underlying.isNull ->
                    Result.ok(null)
                else ->
                    decoder(it)
            }
        }

        fun <A> listOf(decoder: Decoder<A>): Decoder<List<A>> = Decoder { jsonValue ->
            when {
                jsonValue.underlying.isArray ->
                    // Short-circuit on first decode fail
                    jsonValue.underlying.asArray().map { value ->
                        decoder(JsonValue(value)).let { result ->
                            when (result) {
                                is Result.Err ->
                                    return@Decoder Result.err(result.error)
                                is Result.Ok ->
                                    result.value
                            }
                        }
                    }.let(Result.Companion::ok)
                else ->
                    Result.err("Expected JSON array but got ${jsonValue.underlying.javaClass.simpleName}")
            }
        }

        inline fun <reified A> arrayOf(decoder: Decoder<A>): Decoder<Array<A>> = listOf(decoder).map { it.toTypedArray() }

        fun <K, V> keyValuePairs(keyDecoder: Decoder<K>, valueDecoder: Decoder<V>): Decoder<List<Pair<K, V>>> = Decoder { jsonValue ->
            when {
                jsonValue.underlying.isObject ->
                    jsonValue.underlying.asObject().map { member ->
                        keyDecoder(JsonValue(Json.value(member.name))).flatMap { k ->
                            valueDecoder(JsonValue(member.value)).map { v ->
                                k to v
                            }
                        }.let { result ->
                            when (result) {
                                is Result.Err ->
                                    return@Decoder Result.err(result.error)
                                is Result.Ok ->
                                    result.value
                            }
                        }
                    }.let(Result.Companion::ok)
                else ->
                    Result.err("Expected JSON object but got ${jsonValue.underlying.javaClass.simpleName}")
            }
        }


        fun <V> keyValuePairs(valueDecoder: Decoder<V>) = Decoder.keyValuePairs(Decoder.string, valueDecoder)

        fun <K, V> mapOf(keyDecoder: Decoder<K>, valueDecoder: Decoder<V>): Decoder<Map<K, V>> {
            return keyValuePairs(keyDecoder, valueDecoder).map { it.toMap() }
        }

        fun <V> mapOf(valueDecoder: Decoder<V>) = Decoder.mapOf(Decoder.string, valueDecoder)

        fun <A> singletonOf(decoder: Decoder<A>) = Decoder { jsonValue ->
            when {
                jsonValue.underlying.isArray -> {
                    jsonValue.underlying.asArray().let { array ->
                        if (array.size() == 1) {
                            decoder(JsonValue(array[0]))
                        } else {
                            Result.err("Expected array of one item but got one with ${array.size()} items")
                        }
                    }
                }
                else ->
                    Result.err("Expected array but got ${jsonValue.underlying.javaClass.simpleName}")
            }
        }

        fun <A, B> pairOf(left: Decoder<A>, right: Decoder<B>): Decoder<Pair<A, B>> = Decoder { jsonValue ->
            when {
                jsonValue.underlying.isArray -> {
                    jsonValue.underlying.asArray().let { array ->
                        if (array.size() == 2) {
                            left(JsonValue(array[0])).flatMap { v1 ->
                                right(JsonValue(array[1])).map { v2 ->
                                    v1 to v2
                                }
                            }
                        } else {
                            Result.err("Expected Pair but got array with ${array.size()} items")
                        }

                    }
                }
                else ->
                    Result.err("Expected Pair but got ${jsonValue.underlying.javaClass.simpleName}")
            }
        }

        fun <A, B, C> tripleOf(d1: Decoder<A>, d2: Decoder<B>, d3: Decoder<C>): Decoder<Triple<A, B, C>> = Decoder { jsonValue ->
            when {
                jsonValue.underlying.isArray ->
                    jsonValue.underlying.asArray().let { array ->
                        if (array.size() == 3) {
                            d1(JsonValue(array[0])).flatMap { v1 ->
                                d2(JsonValue(array[1])).flatMap { v2 ->
                                    d3(JsonValue(array[2])).map { v3 ->
                                        Triple(v1, v2, v3)
                                    }
                                }
                            }
                        } else {
                            Result.err("Expected Triple but got array with ${array.size()} items")
                        }
                    }
                else ->
                    Result.err("Expected Pair but got ${jsonValue.underlying.javaClass.simpleName}")
            }
        }

        fun <A> index(i: Int, decoder: Decoder<A>): Decoder<A> {
            return Decoder { jsonValue ->
                when {
                    jsonValue.underlying.isArray ->
                        jsonValue.underlying.asArray().let { array ->
                            if (i >= 0 && i < array.size()) {
                                decoder(JsonValue(array.get(i)))
                            } else {
                                Result.err("Expected index $i to be in bounds of array")
                            }
                        }
                    else ->
                        Result.err("Expected Array but got ${jsonValue.underlying.javaClass.simpleName}")
                }
            }
        }

        fun <A> oneOf(decoders: Iterable<Decoder<A>>): Decoder<A> = Decoder { jsonValue ->
            for (decoder in decoders) {
                decoder(jsonValue).let { result ->
                    if (result is Result.Ok) {
                        return@Decoder result
                    }
                }
            }

            Result.err("None of the decoders matched")
        }

        fun <A> oneOf(vararg decoders: Decoder<A>): Decoder<A> = Companion.oneOf(decoders.asIterable())

        fun <T> succeed(value: T): Decoder<T> = Decoder {
            Result.ok(value)
        }

        fun <T> fail(message: String): Decoder<T> = Decoder {
            Result.err(message)
        }

        val value: Decoder<JsonValue> = Decoder { jsonValue ->
            Result.ok(jsonValue)
        }

        // MAPPING (generated by codegen.js)

        fun <V1, T> map(f: (V1) -> T, d1: Decoder<V1>): Decoder<T> {
            return Decoder { value: JsonValue ->
                d1(value).map { v1: V1 ->
                    f(v1)
                }
            }
        }

        fun <V1, V2, T> map(f: (V1, V2) -> T, d1: Decoder<V1>, d2: Decoder<V2>): Decoder<T> {
            return Decoder { value: JsonValue ->
                d1(value).flatMap { v1: V1 ->
                    d2(value).map { v2: V2 ->
                        f(v1, v2)
                    }
                }
            }
        }

        fun <V1, V2, V3, T> map(f: (V1, V2, V3) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>): Decoder<T> {
            return Decoder { value: JsonValue ->
                d1(value).flatMap { v1: V1 ->
                    d2(value).flatMap { v2: V2 ->
                        d3(value).map { v3: V3 ->
                            f(v1, v2, v3)
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, T> map(f: (V1, V2, V3, V4) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>): Decoder<T> {
            return Decoder { value: JsonValue ->
                d1(value).flatMap { v1: V1 ->
                    d2(value).flatMap { v2: V2 ->
                        d3(value).flatMap { v3: V3 ->
                            d4(value).map { v4: V4 ->
                                f(v1, v2, v3, v4)
                            }
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, V5, T> map(f: (V1, V2, V3, V4, V5) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>, d5: Decoder<V5>): Decoder<T> {
            return Decoder { value: JsonValue ->
                d1(value).flatMap { v1: V1 ->
                    d2(value).flatMap { v2: V2 ->
                        d3(value).flatMap { v3: V3 ->
                            d4(value).flatMap { v4: V4 ->
                                d5(value).map { v5: V5 ->
                                    f(v1, v2, v3, v4, v5)
                                }
                            }
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, V5, V6, T> map(f: (V1, V2, V3, V4, V5, V6) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>, d5: Decoder<V5>, d6: Decoder<V6>): Decoder<T> {
            return Decoder { value: JsonValue ->
                d1(value).flatMap { v1: V1 ->
                    d2(value).flatMap { v2: V2 ->
                        d3(value).flatMap { v3: V3 ->
                            d4(value).flatMap { v4: V4 ->
                                d5(value).flatMap { v5: V5 ->
                                    d6(value).map { v6: V6 ->
                                        f(v1, v2, v3, v4, v5, v6)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, V5, V6, V7, T> map(f: (V1, V2, V3, V4, V5, V6, V7) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>, d5: Decoder<V5>, d6: Decoder<V6>, d7: Decoder<V7>): Decoder<T> {
            return Decoder { value: JsonValue ->
                d1(value).flatMap { v1: V1 ->
                    d2(value).flatMap { v2: V2 ->
                        d3(value).flatMap { v3: V3 ->
                            d4(value).flatMap { v4: V4 ->
                                d5(value).flatMap { v5: V5 ->
                                    d6(value).flatMap { v6: V6 ->
                                        d7(value).map { v7: V7 ->
                                            f(v1, v2, v3, v4, v5, v6, v7)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, V5, V6, V7, V8, T> map(f: (V1, V2, V3, V4, V5, V6, V7, V8) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>, d5: Decoder<V5>, d6: Decoder<V6>, d7: Decoder<V7>, d8: Decoder<V8>): Decoder<T> {
            return Decoder { value: JsonValue ->
                d1(value).flatMap { v1: V1 ->
                    d2(value).flatMap { v2: V2 ->
                        d3(value).flatMap { v3: V3 ->
                            d4(value).flatMap { v4: V4 ->
                                d5(value).flatMap { v5: V5 ->
                                    d6(value).flatMap { v6: V6 ->
                                        d7(value).flatMap { v7: V7 ->
                                            d8(value).map { v8: V8 ->
                                                f(v1, v2, v3, v4, v5, v6, v7, v8)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, V5, V6, V7, V8, V9, T> map(f: (V1, V2, V3, V4, V5, V6, V7, V8, V9) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>, d5: Decoder<V5>, d6: Decoder<V6>, d7: Decoder<V7>, d8: Decoder<V8>, d9: Decoder<V9>): Decoder<T> {
            return Decoder { value: JsonValue ->
                d1(value).flatMap { v1: V1 ->
                    d2(value).flatMap { v2: V2 ->
                        d3(value).flatMap { v3: V3 ->
                            d4(value).flatMap { v4: V4 ->
                                d5(value).flatMap { v5: V5 ->
                                    d6(value).flatMap { v6: V6 ->
                                        d7(value).flatMap { v7: V7 ->
                                            d8(value).flatMap { v8: V8 ->
                                                d9(value).map { v9: V9 ->
                                                    f(v1, v2, v3, v4, v5, v6, v7, v8, v9)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, T> map(f: (V1, V2, V3, V4, V5, V6, V7, V8, V9, V10) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>, d5: Decoder<V5>, d6: Decoder<V6>, d7: Decoder<V7>, d8: Decoder<V8>, d9: Decoder<V9>, d10: Decoder<V10>): Decoder<T> {
            return Decoder { value: JsonValue ->
                d1(value).flatMap { v1: V1 ->
                    d2(value).flatMap { v2: V2 ->
                        d3(value).flatMap { v3: V3 ->
                            d4(value).flatMap { v4: V4 ->
                                d5(value).flatMap { v5: V5 ->
                                    d6(value).flatMap { v6: V6 ->
                                        d7(value).flatMap { v7: V7 ->
                                            d8(value).flatMap { v8: V8 ->
                                                d9(value).flatMap { v9: V9 ->
                                                    d10(value).map { v10: V10 ->
                                                        f(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, T> map(f: (V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>, d5: Decoder<V5>, d6: Decoder<V6>, d7: Decoder<V7>, d8: Decoder<V8>, d9: Decoder<V9>, d10: Decoder<V10>, d11: Decoder<V11>): Decoder<T> {
            return Decoder { value: JsonValue ->
                d1(value).flatMap { v1: V1 ->
                    d2(value).flatMap { v2: V2 ->
                        d3(value).flatMap { v3: V3 ->
                            d4(value).flatMap { v4: V4 ->
                                d5(value).flatMap { v5: V5 ->
                                    d6(value).flatMap { v6: V6 ->
                                        d7(value).flatMap { v7: V7 ->
                                            d8(value).flatMap { v8: V8 ->
                                                d9(value).flatMap { v9: V9 ->
                                                    d10(value).flatMap { v10: V10 ->
                                                        d11(value).map { v11: V11 ->
                                                            f(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, T> map(f: (V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>, d5: Decoder<V5>, d6: Decoder<V6>, d7: Decoder<V7>, d8: Decoder<V8>, d9: Decoder<V9>, d10: Decoder<V10>, d11: Decoder<V11>, d12: Decoder<V12>): Decoder<T> {
            return Decoder { value: JsonValue ->
                d1(value).flatMap { v1: V1 ->
                    d2(value).flatMap { v2: V2 ->
                        d3(value).flatMap { v3: V3 ->
                            d4(value).flatMap { v4: V4 ->
                                d5(value).flatMap { v5: V5 ->
                                    d6(value).flatMap { v6: V6 ->
                                        d7(value).flatMap { v7: V7 ->
                                            d8(value).flatMap { v8: V8 ->
                                                d9(value).flatMap { v9: V9 ->
                                                    d10(value).flatMap { v10: V10 ->
                                                        d11(value).flatMap { v11: V11 ->
                                                            d12(value).map { v12: V12 ->
                                                                f(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, T> map(f: (V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>, d5: Decoder<V5>, d6: Decoder<V6>, d7: Decoder<V7>, d8: Decoder<V8>, d9: Decoder<V9>, d10: Decoder<V10>, d11: Decoder<V11>, d12: Decoder<V12>, d13: Decoder<V13>): Decoder<T> {
            return Decoder { value: JsonValue ->
                d1(value).flatMap { v1: V1 ->
                    d2(value).flatMap { v2: V2 ->
                        d3(value).flatMap { v3: V3 ->
                            d4(value).flatMap { v4: V4 ->
                                d5(value).flatMap { v5: V5 ->
                                    d6(value).flatMap { v6: V6 ->
                                        d7(value).flatMap { v7: V7 ->
                                            d8(value).flatMap { v8: V8 ->
                                                d9(value).flatMap { v9: V9 ->
                                                    d10(value).flatMap { v10: V10 ->
                                                        d11(value).flatMap { v11: V11 ->
                                                            d12(value).flatMap { v12: V12 ->
                                                                d13(value).map { v13: V13 ->
                                                                    f(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, T> map(f: (V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>, d5: Decoder<V5>, d6: Decoder<V6>, d7: Decoder<V7>, d8: Decoder<V8>, d9: Decoder<V9>, d10: Decoder<V10>, d11: Decoder<V11>, d12: Decoder<V12>, d13: Decoder<V13>, d14: Decoder<V14>): Decoder<T> {
            return Decoder { value: JsonValue ->
                d1(value).flatMap { v1: V1 ->
                    d2(value).flatMap { v2: V2 ->
                        d3(value).flatMap { v3: V3 ->
                            d4(value).flatMap { v4: V4 ->
                                d5(value).flatMap { v5: V5 ->
                                    d6(value).flatMap { v6: V6 ->
                                        d7(value).flatMap { v7: V7 ->
                                            d8(value).flatMap { v8: V8 ->
                                                d9(value).flatMap { v9: V9 ->
                                                    d10(value).flatMap { v10: V10 ->
                                                        d11(value).flatMap { v11: V11 ->
                                                            d12(value).flatMap { v12: V12 ->
                                                                d13(value).flatMap { v13: V13 ->
                                                                    d14(value).map { v14: V14 ->
                                                                        f(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, T> map(f: (V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>, d5: Decoder<V5>, d6: Decoder<V6>, d7: Decoder<V7>, d8: Decoder<V8>, d9: Decoder<V9>, d10: Decoder<V10>, d11: Decoder<V11>, d12: Decoder<V12>, d13: Decoder<V13>, d14: Decoder<V14>, d15: Decoder<V15>): Decoder<T> {
            return Decoder { value: JsonValue ->
                d1(value).flatMap { v1: V1 ->
                    d2(value).flatMap { v2: V2 ->
                        d3(value).flatMap { v3: V3 ->
                            d4(value).flatMap { v4: V4 ->
                                d5(value).flatMap { v5: V5 ->
                                    d6(value).flatMap { v6: V6 ->
                                        d7(value).flatMap { v7: V7 ->
                                            d8(value).flatMap { v8: V8 ->
                                                d9(value).flatMap { v9: V9 ->
                                                    d10(value).flatMap { v10: V10 ->
                                                        d11(value).flatMap { v11: V11 ->
                                                            d12(value).flatMap { v12: V12 ->
                                                                d13(value).flatMap { v13: V13 ->
                                                                    d14(value).flatMap { v14: V14 ->
                                                                        d15(value).map { v15: V15 ->
                                                                            f(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, T> map(f: (V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>, d5: Decoder<V5>, d6: Decoder<V6>, d7: Decoder<V7>, d8: Decoder<V8>, d9: Decoder<V9>, d10: Decoder<V10>, d11: Decoder<V11>, d12: Decoder<V12>, d13: Decoder<V13>, d14: Decoder<V14>, d15: Decoder<V15>, d16: Decoder<V16>): Decoder<T> {
            return Decoder { value: JsonValue ->
                d1(value).flatMap { v1: V1 ->
                    d2(value).flatMap { v2: V2 ->
                        d3(value).flatMap { v3: V3 ->
                            d4(value).flatMap { v4: V4 ->
                                d5(value).flatMap { v5: V5 ->
                                    d6(value).flatMap { v6: V6 ->
                                        d7(value).flatMap { v7: V7 ->
                                            d8(value).flatMap { v8: V8 ->
                                                d9(value).flatMap { v9: V9 ->
                                                    d10(value).flatMap { v10: V10 ->
                                                        d11(value).flatMap { v11: V11 ->
                                                            d12(value).flatMap { v12: V12 ->
                                                                d13(value).flatMap { v13: V13 ->
                                                                    d14(value).flatMap { v14: V14 ->
                                                                        d15(value).flatMap { v15: V15 ->
                                                                            d16(value).map { v16: V16 ->
                                                                                f(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16)
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, T> map(f: (V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>, d5: Decoder<V5>, d6: Decoder<V6>, d7: Decoder<V7>, d8: Decoder<V8>, d9: Decoder<V9>, d10: Decoder<V10>, d11: Decoder<V11>, d12: Decoder<V12>, d13: Decoder<V13>, d14: Decoder<V14>, d15: Decoder<V15>, d16: Decoder<V16>, d17: Decoder<V17>): Decoder<T> {
            return Decoder { value: JsonValue ->
                d1(value).flatMap { v1: V1 ->
                    d2(value).flatMap { v2: V2 ->
                        d3(value).flatMap { v3: V3 ->
                            d4(value).flatMap { v4: V4 ->
                                d5(value).flatMap { v5: V5 ->
                                    d6(value).flatMap { v6: V6 ->
                                        d7(value).flatMap { v7: V7 ->
                                            d8(value).flatMap { v8: V8 ->
                                                d9(value).flatMap { v9: V9 ->
                                                    d10(value).flatMap { v10: V10 ->
                                                        d11(value).flatMap { v11: V11 ->
                                                            d12(value).flatMap { v12: V12 ->
                                                                d13(value).flatMap { v13: V13 ->
                                                                    d14(value).flatMap { v14: V14 ->
                                                                        d15(value).flatMap { v15: V15 ->
                                                                            d16(value).flatMap { v16: V16 ->
                                                                                d17(value).map { v17: V17 ->
                                                                                    f(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17)
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18, T> map(f: (V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>, d5: Decoder<V5>, d6: Decoder<V6>, d7: Decoder<V7>, d8: Decoder<V8>, d9: Decoder<V9>, d10: Decoder<V10>, d11: Decoder<V11>, d12: Decoder<V12>, d13: Decoder<V13>, d14: Decoder<V14>, d15: Decoder<V15>, d16: Decoder<V16>, d17: Decoder<V17>, d18: Decoder<V18>): Decoder<T> {
            return Decoder { value: JsonValue ->
                d1(value).flatMap { v1: V1 ->
                    d2(value).flatMap { v2: V2 ->
                        d3(value).flatMap { v3: V3 ->
                            d4(value).flatMap { v4: V4 ->
                                d5(value).flatMap { v5: V5 ->
                                    d6(value).flatMap { v6: V6 ->
                                        d7(value).flatMap { v7: V7 ->
                                            d8(value).flatMap { v8: V8 ->
                                                d9(value).flatMap { v9: V9 ->
                                                    d10(value).flatMap { v10: V10 ->
                                                        d11(value).flatMap { v11: V11 ->
                                                            d12(value).flatMap { v12: V12 ->
                                                                d13(value).flatMap { v13: V13 ->
                                                                    d14(value).flatMap { v14: V14 ->
                                                                        d15(value).flatMap { v15: V15 ->
                                                                            d16(value).flatMap { v16: V16 ->
                                                                                d17(value).flatMap { v17: V17 ->
                                                                                    d18(value).map { v18: V18 ->
                                                                                        f(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18)
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18, V19, T> map(f: (V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18, V19) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>, d5: Decoder<V5>, d6: Decoder<V6>, d7: Decoder<V7>, d8: Decoder<V8>, d9: Decoder<V9>, d10: Decoder<V10>, d11: Decoder<V11>, d12: Decoder<V12>, d13: Decoder<V13>, d14: Decoder<V14>, d15: Decoder<V15>, d16: Decoder<V16>, d17: Decoder<V17>, d18: Decoder<V18>, d19: Decoder<V19>): Decoder<T> {
            return Decoder { value: JsonValue ->
                d1(value).flatMap { v1: V1 ->
                    d2(value).flatMap { v2: V2 ->
                        d3(value).flatMap { v3: V3 ->
                            d4(value).flatMap { v4: V4 ->
                                d5(value).flatMap { v5: V5 ->
                                    d6(value).flatMap { v6: V6 ->
                                        d7(value).flatMap { v7: V7 ->
                                            d8(value).flatMap { v8: V8 ->
                                                d9(value).flatMap { v9: V9 ->
                                                    d10(value).flatMap { v10: V10 ->
                                                        d11(value).flatMap { v11: V11 ->
                                                            d12(value).flatMap { v12: V12 ->
                                                                d13(value).flatMap { v13: V13 ->
                                                                    d14(value).flatMap { v14: V14 ->
                                                                        d15(value).flatMap { v15: V15 ->
                                                                            d16(value).flatMap { v16: V16 ->
                                                                                d17(value).flatMap { v17: V17 ->
                                                                                    d18(value).flatMap { v18: V18 ->
                                                                                        d19(value).map { v19: V19 ->
                                                                                            f(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19)
                                                                                        }
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18, V19, V20, T> map(f: (V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18, V19, V20) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>, d5: Decoder<V5>, d6: Decoder<V6>, d7: Decoder<V7>, d8: Decoder<V8>, d9: Decoder<V9>, d10: Decoder<V10>, d11: Decoder<V11>, d12: Decoder<V12>, d13: Decoder<V13>, d14: Decoder<V14>, d15: Decoder<V15>, d16: Decoder<V16>, d17: Decoder<V17>, d18: Decoder<V18>, d19: Decoder<V19>, d20: Decoder<V20>): Decoder<T> {
            return Decoder { value: JsonValue ->
                d1(value).flatMap { v1: V1 ->
                    d2(value).flatMap { v2: V2 ->
                        d3(value).flatMap { v3: V3 ->
                            d4(value).flatMap { v4: V4 ->
                                d5(value).flatMap { v5: V5 ->
                                    d6(value).flatMap { v6: V6 ->
                                        d7(value).flatMap { v7: V7 ->
                                            d8(value).flatMap { v8: V8 ->
                                                d9(value).flatMap { v9: V9 ->
                                                    d10(value).flatMap { v10: V10 ->
                                                        d11(value).flatMap { v11: V11 ->
                                                            d12(value).flatMap { v12: V12 ->
                                                                d13(value).flatMap { v13: V13 ->
                                                                    d14(value).flatMap { v14: V14 ->
                                                                        d15(value).flatMap { v15: V15 ->
                                                                            d16(value).flatMap { v16: V16 ->
                                                                                d17(value).flatMap { v17: V17 ->
                                                                                    d18(value).flatMap { v18: V18 ->
                                                                                        d19(value).flatMap { v19: V19 ->
                                                                                            d20(value).map { v20: V20 ->
                                                                                                f(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20)
                                                                                            }
                                                                                        }
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

