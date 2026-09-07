package net.thunderbird.feature.smartassistant.internal

import net.thunderbird.feature.smartassistant.SmartAssistantText

internal data class SmartAssistantCacheKey(
    val accountHash: String,
    val contextHash: String,
    val requestHash: String,
)

internal class SmartAssistantMemoryCache(
    private val maxEntries: Int,
    private val ttlMillis: Long,
    private val nowMillis: () -> Long,
) {
    private val entries = LinkedHashMap<SmartAssistantCacheKey, CacheEntry>(INITIAL_CAPACITY, LOAD_FACTOR, true)

    @Synchronized
    fun get(key: SmartAssistantCacheKey): SmartAssistantText? {
        val entry = entries[key] ?: return null
        return if (nowMillis() - entry.createdAtMillis >= ttlMillis) {
            entries.remove(key)
            null
        } else {
            entry.output
        }
    }

    @Synchronized
    fun put(key: SmartAssistantCacheKey, output: SmartAssistantText) {
        entries[key] = CacheEntry(output, nowMillis())
        while (entries.size > maxEntries) {
            entries.entries.iterator().run {
                next()
                remove()
            }
        }
    }

    @Synchronized
    fun invalidateContext(contextHash: String) {
        entries.keys.removeAll { it.contextHash == contextHash }
    }

    @Synchronized
    fun invalidateAccount(accountHash: String) {
        entries.keys.removeAll { it.accountHash == accountHash }
    }

    private data class CacheEntry(
        val output: SmartAssistantText,
        val createdAtMillis: Long,
    )

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}
