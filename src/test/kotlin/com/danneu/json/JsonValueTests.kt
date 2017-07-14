package com.danneu.json

import com.danneu.json.Encoder as JE
import org.junit.Assert.assertEquals
import org.junit.Test

class User(val id: Int, val uname: String)

class JsonValueTests {
    @Test
    fun testEquality() {
        val user = User(42, "dan")

        val jsonString = """{"ok":true,"error":null,"user":{"id":42,"username":"dan","luckyNumbers":[3,9,27],"favoriteColors":["orange","black"]}}"""
        val expectedValue = Decoder.parseOrThrow(jsonString)

        val jsonValue = JE.obj(
            "ok" to JE.bool(true),
            "error" to JE.`null`,
            "user" to JE.obj(
                "id" to JE.num(user.id),
                "username" to JE.str(user.uname),
                "luckyNumbers" to JE.array(JE.num(3), JE.num(9), JE.num(27)),
                "favoriteColors" to JE.array(listOf(JE.str("orange"), JE.str("black")))
            )
        )

        assertEquals(expectedValue, jsonValue)
    }
}
