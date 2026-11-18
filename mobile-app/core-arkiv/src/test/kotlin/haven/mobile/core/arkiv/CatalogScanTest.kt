package haven.mobile.core.arkiv

import haven.mobile.core.domain.HavenChain
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Scan rows -> playable catalogue entries. These pin the join the single-scan refresh depends on:
 * drip SERIES headers are folded into their PARTs (title + gate) rather than listed as media,
 * expired rows are dropped against the scan head, and the generic-file groups (image/text/file)
 * survive as media the intersection can unlock.
 */
class CatalogScanTest {

    private val client = ArkivClientImpl(ArkivConfig(endpointUrl = "https://example.com"))

    private fun catalog(rows: List<JSONObject>, head: Long? = 1_000) = with(client) {
        catalogItems(rows, head)
    }

    @Test
    fun `generic media groups are kept as playable items`() {
        val rows = listOf(
            row(key = "0xv", grp = "haven.video.full", mime = 1),
            row(key = "0xa", grp = "haven.audio.full", mime = 4),
            row(key = "0xi", grp = "haven.image.full", mime = 8, gateToken = GATE),
            row(key = "0xt", grp = "haven.text.full", mime = 13, gateToken = GATE),
            row(key = "0xf", grp = "haven.file.full", mime = 14, gateToken = GATE),
        )
        val items = catalog(rows)
        assertEquals(listOf("0xv", "0xa", "0xi", "0xt", "0xf"), items.map { it.id })
        // A gated image/doc/file carries a gate just like a video, so it is openable.
        val image = items.first { it.id == "0xi" }
        assertEquals(GATE.lowercase(), image.gate?.tokenAddress?.lowercase())
        assertEquals(HavenChain.BASE_MAINNET, image.gate?.let { HavenChain.parse(it.chain) })
    }

    @Test
    fun `series headers are not media and fold into their parts`() {
        val rows = listOf(
            row(key = "0xs", grp = "haven.video.drip.series", dripId = DRIP, title = "Genesis", gateToken = GATE),
            row(key = "0xp", grp = "haven.video.drip.part", dripId = DRIP, dripIdx = 0, mime = 1),
        )
        val items = catalog(rows)
        // Only the part is media; the series header is consumed by the join.
        assertEquals(listOf("0xp"), items.map { it.id })
        val part = items.single()
        assertEquals("Genesis — part 1", part.title)
        // The part carries no gate of its own; it inherits the series header's.
        assertEquals(GATE.lowercase(), part.gate?.tokenAddress?.lowercase())
    }

    @Test
    fun `part without its series keeps what it has`() {
        val rows = listOf(row(key = "0xp", grp = "haven.video.drip.part", dripId = "missing", dripIdx = 2, mime = 1))
        val items = catalog(rows)
        assertEquals(1, items.size)
        assertNull(items.single().gate)
    }

    @Test
    fun `expired rows are dropped against the scan head`() {
        val rows = listOf(
            row(key = "0xlive", grp = "haven.video.full", mime = 1, expiresAt = 2_000),
            row(key = "0xdead", grp = "haven.video.full", mime = 1, expiresAt = 1_000),
        )
        val items = catalog(rows, head = 1_000)
        assertEquals(listOf("0xlive"), items.map { it.id })
    }

    @Test
    fun `missing head fails open and keeps the row`() {
        val rows = listOf(row(key = "0xv", grp = "haven.video.full", mime = 1, expiresAt = 1))
        assertTrue(catalog(rows, head = null).isNotEmpty())
    }

    @Test
    fun `rows without an id are dropped`() {
        val rows = listOf(row(key = "", grp = "haven.video.full", mime = 1))
        assertTrue(catalog(rows).isEmpty())
    }

    /** A normalized scan row: the flat keys `toMediaItem` reads, with the fields a test varies. */
    private fun row(
        key: String,
        grp: String,
        mime: Int? = null,
        gateToken: String? = null,
        expiresAt: Long? = null,
        dripId: String? = null,
        dripIdx: Int? = null,
        title: String? = null,
    ): JSONObject = JSONObject().apply {
        put("key", key)
        put("owner", "0xowner")
        put("grp", grp)
        mime?.let { put("mime", it) }
        title?.let { put("title", it) }
        gateToken?.let {
            put("gate_token", it)
            put("gate_chain", 8453)
            put("gate_threshold", 1)
        }
        expiresAt?.let { put("expiresAtBlock", it) }
        dripId?.let { put("drip_id", it) }
        dripIdx?.let { put("drip_idx", it) }
    }

    private companion object {
        const val GATE = "0xABCDEF1234567890abcdef1234567890ABCDEF12"
        const val DRIP = "drip-uuid-1"
    }
}