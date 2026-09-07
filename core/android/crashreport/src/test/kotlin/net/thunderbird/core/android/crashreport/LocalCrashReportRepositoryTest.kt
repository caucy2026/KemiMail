package net.thunderbird.core.android.crashreport

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.test.Test

class LocalCrashReportRepositoryTest {
    private val environment = CrashReportEnvironment(
        appVersion = "20.1",
        appVersionCode = 39040,
        androidSdk = 31,
        manufacturer = "KEMI",
        model = "huanglong",
    )

    @Test
    fun `record atomically replaces the single latest report and clear removes it`() {
        val file = FakeCrashReportFile()
        var timestamp = 10L
        val repository = createRepository(file) { timestamp }

        repository.record(IllegalStateException("first secret"))
        timestamp = 20L
        repository.record(IllegalArgumentException("second secret"))

        assertThat(file.writeCount).isEqualTo(2)
        assertThat(repository.readLatest()?.occurredAtEpochSeconds).isEqualTo(20L)
        repository.clear()
        assertThat(file.exists).isFalse()
        assertThat(repository.readLatest()).isNull()
    }

    @Test
    fun `corrupt or oversized report is discarded`() {
        val file = FakeCrashReportFile().apply {
            bytes = "format=1\nuntrusted=mail-body".encodeToByteArray()
        }
        val repository = createRepository(file) { 1L }

        assertThat(repository.readLatest()).isNull()
        assertThat(file.exists).isFalse()

        file.bytes = ByteArray(CrashReportCodec.MAX_PAYLOAD_BYTES + 1) { 'x'.code.toByte() }
        assertThat(repository.readLatest()).isNull()
        assertThat(file.exists).isFalse()
    }

    @Test
    fun `crash path is best effort when storage fails`() {
        val file = FakeCrashReportFile().apply { failWrites = true }
        val repository = createRepository(file) { 1L }

        repository.record(IllegalStateException("private message"))

        assertThat(file.exists).isFalse()
        assertThat(file.writeAttempted).isTrue()
    }

    private fun createRepository(
        file: CrashReportFile,
        epochSecondsProvider: () -> Long,
    ): LocalCrashReportRepository {
        return LocalCrashReportRepository(
            reportFile = file,
            reportFactory = CrashReportFactory(
                environmentProvider = { environment },
                epochSecondsProvider = epochSecondsProvider,
            ),
        )
    }
}

private class FakeCrashReportFile : CrashReportFile {
    var bytes: ByteArray? = null
    var failWrites = false
    var writeCount = 0
    var writeAttempted = false

    val exists: Boolean
        get() = bytes != null

    override fun writeAtomically(payload: ByteArray) {
        writeAttempted = true
        if (failWrites) error("simulated storage failure")
        bytes = payload.copyOf()
        writeCount++
    }

    override fun read(): ByteArray? = bytes?.copyOf()

    override fun delete() {
        bytes = null
    }
}
