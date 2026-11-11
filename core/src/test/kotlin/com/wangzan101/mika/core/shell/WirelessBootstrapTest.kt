package com.wangzan101.mika.core.shell

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WirelessBootstrapTest {
    private class Fake : WirelessBootstrap.Port {
        val calls = mutableListOf<String>()
        var accepted = true
        var port: Int? = 40000
        var fail: String? = null
        var paired = false
        override suspend fun start(name: String, password: String): Boolean {
            calls += "start"
            assertTrue(name.startsWith("mika-"))
            assertEquals(32, password.length)
            return accepted
        }
        override suspend fun find(name: String): Int? { calls += "find"; return port }
        override suspend fun pair(port: Int, password: String) {
            calls += "pair"
            if (fail == "pair") error("pair rejected")
            if (fail == "cancel") throw CancellationException()
        }
        override suspend fun verify() { calls += "verify"; if (fail == "verify") error("key not trusted") }
        override fun savePaired() { calls += "save"; paired = true }
        override suspend fun stop(name: String) { calls += "stop" }
    }

    @Test fun savesOnlyAfterRealConnectionVerificationAndCleansUp() = runTest {
        val f = Fake()
        assertTrue(WirelessBootstrap(f).initialize())
        assertEquals(listOf("start", "find", "pair", "verify", "save", "stop"), f.calls)
        assertTrue(f.paired)
    }

    @Test fun unsupportedServiceAndMissingPortDoNotSavePairing() = runTest {
        for (f in listOf(Fake().apply { accepted = false }, Fake().apply { port = null })) {
            assertFalse(WirelessBootstrap(f).initialize())
            assertFalse(f.paired)
            assertEquals("stop", f.calls.last())
        }
    }

    @Test fun protocolAndVerificationFailuresNeverMarkInitialized() = runTest {
        for (stage in listOf("pair", "verify")) {
            val f = Fake().apply { fail = stage }
            assertFalse(WirelessBootstrap(f).initialize())
            assertFalse(f.paired)
            assertFalse("save" in f.calls)
            assertEquals("stop", f.calls.last())
        }
    }

    @Test fun cancellationStillStopsTemporaryService() = runTest {
        val f = Fake().apply { fail = "cancel" }
        try { WirelessBootstrap(f).initialize(); fail("expected cancellation") }
        catch (_: CancellationException) { }
        assertFalse(f.paired)
        assertEquals("stop", f.calls.last())
    }
}
