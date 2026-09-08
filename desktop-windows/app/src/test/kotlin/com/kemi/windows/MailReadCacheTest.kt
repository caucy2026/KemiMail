package com.kemi.windows

import kotlin.test.*

class MailReadCacheTest {
    private val mail = MailSummary(1,1,"Synthetic","Fixture",null,false,false)
    private fun detail(summary: MailSummary = mail,body: String = "正文") = MailDetail(summary,"","",null,body,emptyList())
    @Test fun `cache isolates accounts folders and UID validity and uses current flags`() {
        val testSubject = MailReadCache()
        testSubject.put("a","INBOX",detail())
        assertEquals("正文",testSubject.get("a","INBOX",mail)?.body)
        assertNull(testSubject.get("b","INBOX",mail))
        assertNull(testSubject.get("a","Sent",mail))
        assertNull(testSubject.get("a","INBOX",mail.copy(validity = 2)))
        assertEquals(true,testSubject.get("a","INBOX",mail.copy(seen = true))?.summary?.seen)
        testSubject.clear(); assertNull(testSubject.get("a","INBOX",mail))
    }
    @Test fun `cache expires and evicts the least recently used message`() {
        var time = 0L
        val testSubject = MailReadCache(maxEntries = 2,now = { time })
        testSubject.put("a","INBOX",detail())
        testSubject.put("a","INBOX",detail(mail.copy(uid = 2)))
        assertNotNull(testSubject.get("a","INBOX",mail))
        testSubject.put("a","INBOX",detail(mail.copy(uid = 3)))
        assertNull(testSubject.get("a","INBOX",mail.copy(uid = 2)))
        time = 301_000_000_000L
        assertNull(testSubject.get("a","INBOX",mail))
    }
    @Test fun `oversized content cannot exceed cache memory bound`() {
        val testSubject = MailReadCache(maxBytes = 1100)
        testSubject.put("a","INBOX",detail())
        testSubject.put("a","INBOX",detail(mail.copy(uid = 2),"x".repeat(1000)))
        assertNotNull(testSubject.get("a","INBOX",mail))
        assertNull(testSubject.get("a","INBOX",mail.copy(uid = 2)))
    }
}
