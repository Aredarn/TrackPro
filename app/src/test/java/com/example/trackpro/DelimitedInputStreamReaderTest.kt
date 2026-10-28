package com.example.trackpro

import com.example.trackpro.managerClasses.gpsDataManagers.DelimitedInputStreamReader
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class DelimitedInputStreamReaderTest {

    private fun reader(text: String) =
        DelimitedInputStreamReader(ByteArrayInputStream(text.toByteArray()), "\n".toByteArray())

    private fun ByteArray.line(len: Int) = decodeToString(0, len)

    @Test
    fun `reads consecutive lines then reports end of stream`() {
        val r = reader("NOFIX\n{\"a\":1}\n")
        val buf = ByteArray(64)
        assertEquals("NOFIX", buf.line(r.read(buf)))
        assertEquals("{\"a\":1}", buf.line(r.read(buf)))
        assertEquals(-1, r.read(buf))
    }

    @Test
    fun `truncates a line longer than the target buffer`() {
        val r = reader("0123456789\n")
        val buf = ByteArray(4)
        assertEquals(4, r.read(buf))
        assertEquals("0123", buf.line(4))
    }

    @Test
    fun `drops a runaway line without a delimiter`() {
        val r = reader("x".repeat(3000) + "\nOK\n")
        val buf = ByteArray(64)
        // The guard resets at 2048 bytes; the 951-byte remainder comes out truncated to the buffer.
        assertEquals(64, r.read(buf))
        assertEquals("OK", buf.line(r.read(buf)))
    }
}
