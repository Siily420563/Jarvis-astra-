package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testParseSimpleCommand() {
        val steps = Parser.parse("open YouTube")
        assertNotNull(steps)
        assertEquals(1, steps!!.size)
        assertEquals("open", steps[0].action)
        assertEquals("YouTube", steps[0].argument)
    }

    @Test
    fun testParseChainedCommands() {
        val steps = Parser.parse("open YouTube then tap Search then type lofi music")
        assertNotNull(steps)
        assertEquals(3, steps!!.size)
        assertEquals(Step("open", "YouTube"), steps[0])
        assertEquals(Step("tap", "Search"), steps[1])
        assertEquals(Step("type", "lofi music"), steps[2])
    }

    @Test
    fun testParseNavigationCommands() {
        val down = Parser.parse("scroll down")
        assertNotNull(down)
        assertEquals("down", down!![0].action)

        val back = Parser.parse("go back")
        assertNotNull(back)
        assertEquals("back", back!![0].action)

        val home = Parser.parse("go home")
        assertNotNull(home)
        assertEquals("home", home!![0].action)
    }

    @Test
    fun testParseInvalidCommand() {
        val steps = Parser.parse("fly to the moon")
        assertNull(steps)
    }
}
