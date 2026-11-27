package ir.thepoo7an.chand.data

/**
 * One cache-first rule shared by the in-app feed and the five widgets.
 *
 * The persisted snapshot is always the first thing rendered; the network is only a *revalidation*
 * step. A snapshot younger than [SOFT_TTL_MS] counts as fresh, so revalidating it would be wasted
 * work: Android kills backgrounded processes aggressively, which means a quick "switch away and
 * back" used to re-run a full multi-provider fetch for data that was seconds old.
 *
 * A snapshot that is older than the soft TTL is served immediately and revalidated in the
 * background, which is exactly the stale-while-revalidate contract the UI and widgets expect.
 */
object SnapshotPolicy {

    /** Age under which a snapshot is served without asking the providers again. */
    const val SOFT_TTL_MS = 30_000L

    /**
     * Age of a snapshot fetched at [updatedAtMillis], evaluated at [nowMillis].
     *
     * Returns [Long.MAX_VALUE] — "never fresh" — for a missing timestamp and for a timestamp in
     * the future (a device clock that jumped backwards must not pin a snapshot as fresh forever).
     */
    fun ageMillis(updatedAtMillis: Long, nowMillis: Long): Long = when {
        updatedAtMillis <= 0L -> Long.MAX_VALUE
        nowMillis < updatedAtMillis -> Long.MAX_VALUE
        else -> nowMillis - updatedAtMillis
    }

    /** True when a snapshot of this age may be served without revalidating. */
    fun isFresh(ageMillis: Long, softTtlMillis: Long = SOFT_TTL_MS): Boolean =
        ageMillis >= 0L && ageMillis < softTtlMillis

    /** Convenience overload for callers that hold the snapshot timestamp. */
    fun isFresh(
        updatedAtMillis: Long,
        nowMillis: Long,
        softTtlMillis: Long = SOFT_TTL_MS
    ): Boolean = isFresh(ageMillis(updatedAtMillis, nowMillis), softTtlMillis)

    /** True when the network must be asked again; a missing snapshot always needs a fetch. */
    fun shouldRevalidate(
        updatedAtMillis: Long?,
        nowMillis: Long,
        softTtlMillis: Long = SOFT_TTL_MS
    ): Boolean {
        val updatedAt = updatedAtMillis ?: return true
        return !isFresh(updatedAt, nowMillis, softTtlMillis)
    }
}
