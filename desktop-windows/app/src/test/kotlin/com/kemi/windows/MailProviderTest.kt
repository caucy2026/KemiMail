package com.kemi.windows

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MailProviderTest {
    @Test fun `Alibaba supports custom domains and preserves account identity and credentials`() {
        val original = Account(name = "Work",email = "person@example.invalid",username = "old-user",password = "synthetic",
            imapPort = 143,smtpPort = 587,imapSecurity = Security.STARTTLS,smtpSecurity = Security.STARTTLS)
        val configured = MailProvider.ALIBABA.applyTo(original)
        assertEquals(original.id,configured.id)
        assertEquals(original.name,configured.name)
        assertEquals(original.email,configured.email)
        assertEquals(original.password,configured.password)
        assertEquals(original.email,configured.username)
        assertEquals("imap.qiye.aliyun.com",configured.imapHost)
        assertEquals("smtp.qiye.aliyun.com",configured.smtpHost)
        assertEquals(993,configured.imapPort)
        assertEquals(465,configured.smtpPort)
        assertEquals(Security.TLS,configured.imapSecurity)
        assertEquals(Security.TLS,configured.smtpSecurity)
        configured.validate()
    }

    @Test fun `preset selection requires both hosts ports and TLS`() {
        val account = MailProvider.ALIBABA.applyTo(Account())
        assertTrue(MailProvider.ALIBABA.matches(account,"993","465"))
        assertFalse(MailProvider.ALIBABA.matches(account,"993","587"))
        assertFalse(MailProvider.ALIBABA.matches(account.copy(smtpHost = "smtp.example.invalid"),"993","465"))
        assertFalse(MailProvider.ALIBABA.matches(account.copy(imapSecurity = Security.STARTTLS),"993","465"))
        assertFalse(MailProvider.QQ.matches(account,"993","465"))
    }

    @Test fun `switching presets resets ports without clearing credentials`() {
        val account = Account(email = "person@example.invalid",username = "custom",password = "synthetic")
        MailProvider.entries.forEach { provider ->
            val result = provider.applyTo(account)
            assertTrue(provider.matches(result,result.imapPort.toString(),result.smtpPort.toString()))
            assertEquals(account.password,result.password)
        }
        assertEquals("custom",MailProvider.QQ.applyTo(account).username)
    }

    @Test fun `filters combine search and flags while preserving message order`() {
        val unread = MailSummary(1,1,"Project plan","Team",null,false,false)
        val starred = unread.copy(uid = 2,seen = true,starred = true)
        val other = unread.copy(uid = 3,subject = "Other",sender = "Someone",seen = true)
        val messages = listOf(unread,starred,other)
        assertEquals(messages,filterMessages(messages,"",MailFilter.ALL))
        assertEquals(listOf(unread),filterMessages(messages," TEAM ",MailFilter.UNREAD))
        assertEquals(listOf(starred),filterMessages(messages,"project",MailFilter.STARRED))
        assertEquals(emptyList(),filterMessages(messages,"missing",MailFilter.STARRED))
    }
}
