package app.k9mail.feature.account.setup.ui.credential

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.matches
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertFails
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CredentialTransferCryptoTest {
    @Test
    fun `decrypt should interoperate with browser encryption protocol`() {
        val sessionId = "test-session-id"
        val receiverKeys = CredentialTransferCrypto.generateKeyPair()
        val envelope = encryptLikeBrowser(
            sessionId = sessionId,
            receiverPublicKey = receiverKeys,
            plaintext = "{\"authorizationCode\":\"app-password\"}".toByteArray(),
        )

        val plaintext = CredentialTransferCrypto.decrypt(
            sessionId = sessionId,
            receiverKeys = receiverKeys,
            senderPublicKey = envelope.senderPublicKey,
            nonce = envelope.nonce,
            ciphertext = envelope.ciphertext,
        )

        assertThat(String(plaintext, StandardCharsets.UTF_8)).isEqualTo(
            "{\"authorizationCode\":\"app-password\"}",
        )
    }

    @Test
    fun `decrypt should reject a different tablet key`() {
        val receiverKeys = CredentialTransferCrypto.generateKeyPair()
        val envelope = encryptLikeBrowser(
            sessionId = "session-id",
            receiverPublicKey = receiverKeys,
            plaintext = "secret".toByteArray(),
        )

        assertFails {
            CredentialTransferCrypto.decrypt(
                sessionId = "session-id",
                receiverKeys = CredentialTransferCrypto.generateKeyPair(),
                senderPublicKey = envelope.senderPublicKey,
                nonce = envelope.nonce,
                ciphertext = envelope.ciphertext,
            )
        }
    }

    @Test
    fun `verification code should contain six digits`() {
        val keys = CredentialTransferCrypto.generateKeyPair()

        val code = CredentialTransferCrypto.verificationCode("session", keys.public.encoded, "write-token")

        assertThat(code).matches(Regex("\\d{6}"))
    }

    private fun encryptLikeBrowser(
        sessionId: String,
        receiverPublicKey: KeyPair,
        plaintext: ByteArray,
    ): Envelope {
        val senderKeys = CredentialTransferCrypto.generateKeyPair()
        val sharedSecret = KeyAgreement.getInstance("ECDH").apply {
            init(senderKeys.private)
            doPhase(receiverPublicKey.public, true)
        }.generateSecret()
        val salt = MessageDigest.getInstance("SHA-256").digest(
            "kemi-credential-relay-v1|$sessionId".toByteArray(StandardCharsets.UTF_8),
        )
        val extractedKey = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(salt, "HmacSHA256"))
        }.doFinal(sharedSecret)
        val encryptionKey = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(extractedKey, "HmacSHA256"))
            update("KEMI credential transfer v1".toByteArray(StandardCharsets.UTF_8))
            update(1.toByte())
        }.doFinal()
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val ciphertext = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(encryptionKey, "AES"),
                GCMParameterSpec(128, nonce),
            )
            updateAAD("v1|$sessionId".toByteArray(StandardCharsets.UTF_8))
            doFinal(plaintext)
        }
        sharedSecret.fill(0)
        extractedKey.fill(0)
        encryptionKey.fill(0)

        return Envelope(
            senderPublicKey = CredentialTransferCrypto.encodeBase64Url(senderKeys.public.encoded),
            nonce = CredentialTransferCrypto.encodeBase64Url(nonce),
            ciphertext = CredentialTransferCrypto.encodeBase64Url(ciphertext),
        )
    }

    private data class Envelope(
        val senderPublicKey: String,
        val nonce: String,
        val ciphertext: String,
    )
}
