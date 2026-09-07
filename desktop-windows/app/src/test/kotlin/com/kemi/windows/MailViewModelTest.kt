package com.kemi.windows

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.nio.file.Files
import kotlin.test.Test
import assertk.assertThat
import assertk.assertions.*

private class FakeGateway : MailGateway {
    var sendCount = 0
    var sendFailure: MailFailure? = null
    var readGate: CompletableDeferred<Unit>? = null
    private val summary = MailSummary(1,1,"Synthetic","Fixture",null,false,false)
    override suspend fun check(account: Account) = Unit
    override suspend fun folders(account: Account) = listOf(MailFolder("INBOX","收件箱"))
    override suspend fun list(account: Account,folder: String,limit: Int) = listOf(summary)
    override suspend fun read(account: Account,folder: String,mail: MailSummary): MailDetail {
        readGate?.await()
        return MailDetail(summary,"fixture@example.invalid","fixture@example.invalid",null,"Synthetic",emptyList())
    }
    override suspend fun flag(account: Account,folder: String,mail: MailSummary,seen: Boolean?,starred: Boolean?) = Unit
    override suspend fun trash(account: Account,folder: String,mail: MailSummary,destination: String) = Unit
    override suspend fun send(account: Account,draft: ComposeDraft): SendResult {
        sendCount++; sendFailure?.let { throw it }; return SendResult(false)
    }
}
class MailViewModelTest {
    private suspend fun MailViewModel.idle() = withTimeout(5000) { state.first { !it.busy } }
    private fun test(block: suspend (MailViewModel,FakeGateway,AccountVault,Account) -> Unit) = runBlocking {
        val dir = Files.createTempDirectory("mail-state-test-")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val account = Account(email = "fixture@example.invalid",username = "fixture",password = "test-only",
                imapHost = "imap.example.invalid",smtpHost = "smtp.example.invalid")
            val vault = AccountVault(dir,FakeProtector()); vault.save(listOf(account))
            val gateway = FakeGateway()
            val testSubject = MailViewModel(gateway,vault,scope); testSubject.idle()
            block(testSubject,gateway,vault,account)
        } finally { scope.cancel(); Files.walk(dir).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
    @Test fun `accepted mail with failed Sent copy closes draft without retry`() = test { testSubject,gateway,vault,account ->
        testSubject.dispatch(MailEvent.Compose()); testSubject.idle()
        testSubject.dispatch(MailEvent.EditDraft(ComposeDraft(to = account.email,body = "Synthetic")))
        testSubject.dispatch(MailEvent.Send); testSubject.idle()
        assertThat(gateway.sendCount).isEqualTo(1)
        assertThat(testSubject.state.value.draft).isNull()
        assertThat(testSubject.state.value.status).contains("邮件已发送")
        assertThat(vault.loadDraft(account.id)).isEqualTo(ComposeDraft())
    }
    @Test fun `uncertain send preserves encrypted draft and never retries automatically`() = test { testSubject,gateway,vault,account ->
        val draft = ComposeDraft(to = account.email,body = "Synthetic")
        gateway.sendFailure = MailFailure("未能确认发送结果，请先检查网页版")
        testSubject.dispatch(MailEvent.Compose()); testSubject.idle()
        testSubject.dispatch(MailEvent.EditDraft(draft))
        testSubject.dispatch(MailEvent.Send); testSubject.idle()
        assertThat(gateway.sendCount).isEqualTo(1)
        assertThat(testSubject.state.value.error).isTrue()
        assertThat(testSubject.state.value.draft).isEqualTo(draft)
        assertThat(vault.loadDraft(account.id)).isEqualTo(draft)
    }
    @Test fun `selection cannot change while message request is running`() = test { testSubject,gateway,_,account ->
        testSubject.dispatch(MailEvent.Refresh); testSubject.idle()
        val gate = CompletableDeferred<Unit>(); gateway.readGate = gate
        testSubject.dispatch(MailEvent.Read(testSubject.state.value.messages.single()))
        testSubject.dispatch(MailEvent.SelectAccount(account.copy(id = "other")))
        assertThat(testSubject.state.value.account?.id).isEqualTo(account.id)
        gate.complete(Unit); testSubject.idle()
        assertThat(testSubject.state.value.detail?.summary?.uid).isEqualTo(1L)
    }
    @Test fun `closing compose saves the current draft and restores it next time`() = test { testSubject,_,vault,account ->
        val draft = ComposeDraft(to = account.email,body = "Synthetic draft")
        testSubject.dispatch(MailEvent.Compose()); testSubject.idle()
        testSubject.dispatch(MailEvent.EditDraft(draft))
        testSubject.dispatch(MailEvent.CloseDraft); testSubject.idle()
        assertThat(testSubject.state.value.draft).isNull()
        assertThat(vault.loadDraft(account.id)).isEqualTo(draft)
        testSubject.dispatch(MailEvent.Compose()); testSubject.idle()
        assertThat(testSubject.state.value.draft).isEqualTo(draft)
    }
}
