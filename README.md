
# json-combinator

JSON decode/encode combinators for Kotlin.

- Result monad from [danneu/kotlin-result](https://github.com/danneu/kotlin-result). 
- Extracted from [danneu/kog](https://github.com/danneu/kog).
- Inspired by [Elm](http://elm-lang.org/).

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

```kotlin
import com.danneu.json.combinator.Decode as JD
import com.danneu.json.combinator.Encode as JE
import com.danneu.json.combinator.JsonValue // Opaque
```

### Parsing

`Decoder.parse()` parses a JSON string.

```kotlin
val result: Result<JsonValue, Exception> = JD.parse("[1, 2, 3]")
```

Here are some ways to get the `JsonValue` out.

- With `Result.Ok`'s `.value`:

    ```kotlin
    val result: Result<JsonValue, Exception> = JD.parse("[1, 2, 3]")
  
    if (result is Result.Ok) {
      result.value == listOf(1, 2, 3)
    }
    ```
    
- With `Result`'s `.getOrElse`:

    ```kotlin
    JD.parse("[1,2,3]").getOrElse(emptyList()) == listOf(1, 2, 3)
    JD.parse("bad json").getOrElse(emptyList()) == emptyList()
    ```
   
- With `Result`'s `.map`/`.fold`:

    ```kotlin
    JD.parse("[1,2,3]").map { nums ->
      nums == listOf(1, 2, 3)
    }
  
    JD.parse("bad json").map { nums ->
      // will not reach this
    }
  
    JD.parse("[1,2,3]").fold(
      { nums ->
          nums == listOf(1, 2, 3)
      }, 
      { err ->
        // will not reach this
      }
    )
    ```

### Decoding

`Decoder<T>` are combinators that can be invoked on `JsonValue` to
return a `Result<T, Exception>`.

```kotlin
JD.parse("[1, 2, 3]").map { jsonValue ->
    val decoder = JD.listOf(JD.int)
    
    // Result<List<Int>, Exception>
    val result = decoder(jsonValue)
    
    result.map { nums ->
      val sum = nums.fold(0, { a, b -> a + b })
      assert(sum == 6)
    }
}

```

TODO

### Encoding

TODO
