package com.danneu.json

import com.danneu.result.Result
import com.danneu.result.flatMap
import com.eclipsesource.json.Json
import com.eclipsesource.json.JsonValue
import java.io.Reader

class Decoder <out T> (val decode: (JsonValue) -> Result<T, String>) {
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

        fun parse(reader: Reader): Result<JsonValue, String> {
            return Result.ok(Json.parse(reader))
        }

        fun parse(string: String): Result<JsonValue, String> {
            return Result.ok(Json.parse(string))
        }

        // DECODERS

        fun <A> lazy(getDecoder: () -> Decoder<A>): Decoder<A> = Decoder { getDecoder().decode(it) }

        fun <A> get(key: String, decoder: Decoder<A>): Decoder<A> = Decoder {
            when {
                it.isObject ->
                    it.asObject().get(key)?.let(decoder.decode) ?:
                        Result.err("Expected field \"$key\" but it was missing")
                else ->
                    Result.err("Expected object but got ${it.javaClass.simpleName}")
            }
        }

        val string: Decoder<String> = Decoder {
            when {
                it.isString ->
                    Result.ok(it.asString())
                else ->
                    Result.err("Expected String but got ${it.javaClass.simpleName}")
            }
        }

        val int: Decoder<Int> = Decoder {
            when {
                it.isNumber ->
                    Result.ok(it.asInt())
                else ->
                    Result.err("Expected Int but got ${it.javaClass.simpleName}")
            }
        }

        val float: Decoder<Float> = Decoder {
            when {
                it.isNumber ->
                    Result.ok(it.asFloat())
                else ->
                    Result.err("Expected Float but got ${it.javaClass.simpleName}")
            }
        }

        val double: Decoder<Double> = Decoder {
            when {
                it.isNumber ->
                    Result.ok(it.asDouble())
                else ->
                    Result.err("Expected Double but got ${it.javaClass.simpleName}")
            }
        }

        val long: Decoder<Long> = Decoder {
            when {
                it.isNumber ->
                    Result.ok(it.asLong())
                else ->
                    Result.err("Expected Long but got ${it.javaClass.simpleName}")
            }
        }


        val bool: Decoder<Boolean> = Decoder {
            when {
                it.isBoolean ->
                    Result.ok(it.asBoolean())
                else ->
                    Result.err("Expected Bool but got ${it.javaClass.simpleName}")
            }
        }

        fun <T> whenNull(default: T): Decoder<T> = Decoder {
            when {
                it.isNull ->
                    Result.ok(default)
                else ->
                    Result.err("Expected null but got ${it.javaClass.simpleName}")
            }
        }

        fun <T> nullable(decoder: Decoder<T>): Decoder<T?> = Decoder {
            when {
                it.isNull ->
                    Result.ok(null)
                else ->
                    decoder(it)
            }
        }

        fun <A> listOf(decoder: Decoder<A>): Decoder<List<A>> = Decoder { jsonValue ->
            when {
                jsonValue.isArray ->
                    // Short-circuit on first decode fail
                    jsonValue.asArray().map { value ->
                        decoder(value).let { result ->
                            when (result) {
                                is Result.Err ->
                                    return@Decoder Result.err(result.error)
                                is Result.Ok ->
                                    result.value
                            }
                        }
                    }.let(Result.Companion::ok)
                else ->
                    Result.err("Expected JSON array but got ${jsonValue.javaClass.simpleName}")
            }
        }

        inline fun <reified A> arrayOf(decoder: Decoder<A>): Decoder<Array<A>> = listOf(decoder).map { it.toTypedArray() }

        fun <A> keyValuePairs(decoder: Decoder<A>): Decoder<List<Pair<String, A>>> = Decoder { jsonValue ->
            when {
                jsonValue.isObject ->
                    jsonValue.asObject().map { member ->
                        decoder(member.value).map { member.name to it }.let { result ->
                            when (result) {
                                is Result.Err ->
                                    return@Decoder Result.err(result.error)
                                is Result.Ok ->
                                    result.value
                            }
                        }
                    }.let(Result.Companion::ok)
                else ->
                    Result.err("Expected JSON object but got ${jsonValue.javaClass.simpleName}")
            }
        }

        fun <A> mapOf(decoder: Decoder<A>): Decoder<Map<String, A>> = keyValuePairs(decoder).map { it.toMap() }

        fun <A, B> pairOf(left: Decoder<A>, right: Decoder<B>): Decoder<Pair<A, B>> = Decoder { jsonValue ->
            when {
                jsonValue.isArray -> {
                    jsonValue.asArray().let { array ->
                        if (array.size() == 2) {
                            left(array[0]).flatMap { v1 ->
                                right(array[1]).map { v2 ->
                                    v1 to v2
                                }
                            }
                        } else {
                            Result.err("Expected Pair but got array with ${array.size()} items")
                        }

                    }
                }
                else ->
                    Result.err("Expected Pair but got ${jsonValue.javaClass.simpleName}")
            }
        }

