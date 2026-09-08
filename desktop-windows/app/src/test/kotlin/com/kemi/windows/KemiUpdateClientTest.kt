package com.kemi.windows

import assertk.assertThat
import assertk.assertions.*
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class KemiUpdateClientTest {
    private val bytes = "synthetic installer".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun response(code: Int = 10401,extra: String = "") = """{"status":200,"data":{
        "package_name":"${MailRelease.packageName}","os_type":"windows","local_version_code":10400,
        "has_update":true,"version_name":"1.4.1","version_code":$code,
        "download_url":"https://example.invalid/setup.exe","sha256":"$hash","file_size_bytes":${bytes.size},
        "force_update":false,"release_notes":"Update"$extra}}"""
    @Test fun `accepts valid matching Windows update`() {
        val testSubject = KemiUpdateClient()
        val result = testSubject.parse(response(),10400)!!
        assertThat(result.code).isEqualTo(10401); assertThat(result.size).isEqualTo(bytes.size.toLong())
    }
    @Test fun `same version is not an upgrade`() {
        assertFailsWith<UpdateFailure> { KemiUpdateClient().parse(response(10400),10400) }
    }
    @Test fun `rejects other platform`() {
        assertFailsWith<UpdateFailure> { KemiUpdateClient().parse(response().replace("windows","android"),10400) }
    }
    @Test fun `rejects other package`() {
        assertFailsWith<UpdateFailure> { KemiUpdateClient().parse(response().replace(MailRelease.packageName,"other.app"),10400) }
    }
    @Test fun `rejects stale local version`() {
        assertFailsWith<UpdateFailure> { KemiUpdateClient().parse(response(),10300) }
    }
    @Test fun `rejects missing hash`() {
        assertFailsWith<UpdateFailure> { KemiUpdateClient().parse(response().replace(hash,""),10400) }
    }
    @Test fun `rejects non HTTPS and embedded credentials`() {
        for (url in listOf("http://example.invalid/setup.exe","https://user:pass@example.invalid/setup.exe","file:///setup.exe")) {
            assertFailsWith<UpdateFailure> { KemiUpdateClient.secureUri(url) }
        }
    }
    @Test fun `rejects zip as executable updater`() {
        assertFailsWith<UpdateFailure> { KemiUpdateClient().parse(response().replace("setup.exe","setup.zip"),10400) }
    }
    @Test fun `accepts no update without download metadata`() {
        val testSubject = KemiUpdateClient()
        assertThat(testSubject.parse("""{"status":200,"data":{"has_update":false,"package_name":"${MailRelease.packageName}","os_type":"windows","local_version_code":10400}}""",10400)).isNull()
    }
    @Test fun `accepts legacy field aliases`() {
        assertThat(KemiUpdateClient().parse(response().replace("download_url","apk_url").replace("sha256","apk_sha256").replace("file_size_bytes","file_size"),10400)!!.sha256).isEqualTo(hash)
    }
    @Test fun `valid download retains verified bytes`() = runTest {
        val directory = Files.createTempDirectory("kemi-update-test")
        val target = directory.resolve("setup.exe")
        try {
            val testSubject = KemiUpdateClient()
            testSubject.copyVerified(ByteArrayInputStream(bytes),target,testSubject.parse(response(),10400)!!) {}
            assertThat(Files.readAllBytes(target).toList()).isEqualTo(bytes.toList())
        } finally { Files.deleteIfExists(target); Files.delete(directory) }
    }
    @Test fun `truncated oversized and corrupt downloads are removed`() = runTest {
        val directory = Files.createTempDirectory("kemi-update-test")
        val target = directory.resolve("setup.exe")
        try {
            val testSubject = KemiUpdateClient()
            for (bad in listOf(bytes.copyOf(bytes.size-1),bytes + 0,ByteArray(bytes.size))) {
                assertFailsWith<UpdateFailure> {
                    testSubject.copyVerified(ByteArrayInputStream(bad),target,testSubject.parse(response(),10400)!!) {}
                }
                assertThat(Files.exists(target)).isFalse()
            }
        } finally { Files.deleteIfExists(target); Files.delete(directory) }
    }
}
