package haven.mobile.core.arkiv

/**
 * The result of walking a whole Arkiv listing in one pass.
 *
 * [items] is every live record the walk returned, decoded to [haven.mobile.core.domain.MediaItem]:
 * drip SERIES headers are folded into their PARTs rather than listed as media, and expired rows are
 * dropped. [headBlock] is the chain head the node last reported, for expiry and block-timestamp work.
 * [complete] is false when the walk stopped at its page cap rather than at the end of the listing —
 * callers must not prune local state against an incomplete scan. [pages] is how many `arkiv_query`
 * pages the walk read.
 */
data class ArkivScan<T>(
    val items: List<T>,
    val headBlock: Long?,
    val complete: Boolean,
    val pages: Int,
)