        fun <A, B, C> tripleOf(d1: Decoder<A>, d2: Decoder<B>, d3: Decoder<C>): Decoder<Triple<A, B, C>> = Decoder { jsonValue ->
            when {
                jsonValue.isArray ->
                    jsonValue.asArray().let { array ->
                        if (array.size() == 3) {
                            d1(array[0]).flatMap { v1 ->
                                d2(array[1]).flatMap { v2 ->
                                    d3(array[2]).map { v3 ->
                                        Triple(v1, v2, v3)
                                    }
                                }
                            }
                        } else {
                            Result.err("Expected Triple but got array with ${array.size()} items")
                        }
                    }
                else ->
                    Result.err("Expected Pair but got ${jsonValue.javaClass.simpleName}")
            }
        }


        fun <A> getIn(keys: List<String>, decoder: Decoder<A>): Decoder<A> {
            return keys.foldRight(decoder, { k, a -> get(k ,a) })
        }


        fun <A> index(i: Int, decoder: Decoder<A>): Decoder<A> {
            return Decoder { jsonValue ->
                when {
                    jsonValue.isArray ->
                        jsonValue.asArray().let { array ->
                            if (i >= 0 && i < array.size()) {
                                decoder(array.get(i))
                            } else {
                                Result.err("Expected index $i to be in bounds of array")
                            }
                        }
                    else ->
                        Result.err("Expected Array but got ${jsonValue.javaClass.simpleName}")
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

        // MAPPING

        // TODO: Generalize `mapN` function and allow chaining.

        fun <V1, T> map(f: (V1) -> T, d1: Decoder<V1>): Decoder<T> = Decoder { value ->
            d1(value).map { v1 ->
                f(v1)
            }
        }

        fun <V1, V2, T> map2(f: (V1, V2) -> T, d1: Decoder<V1>, d2: Decoder<V2>): Decoder<T> {
            return Decoder { value ->
                d1(value).flatMap { v1 ->
                    d2(value).map {v2 ->
                        f(v1, v2)
                    }
                }
            }
        }

        fun <V1, V2, V3, T> map3(f: (V1, V2, V3) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>): Decoder<T> {
            return Decoder { value ->
                d1(value).flatMap { v1 ->
                    d2(value).flatMap { v2 ->
                        d3(value).map { v3 ->
                            f(v1, v2, v3)
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, T> map4(f: (V1, V2, V3, V4) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>): Decoder<T> {
            return Decoder { value ->
                d1(value).flatMap { v1 ->
                    d2(value).flatMap { v2 ->
                        d3(value).flatMap { v3 ->
                            d4(value).map { v4 ->
                                f(v1, v2, v3, v4)
                            }
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, V5, T> map5(f: (V1, V2, V3, V4, V5) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>, d5: Decoder<V5>): Decoder<T> {
            return Decoder { value ->
                d1(value).flatMap { v1 ->
                    d2(value).flatMap { v2 ->
                        d3(value).flatMap { v3 ->
                            d4(value).flatMap { v4 ->
                                d5(value).map { v5 ->
                                    f(v1, v2, v3, v4, v5)
                                }
                            }
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, V5, V6, T> map6(f: (V1, V2, V3, V4, V5, V6) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>, d5: Decoder<V5>, d6: Decoder<V6>): Decoder<T> {
            return Decoder { value ->
                d1(value).flatMap { v1 ->
                    d2(value).flatMap { v2 ->
                        d3(value).flatMap { v3 ->
                            d4(value).flatMap { v4 ->
                                d5(value).flatMap { v5 ->
                                    d6(value).map { v6 ->
                                        f(v1, v2, v3, v4, v5, v6)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun <V1, V2, V3, V4, V5, V6, V7, T> map7(f: (V1, V2, V3, V4, V5, V6, V7) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>, d5: Decoder<V5>, d6: Decoder<V6>, d7: Decoder<V7>): Decoder<T> {
            return Decoder { value ->
                d1(value).flatMap { v1 ->
                    d2(value).flatMap { v2 ->
                        d3(value).flatMap { v3 ->
                            d4(value).flatMap { v4 ->
                                d5(value).flatMap { v5 ->
                                    d6(value).flatMap { v6 ->
                                        d7(value).map { v7 ->
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

        fun <V1, V2, V3, V4, V5, V6, V7, V8, T> map8(f: (V1, V2, V3, V4, V5, V6, V7, V8) -> T, d1: Decoder<V1>, d2: Decoder<V2>, d3: Decoder<V3>, d4: Decoder<V4>, d5: Decoder<V5>, d6: Decoder<V6>, d7: Decoder<V7>, d8: Decoder<V8>): Decoder<T> {
            return Decoder { value ->
                d1(value).flatMap { v1 ->
                    d2(value).flatMap { v2 ->
                        d3(value).flatMap { v3 ->
                            d4(value).flatMap { v4 ->
                                d5(value).flatMap { v5 ->
                                    d6(value).flatMap { v6 ->
                                        d7(value).flatMap { v7 ->
                                            d8(value).map { v8 ->
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

        fun <T> succeed(value: T): Decoder<T> = Decoder {
            Result.ok(value)
        }

        fun <T> fail(message: String): Decoder<T> = Decoder {
            Result.err(message)
        }
    }
}
