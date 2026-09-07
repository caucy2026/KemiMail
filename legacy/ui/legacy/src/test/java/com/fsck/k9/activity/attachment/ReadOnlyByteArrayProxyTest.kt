package com.fsck.k9.activity.attachment

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReadOnlyByteArrayProxyTest {
    @Test
    fun `proxy exposes bounded random reads without a file path`() {
        val testSubject = ReadOnlyByteArrayProxy(byteArrayOf(10, 20, 30, 40, 50))
        val destination = ByteArray(4)

        assertThat(testSubject.onGetSize()).isEqualTo(5L)
        assertThat(testSubject.onRead(offset = 1, size = 3, data = destination)).isEqualTo(3)
        assertThat(destination.toList()).containsExactly(20.toByte(), 30.toByte(), 40.toByte(), 0.toByte())
        assertThat(testSubject.onRead(offset = 5, size = 3, data = destination)).isEqualTo(0)
        assertThat(testSubject.onRead(offset = -1, size = 3, data = destination)).isEqualTo(0)
    }
}
