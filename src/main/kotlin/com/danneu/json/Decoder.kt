package com.danneu.json

import com.danneu.result.Result
import com.danneu.result.flatMap
import com.eclipsesource.json.Json
import com.eclipsesource.json.JsonValue
import java.io.Reader

// TODO: Rewrite collection validators so that they short-circuit on failure instead of failing
//       after running a decoder on each item.

class Decoder <out T> (val decode: (JsonValue) -> Result<T, Exception>) {
    /** Decoders can be applied like functions.
     */
    operator fun invoke(value: JsonValue): Result<T, Exception> {
        return decode(value)
    }

    /** Specify the decoder to use based on the result of the previous decoder.
     */
    fun <B> flatMap(f: (T) -> Decoder<B>): Decoder<B> = Decoder { value ->
        this.decode(value).flatMap { success: T ->
            f(success).decode(value)
        }
    }


    /** Apply a function to the decoded value on successful decode.
     */
    fun <B> map(f: (T) -> B): Decoder<B> = Decoder { value ->
        this.decode(value).map { success: T ->
            f(success)
        }
    }

    companion object {
        // PARSING

        fun parse(reader: Reader): Result<JsonValue, Exception> {
            return Result.ok(Json.parse(reader))
        }

        fun parse(string: String): Result<JsonValue, Exception> {
            return Result.ok(Json.parse(string))
        }

        // DECODERS

        fun <A> get(key: String, d1: Decoder<A>): Decoder<A> = Decoder {
            when {
                it.isObject ->
                    it.asObject().get(key)?.let { d1(it) }
                        ?: Result.err(Exception("Expected field \"$key\" but it was missing"))
                else ->
                    Result.err(Exception("Expected object but got ${it.javaClass.simpleName}"))
            }
        }

        fun <A, B> map(d1: Decoder<A>, f: (A) -> B): Decoder<B> = Decoder { value ->
            d1.decode(value).map { success: A ->
                f(success)
            }
        }

        // Decoder.map2(int, int, { a, b -> a + b })
        fun <A, B, C> map2(d1: Decoder<A>, d2: Decoder<B>, f: (A, B) -> C): Decoder<C> = Decoder { value ->
            d1.decode(value).flatMap { a ->
                d2.decode(value).map { b ->
                    f(a, b)
                }
            }
        }

        val string: Decoder<String> = Decoder {
            when {
                it.isString -> Result.ok(it.asString())
                else -> Result.err(Exception("Expected String but got ${it.javaClass.simpleName}"))
            }
        }

        val int: Decoder<Int> = Decoder {
            when {
                it.isNumber -> Result.ok(it.asInt())
                else -> Result.err(Exception("Expected Int but got ${it.javaClass.simpleName}"))
            }
        }

        val float: Decoder<Float> = Decoder {
            when {
                it.isNumber -> Result.ok(it.asFloat())
                else -> Result.err(Exception("Expected Float but got ${it.javaClass.simpleName}"))
            }
        }

        val double: Decoder<Double> = Decoder {
            when {
                it.isNumber -> Result.ok(it.asDouble())
                else -> Result.err(Exception("Expected Double but got ${it.javaClass.simpleName}"))
            }
        }

        val long: Decoder<Long> = Decoder {
            when {
                it.isNumber -> Result.ok(it.asLong())
                else -> Result.err(Exception("Expected Long but got ${it.javaClass.simpleName}"))
            }
        }


        val bool: Decoder<Boolean> = Decoder {
            when {
                it.isBoolean -> Result.ok(it.asBoolean())
                else -> Result.err(Exception("Expected Bool but got ${it.javaClass.simpleName}"))
            }
        }

        fun <T> `null`(defaultValue: T): Decoder<T> = Decoder {
            when {
                it.isNull -> Result.ok(defaultValue)
                else -> Result.err(Exception("Expected null but got ${it.javaClass.simpleName}"))
            }
        }

        fun <T> nullable(d1: Decoder<T>): Decoder<T?> = Decoder { value ->
            when {
                value.isNull -> Result.ok(null)
                else -> d1.decode(value)
            }
        }

        fun <A> listOf(d1: Decoder<A>): Decoder<List<A>> = Decoder {
            when {
                it.isArray -> {
                    // Short-circuit on first decode fail
                    it.asArray().map { value ->
                        val result = d1(value)
                        when (result) {
                            is Result.Err ->
                                @Suppress("UNCHECKED_CAST")
                                return@Decoder result as Result.Err<List<A>, Exception>
                            is Result.Ok ->
                                result.value
                        }
                    }.let { Result.ok(it) }
                }
                else ->
                    Result.err(Exception("Expected JSON array but got ${it.javaClass.simpleName}"))
            }
        }

        inline fun <reified A> arrayOf(d1: Decoder<A>): Decoder<Array<A>> = this.listOf(d1).map { it.toTypedArray() }

        fun <A> keyValuePairs(d1: Decoder<A>): Decoder<List<Pair<String, A>>> = Decoder {
            when {
                it.isObject -> {
                    // Short-circuit on first decode fail
                    it.asObject().map { member ->
                        val result = d1(member.value).map { value -> member.name to value }
                        when (result) {
                            is Result.Err ->
                                @Suppress("UNCHECKED_CAST")
                                return@Decoder result as Result.Err<List<Pair<String, A>>, Exception>
                            is Result.Ok ->
                                result.value
                        }
                    }.let { pairs -> Result.ok(pairs) }
                }
                else -> Result.err(Exception("Expected JSON object but got ${it.javaClass.simpleName}"))
            }
        }

        fun <A> mapOf(d1: Decoder<A>): Decoder<Map<String, A>> = keyValuePairs(d1).map { it.toMap() }

        fun <A, B> pairOf(left: Decoder<A>, right: Decoder<B>): Decoder<Pair<A, B>> = Decoder {
            when {
                it.isArray -> {
                    val array = it.asArray()
                    if (array.size() == 2) {
                        Result.all(left(array[0]), right(array[1])).map { vals ->
                            @Suppress("UNCHECKED_CAST")
                            (vals[0] as A) to (vals[1] as B)
                        }
                    } else {
                        Result.err(Exception("Expected Pair but got array with ${array.size()} items"))
                    }
                }
                else -> Result.err(Exception("Expected Pair but got ${it.javaClass.simpleName}"))
            }
        }

        fun <A, B, C> triple(d1: Decoder<A>, d2: Decoder<B>, d3: Decoder<C>): Decoder<Triple<A, B, C>> = Decoder {
            when {
                it.isArray -> {
                    val array = it.asArray()
                    if (array.size() == 3) {
                        Result.all(d1(array[0]), d2(array[1]), d3(array[2])).map { vals ->
                            @Suppress("UNCHECKED_CAST")
                            Triple(vals[0] as A, vals[1] as B, vals[2] as C)
                        }
                    } else {
                        Result.err(Exception("Expected Triple but got array with ${array.size()} items"))
                    }
                }
                else -> Result.err(Exception("Expected Pair but got ${it.javaClass.simpleName}"))
            }
        }


        fun <A> getIn(keys: List<String>, d1: Decoder<A>): Decoder<A> {
            return keys.foldRight(d1, { k, a -> get(k ,a) })
        }


        fun <A> index(i: Int, d1: Decoder<A>): Decoder<A> {
            return Decoder {
                when {
                    it.isArray -> {
                        val array = it.asArray()
                        if (i >= 0 && i < array.size()) {
                            d1(array.get(i))
                        } else {
                            Result.err(Exception("Expected index $i to be in bounds of array"))
                        }
                    }
                    else -> Result.err(Exception("Expected Array but got ${it.javaClass.simpleName}"))
                }
            }
        }

        fun <A> oneOf(vararg ds: Decoder<A>): Decoder<A> = Decoder { value ->
            ds.asSequence().map { it(value) }.find { it is Result.Ok }
                ?: Result.Err(Exception("None of the decoders matched"))
        }

        // TODO: Generalize `object` function and allow chaining.
        fun <A, Z> object1(f: (A) -> Z, d1: Decoder<A>): Decoder<Z> {
            return Decoder { value ->
                Result.all(d1(value)).map { vals ->
                    f(vals[0])
                }
            }
        }

        fun <A, B, Z> object2(f: (A, B) -> Z, d1: Decoder<A>, d2: Decoder<B>): Decoder<Z> {
            return Decoder { value ->
                Result.all(d1(value), d2(value)).map { vals ->
                    @Suppress("UNCHECKED_CAST")
                    f(vals[0] as A, vals[1] as B)
                }
            }
        }

        fun <A, B, C, Z> object3(f: (A, B, C) -> Z, d1: Decoder<A>, d2: Decoder<B>, d3: Decoder<C>): Decoder<Z> {
            return Decoder { value ->
                Result.all(d1(value), d2(value), d3(value)).map { vals ->
                    @Suppress("UNCHECKED_CAST")
                    f(vals[0] as A, vals[1] as B, vals[2] as C)
                }
            }
        }

        fun <A, B, C, D, Z> object4(f: (A, B, C, D) -> Z, d1: Decoder<A>, d2: Decoder<B>, d3: Decoder<C>, d4: Decoder<D>): Decoder<Z> {
            return Decoder { value ->
                Result.all(d1(value), d2(value), d3(value), d4(value)).map { vals ->
                    @Suppress("UNCHECKED_CAST")
                    f(vals[0] as A, vals[1] as B, vals[2] as C, vals[3] as D)
                }
            }
        }

        fun <A, B, C, D, E, Z> object5(f: (A, B, C, D, E) -> Z, d1: Decoder<A>, d2: Decoder<B>, d3: Decoder<C>, d4: Decoder<D>, d5: Decoder<E>): Decoder<Z> {
            return Decoder { value ->
                Result.all(d1(value), d2(value), d3(value), d4(value), d5(value)).map { vals ->
                    @Suppress("UNCHECKED_CAST")
                    f(vals[0] as A, vals[1] as B, vals[2] as C, vals[3] as D, vals[4] as E)
                }
            }
        }

        fun <A, B, C, D, E, F, Z> object6(f: (A, B, C, D, E, F) -> Z, d1: Decoder<A>, d2: Decoder<B>, d3: Decoder<C>, d4: Decoder<D>, d5: Decoder<E>, d6: Decoder<F>): Decoder<Z> {
            return Decoder { value ->
                Result.all(d1(value), d2(value), d3(value), d4(value), d5(value), d6(value)).map { vals ->
                    @Suppress("UNCHECKED_CAST")
                    f(vals[0] as A, vals[1] as B, vals[2] as C, vals[3] as D, vals[4] as E, vals[5] as F)
                }
            }
        }

        fun <A, B, C, D, E, F, G, Z> object7(f: (A, B, C, D, E, F, G) -> Z, d1: Decoder<A>, d2: Decoder<B>, d3: Decoder<C>, d4: Decoder<D>, d5: Decoder<E>, d6: Decoder<F>, d7: Decoder<G>): Decoder<Z> {
            return Decoder { value ->
                Result.all(d1(value), d2(value), d3(value), d4(value), d5(value), d6(value), d7(value)).map { vals ->
                    @Suppress("UNCHECKED_CAST")
                    f(vals[0] as A, vals[1] as B, vals[2] as C, vals[3] as D, vals[4] as E, vals[5] as F, vals[6] as G)
                }
            }
        }

        fun <A, B, C, D, E, F, G, H, Z> object8(f: (A, B, C, D, E, F, G, H) -> Z, d1: Decoder<A>, d2: Decoder<B>, d3: Decoder<C>, d4: Decoder<D>, d5: Decoder<E>, d6: Decoder<F>, d7: Decoder<G>, d8: Decoder<H>): Decoder<Z> {
            return Decoder { value ->
                Result.all(d1(value), d2(value), d3(value), d4(value), d5(value), d6(value), d7(value), d8(value)).map { vals ->
                    @Suppress("UNCHECKED_CAST")
                    f(vals[0] as A, vals[1] as B, vals[2] as C, vals[3] as D, vals[4] as E, vals[5] as F, vals[6] as G, vals[7] as H)
                }
            }
        }

        fun <T> succeed(value: T): Decoder<T> = Decoder {
            Result.ok(value)
        }

        fun <T> fail(message: String): Decoder<T> = Decoder {
            Result.err(Exception(message))
        }
    }
}


