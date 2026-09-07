package net.thunderbird.core.android.crashreport

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidAtomicCrashReportFileTest {
    private lateinit var directory: File
    private lateinit var reportFile: AndroidAtomicCrashReportFile

    @BeforeTest
    fun setUp() {
        directory = createTempDirectory(prefix = "crash-report-").toFile()
        reportFile = AndroidAtomicCrashReportFile(File(directory, "latest.report"))
    }

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `write replaces previous payload and delete removes it`() {
        reportFile.writeAtomically("first".encodeToByteArray())
        reportFile.writeAtomically("second".encodeToByteArray())

        assertThat(reportFile.read()?.decodeToString()).isEqualTo("second")
        reportFile.delete()
        assertThat(reportFile.read()).isNull()
    }

    @Test
    fun `oversized on-disk report is deleted without loading it`() {
        File(directory, "latest.report").writeBytes(ByteArray(CrashReportCodec.MAX_PAYLOAD_BYTES + 1))

        assertThat(reportFile.read()).isNull()
        assertThat(File(directory, "latest.report").exists()).isEqualTo(false)
    }
}
