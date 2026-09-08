package com.kemi.windows

/** Bounded, process-only cache; no message bodies or attachments are written to disk. */
internal class MailReadCache(private val maxBytes: Long = 16L * 1024 * 1024,private val maxEntries: Int = 40,
                             private val now: () -> Long = System::nanoTime) {
    private data class Key(val account: String,val folder: String,val validity: Long,val uid: Long)
    private data class Entry(val detail: MailDetail,val stored: Long,val bytes: Long)
    private val entries = LinkedHashMap<Key,Entry>(16,.75f,true)
    private var bytes = 0L
    fun get(account: String,folder: String,mail: MailSummary): MailDetail? {
        val key = Key(account,folder,mail.validity,mail.uid)
        val entry = entries[key] ?: return null
        if (now() - entry.stored > 300_000_000_000L) { entries.remove(key); bytes -= entry.bytes; return null }
        return entry.detail.copy(summary = mail)
    }
    fun put(account: String,folder: String,detail: MailDetail) {
        val key = Key(account,folder,detail.summary.validity,detail.summary.uid)
        entries.remove(key)?.let { bytes -= it.bytes }
        val size = detail.body.length * 2L + detail.attachments.sumOf { it.bytes.size.toLong() + it.name.length * 2L } + 1024
        if (size > maxBytes) return
        entries[key] = Entry(detail,now(),size); bytes += size
        while (bytes > maxBytes || entries.size > maxEntries) {
            val oldest = entries.entries.iterator(); val entry = oldest.next(); bytes -= entry.value.bytes; oldest.remove()
        }
    }
    fun clear() { entries.clear(); bytes = 0 }
}
