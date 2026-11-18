package haven.mobile.core.arkiv

import haven.mobile.core.domain.error.HavenError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * The read budget, pinned without a network: identical queries in flight collapse into one, a 429
 * (or a quota reading over the limit) starts a cooldown during which calls fail fast, and an
 * explicit invalidate reads the chain again.
 */
class ArkivCallGateTest {

    private var clock = 1_000_000L
    private val gate = ArkivCallGate { clock }

    @Test
    fun `identical query collapses into one call`() {
        var calls = 0
        val block = { calls++; "v" }
        assertEquals("v", gate.call("same") { block() })
        assertEquals("v", gate.call("same") { block() })
        assertEquals(1, calls)
    }

    @Test
    fun `different keys each go through`() {
        var calls = 0
        val block = { calls++; "v" }
        gate.call("a") { block() }
        gate.call("b") { block() }
        assertEquals(2, calls)
    }

    @Test
    fun `cached answer expires after the ttl`() {
        var calls = 0
        val block = { calls++; "v" }
        gate.call("k") { block() }
        clock += ArkivCallGate.RESULT_TTL_MILLIS + 1
        gate.call("k") { block() }
        assertEquals(2, calls)
    }

    @Test
    fun `invalidate forces a fresh read`() {
        var calls = 0
        val block = { calls++; "v" }
        gate.call("k") { block() }
        gate.invalidate()
        gate.call("k") { block() }
        assertEquals(2, calls)
    }

    @Test
    fun `429 starts a cooldown that fails fast`() {
        gate.noteResponse(httpCode = 429, retryAfterSeconds = null, quotaUsedPercent = null)
        assertThrows(HavenError.RateLimited::class.java) {
            gate.call("k") { "should not run" }
        }
    }

    @Test
    fun `quota over the block percent starts a cooldown`() {
        gate.noteResponse(httpCode = 200, retryAfterSeconds = null, quotaUsedPercent = ArkivCallGate.QUOTA_BLOCK_PERCENT)
        assertThrows(HavenError.RateLimited::class.java) {
            gate.call("k") { "should not run" }
        }
    }

    @Test
    fun `cooldown clears once its window passes`() {
        gate.noteResponse(httpCode = 429, retryAfterSeconds = 30, quotaUsedPercent = null)
        clock += 30_000
        assertEquals("v", gate.call("k") { "v" })
    }
}