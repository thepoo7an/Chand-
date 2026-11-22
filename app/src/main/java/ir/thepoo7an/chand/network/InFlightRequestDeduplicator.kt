package ir.thepoo7an.chand.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Single-flight coordinator that coalesces concurrent identical in-flight async operations
 * under [key] into a single underlying execution.
 *
 * - Concurrent callers await the same shared [Deferred].
 * - Cancelling one waiter does not cancel the work for other active waiters; the underlying
 *   [Deferred] is cancelled only if all waiters cancel before completion.
 * - Entries are removed immediately upon completion so subsequent requests are never stale-cached.
 */
class InFlightRequestDeduplicator<K : Any, V> {

    private class Entry<V>(
        val deferred: Deferred<V>,
        val waiters: AtomicInteger = AtomicInteger(0)
    )

    private val lock = Any()
    private val inFlight = ConcurrentHashMap<K, Entry<V>>()
    private val sharedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun execute(
        key: K,
        block: suspend () -> V
    ): V {
        val entry = synchronized(lock) {
            val existing = inFlight[key]?.takeIf { !it.deferred.isCompleted && !it.deferred.isCancelled }
            if (existing != null) {
                existing.waiters.incrementAndGet()
                existing
            } else {
                lateinit var created: Entry<V>
                val deferred = sharedScope.async {
                    try {
                        block()
                    } finally {
                        synchronized(lock) {
                            inFlight.remove(key, created)
                        }
                    }
                }
                created = Entry(deferred = deferred, waiters = AtomicInteger(1))
                inFlight[key] = created
                created
            }
        }

        return try {
            entry.deferred.await()
        } finally {
            synchronized(lock) {
                val remaining = entry.waiters.decrementAndGet()
                if (remaining <= 0 && !entry.deferred.isCompleted) {
                    inFlight.remove(key, entry)
                    entry.deferred.cancel()
                }
            }
        }
    }

    internal fun activeCount(): Int = inFlight.size
}
