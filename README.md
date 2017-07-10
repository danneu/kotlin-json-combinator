# kotlin-json-combinator [![Jitpack](https://jitpack.io/v/com.danneu/kotlin-json-combinator.svg)](https://jitpack.io/#com.danneu/kotlin-json-combinator) [![Build Status](https://travis-ci.org/danneu/kotlin-json-combinator.svg?branch=master)](https://travis-ci.org/danneu/kotlin-json-combinator)

JSON decode/encode combinators for Kotlin.

- Result monad from [danneu/kotlin-result](https://github.com/danneu/kotlin-result). 
- Extracted from [danneu/kog](https://github.com/danneu/kog).
- Inspired by [Elm](http://elm-lang.org/).

## Table of Contents

<!-- toc -->

- [Install](#install)
- [Usage](#usage)
- [Parsing](#parsing)
- [Decoding](#decoding)
  * [Basics](#basics)
    + [`.string`](#string)
    + [`.int`, `.float`, `.double`, `.long`](#int-float-double-long)
    + [`.bool`](#bool)
  * [JSON Null](#json-null)
    + [`.whenNull()`](#whennull)
    + [`.nullable()`](#nullable)
  * [JSON Arrays](#json-arrays)
    + [`.listOf()`, `.arrayOf()`](#listof-arrayof)
    + [`.pairOf()`, `.tripleOf`](#pairof-tripleof)
    + [`.index()`](#index)
  * [JSON Objects](#json-objects)
    + [`.get()`](#get)
    + [`.getIn()`](#getin)
  * [Inconsistent Structure](#inconsistent-structure)
    + [`.oneOf()`](#oneof)
  * [Transforming and Chaining](#transforming-and-chaining)
    + [`.map()`](#map)
    + [`.mapError()`](#maperror)
    + [`.andThen()`](#andthen)
    + [`.map()`, `.map2()`, `.map3()`, ..., `.map8()`](#map-map2-map3--map8)
  * [Special](#special)
    + [`.fail()`](#fail)
    + [`.succeed()`](#succeed)
    + [`.lazy()`](#lazy)
- [Encoding](#encoding)
  * [Example](#example)

<!-- tocstop -->

## Install

```kotlin
repositories {
    maven { url "https://jitpack.io" }
}

dependencies {
    compile "com.danneu:kotlin-json-combinator:x.y.z"
    // Or always get latest
    compile "com.danneu:kotlin-json-combinator:master-SNAPSHOT"
}
```

## Usage

The rest of the readme will assume these imports.

```kotlin
import com.danneu.json.Decode as JD
import com.danneu.json.Encode as JE
import com.danneu.json.JsonValue // Opaque
```

## Parsing

`Decoder.parse()` parses JSON from a string or `java.io.Reader`.

```kotlin
val result: Result<JsonValue, String> = JD.parse("[1, 2, 3]")
```

Here are some ways to get the `JsonValue` out.

- With `Result.Ok`'s `.value`:

    ```kotlin
    val result: Result<JsonValue, String> = JD.parse("[1, 2, 3]")
  
    if (result is Result.Ok) {
      result.value == listOf(1, 2, 3)
    }
    ```
    
- With `Result`'s `.getOrElse()`:

    ```kotlin
    JD.parse("[1,2,3]").getOrElse(emptyList()) == listOf(1, 2, 3)
    JD.parse("bad json").getOrElse(emptyList()) == emptyList()
    ```
    
- With `Result`'s `.getOrThrow()`:

    Only use this if you know the result had to succeed or if 
    you want to specifically want to `try/catch` 
    the `com.danneu.result.UnwrapException` upon failure.
    
    ```kotlin
    JD.parse("[1,2,3]").getOrThrow() == listOf(1, 2, 3)
    ```
    
- With `Result`'s `.map`:

    ```kotlin
    JD.parse("[1,2,3]").map { nums ->
      nums == listOf(1, 2, 3)
    }
  
    JD.parse("bad json").map { nums ->
      // will not reach this
    }
    ```
    
- With `Result`'s `.fold`:
  
    ```kotlin
    val sum = JD.parse("[1,2,3]").fold(
      { nums ->
          nums.fold(0, { a, b -> a + b })
      }, 
      { err ->
        // will not reach this
        0
      }
    )
    ```

## Decoding

`Decoder<T>` are combinators that can be invoked on a `JsonValue` to
return a `Result<T, String>`.

```kotlin
JD.parse("[1, 2, 3]").map { jsonValue ->
    val decoder = JD.listOf(JD.int)
    
    // Result<List<Int>, String>
    val result = decoder(jsonValue)
    
    result.map { nums ->
      val sum = nums.fold(0, { a, b -> a + b })
      assert(sum == 6)
    }
}
```

### Basics

#### `.string`

Decode a JSON string into a Kotlin string.

```kotlin
val decoder = JD.string

decoder(JD.parse("\"foo\"").getOrThrow()).getOrThrow() == "foo"
```

#### `.int`, `.float`, `.double`, `.long`

Decode a JSON float into a Kotlin number.

#### `.bool`

Decode a JSON boolean into a Kotlin boolean.

```kotlin
val decoder = JD.bool

decoder(JD.parse("true").getOrThrow()).getOrThrow() == true
```

### JSON Null

#### `.whenNull()`

A decoder that resolves with a default value when it encounters
a JSON null and fails otherwise.

```kotlin
val decoder = JD.whenNull(42)

decoder(JD.parse("null").getOrThrow()).getOrThrow() == 42
decoder(JD.parse("42").getOrThrow()) is Result.Err
```

Useful with `.map()` to provide a default value.

```kotlin
val decoder = JD.oneOf(
    JD.int,
    JD.whenNull(-1)
)

decoder(JD.parse("42").getOrThrow()).getOrThrow() == 42
decoder(JD.parse("null").getOrThrow()).getOrThrow() == -1
```

#### `.nullable()`

Wrap a decoder such that it succeeds with `null` if it encounters JSON `null`.

```kotlin
val decoder = JD.get("answer", JD.nullable(JD.int))

decoder(JD.parse("""{"answer": 42}""").getOrThrow()).getOrThrow() == 42
decoder(JD.parse("""{"answer": null}""").getOrThrow()).getOrThrow() == null
```

### JSON Arrays

#### `.listOf()`, `.arrayOf()`

Decode a JSON array into a Kotlin list or array.

```kotlin
val decoder = JD.listOf(JD.int)

decoder(JD.parse("[1, 2, 3]").getOrThrow()).getOrThrow() == listOf(1, 2, 3)
```

#### `.pairOf()`, `.tripleOf`

Decode a JSON array into a Kotlin pair or triple.

```kotlin
val pairDecoder = JD.pairOf(JD.int, JD.string)
pairDecoder(JD.parse("""[2, "foo"]""").getOrThrow()).getOrThrow() == Pair(2, "foo")

val tripleDecoder = JD.tripleOf(JD.int, JD.string, JD.listOf(JD.int))
tripleDecoder(JD.parse("""[2, "foo", [1, 2, 3]]""").getOrThrow()).getOrThrow() == Triple(2, "foo", listOf(1, 2, 3))
```

#### `.index()`

Decode value at specific index of JSON array.

```kotlin
val decoder = JD.index(2, JD.string)

decoder(JD.parse("""["foo", "bar", "qux"]""").getOrThrow()).getOrThrow() == "qux"
```

### JSON Objects

#### `.get()`

Decode a value at the given key of a JSON object.

```kotlin
val json = JD.parse("""
    {
        "answer": 42
    }
""").getOrThrow()

JD.get("answer", JD.int).decode(json).getOrThrow() == 42
```

#### `.getIn()`

Decode a value in a nested JSON object given a path of keys.

```kotlin
val json = JD.parse("""
    {
        "a": {
            "b": {
                "c": [1, 2, 3]
            }
        }
    }
""").getOrThrow()

val decoder = JD.getIn(listOf("a", "b", "c"), JD.listOf(JD.int))

decoder(json).getOrThrow() == listOf(1, 2, 3)
```

### Inconsistent Structure

#### `.oneOf()`

Try multiple decoders against the value until one works. Tries them left to right.

```kotlin
// Imagine an API that only responds with an array if there is more than one value.
// We want both cases to decode into a list.

val decoder = JD.obj(
    "answer", JD.oneOf(
        JD.int.map { listOf(it) },
        JD.listOf(JD.int)
    )
)

decoder(JD.parse("""{"answer": 1}""").getOrThrow()).getOrThrow() == listOf(1)
decoder(JD.parse("""{"answer": [1, 2, 3]}""").getOrThrow()).getOrThrow() == listOf(1, 2, 3)
```

### Transforming and Chaining

#### `.map()`

If decode is successful, apply a function to the value.
Else, the failed result is returned.

```kotlin
val decoder = JD.listOf(JD.int).map { nums ->
    nums.fold(0, { a, b -> a + b })
}

decoder(JD.parse("[1, 2, 3]").getOrThrow()).getOrThrow() == 6
```

#### `.mapError()`

If decode failed, transform the error.

For example, here it is used to add a more specific error
than `.oneOf`'s "None of the decoders matched" error.

```kotlin
sealed class StringOrInt {
    data class String(val value: kotlin.String): StringOrInt()
    data class Int(val value: kotlin.Int): StringOrInt()
}

val decoder = JD.oneOf(
    JD.string.map(StringOrInt::String),
    JD.int.map(StringOrInt::Int)
).mapError { "Expected String or Int" }

decoder(JD.parse("\"foo\"").getOrThrow()).getOrThrow() == StringOrInt.String("foo")
decoder(JD.parse("42").getOrThrow()).getOrThrow() == StringOrInt.Int(42)
decoder(JD.parse("null").getOrThrow()) == Result.err("Expected String or Int")
```

#### `.andThen()`

Return the next decoder to be applied to a JSON value based on the
previously decoded value.

For example, imagine if an API sends us one of:

- `{ "version": 3, "data": [Int, Int] }`
- `{ "version": 4, "data": {"a": Int, "b": Int} }`

And we want to parse `"data"` into a tuple `Pair<Int, Int>`. 

We can use `.andThen()` to return different decoders depending on the decoded `"version"` value.

```kotlin
val decoder = JD.get("version", JD.int).andThen { version ->
    val dataDecoder = when (version) {
        3 -> JD.pairOf(JD.int, JD.int)
        4 -> JD.object2(::Pair, JD.get("a", JD.int), JD.get("b", JD.int))
        else -> JD.fail("Unexpected version: $version")
    }

    JD.get("data", dataDecoder)
}

decoder(JD.parse("""{"version":3,"data":[1, 2]}""").getOrThrow()).getOrThrow() == Pair(1, 2)
decoder(JD.parse("""{"version":4,"data":{"a":1,"b":2}}""").getOrThrow()).getOrThrow() == Pair(1, 2)
decoder(JD.parse("""{"version":5,"data":null}""").getOrThrow()) is Result.Err
```

#### `.map()`, `.map2()`, `.map3()`, ..., `.map8()`

**TODO:** Applying more than eight decoders is currently unsupported.

Apply 1-8 decoders to a JSON value and then pass all results to a function that returns a Kotlin value.

```kotlin
data class Credentials(val uname: String, val password: String)

val decoder = Decoder.map2(::Credentials, 
    Decoder.get("uname", Decoder.string),
    Decoder.get("password", Decoder.string)
)

val json = JD.parse("""
    {
        "uname": "foo",
        "password": "secret"
    }
""").getOrThrow()

decoder(json).getOrThrow() == Credentials("foo", "secret")
```

Not limited to JSON objects. For example, this decoder plucks credentials
from the only two values we care about in a JSON array.

```kotlin
val decoder = Decoder.map2(::Credentials, 
    Decoder.index(1, Decoder.string),
    Decoder.get(3, Decoder.string)
)

val json = JD.parse("""[42, "foo", -1, "secret"]""").getOrThrow()

decoder(json).getOrThrow() == Credentials("foo", "secret")
```

### Special

#### `.fail()`

Create a decoder that immediately fails with an error message.

```kotlin
val decoder = JD.fail("Nope")

decoder(JD.parse("42").getOrThrow()) is Result.Err
```

Useful in `.oneOf()` and `.andThen()` when you want to give 
a custom error.

#### `.succeed()`

Create a decoders that immediately succeeds with a value.

```kotlin
// Example of using .oneOf() + .succeed() to supply a default value
val decoder = JD.get("answer", JD.oneOf(JD.int, JD.succeed(-1)))

decoder(JD.parse("""{"answer": 42}""").getOrThrow()).getOrThrow() == 42
decoder(JD.parse("""{"answer": null}""").getOrThrow()).getOrThrow() == -1
```

#### `.lazy()`

Enables recursive decoders by waiting until the last second to resolve the decoder.

In this example, a `Comment` may have children `Comment`s which may have children `Comment`s which may have..., etc.

The following snippet would cause a `StackOverflowError` because the `JD.listOf(decoder)` 
must be evaluated eagerly to define `decoder` which will be expanded infinitely due to recursion.

```kotlin
var decoder = JD.map2(
    ::Comment,
    JD.get("text", JD.string),
    JD.get("replies", JD.listOf(decoder)) // <-- The problem
)
```

Instead, `.lazy()` lets us wrap our recursive call in a function so that it will only be expanded as-needed.

```kotlin
data class Comment(val text: String, val replies: List<Comment> = emptyList()) {
    override fun toString() = "Comment(text=$text replies=$replies)"
}

var decoder: JD<Comment>? = null

decoder = JD.map2(
    ::Comment,
    JD.get("text", JD.string),
    JD.get("replies", JD.listOf(JD.lazy { decoder!! }))
)

val json = JD.parse("""
    {
        "text": "comment 1",
        "replies": [
            { "text": "comment 2", "replies": [] },
            { "text": "comment 3", "replies": [ {"text": "comment 4", "replies": []} ] }
        ]
    }
""").getOrThrow()

decoder(json).getOrThrow() == Comment("comment 1", listOf(
    Comment("comment 2"),
    Comment("comment 3", listOf(Comment("comment 4")))
))
```

Even better and cleaner: use a lazy property.

```kotlin
data class Comment(val text: String, val replies: List<Comment> = emptyList()) {
    override fun toString() = "Comment(text=$text replies=$replies)"

    companion object {
        val decoder: JD<Comment> by lazy {
            JD.map2(::Comment,
                JD.get("text", JD.string),
                JD.get("replies", JD.listOf(JD.lazy { decoder }))
            )
        }
    }
}

val json = JD.parse("""
    {
        "text": "comment 1",
        "replies": [
            { "text": "comment 2", "replies": [] },
            { "text": "comment 3", "replies": [ {"text": "comment 4", "replies": []} ] }
        ]
    }
""").getOrThrow()

Comment.decoder(json).getOrThrow() == Comment("comment 1", listOf(
    Comment("comment 2"),
    Comment("comment 3", listOf(Comment("comment 4")))
))
```

## Encoding

The Encoder is used to transform Kotlin values into `JsonValue`s with these members:
`.obj()`, `.array()`, `.num()`, `.str()`, `.bool()`, and ```.`null` ```.

Once you have a `JsonValue`, use `.toString()` or `.toPrettyString()` 
to produce the JSON string.

### Example

```kotlin
import com.danneu.json.Encoder as JE

fun main(args: Array<String>) {
    val user = object { 
       val id = 42
       val uname = "foo"
    }
    
    val jsonValue = JE.obj(
        "ok" to JE.bool(true),
        "error" to JE.`null`,
        "user" to JE.obj(
            "id" to JE.num(user.id),
            "username" to JE.str(user.uname),
            // array() supports both varargs and iterables
            "luckyNumbers" to JE.array(JE.num(3), JE.num(9), JE.num(27)),
            "favoriteColors" to JE.array(listOf(JE.string("orange"), JE.string("black")))
        )
    )
    
    println(jsonValue.toPrettyString())
}
```

JSON:

```json
{
  "ok": true,
  "error": null,
  "user": {
    "id": 42,
    "username": "foo",
    "luckyNumbers": [3, 9, 27],
    "favoriteColors": ["orange", "black"]
  }
}
```


