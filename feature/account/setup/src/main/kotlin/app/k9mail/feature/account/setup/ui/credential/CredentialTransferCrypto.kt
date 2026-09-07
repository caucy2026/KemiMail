package app.k9mail.feature.account.setup.ui.credential

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object CredentialTransferCrypto {
    private val secureRandom = SecureRandom()

    fun generateKeyPair(): KeyPair {
        return KeyPairGenerator.getInstance(KEY_ALGORITHM).apply {
            initialize(ECGenParameterSpec(CURVE_NAME), secureRandom)
        }.generateKeyPair()
    }

    fun randomBase64Url(byteCount: Int): String {
        return encodeBase64Url(ByteArray(byteCount).also(secureRandom::nextBytes))
    }

    fun sha256Base64Url(value: String): String {
        return encodeBase64Url(
            MessageDigest.getInstance(HASH_ALGORITHM).digest(value.toByteArray(StandardCharsets.UTF_8)),
        )
    }

    fun verificationCode(sessionId: String, publicKey: ByteArray, writeToken: String): String {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM).apply {
            update(sessionId.toByteArray(StandardCharsets.UTF_8))
            update(publicKey)
            update(writeToken.toByteArray(StandardCharsets.UTF_8))
        }.digest()
        val number = ((digest[0].toInt() and BYTE_MASK) shl FIRST_BYTE_SHIFT_BITS) or
            ((digest[1].toInt() and BYTE_MASK) shl SECOND_BYTE_SHIFT_BITS) or
            (digest[2].toInt() and BYTE_MASK)
        return (number % VERIFICATION_CODE_MODULUS).toString().padStart(VERIFICATION_CODE_LENGTH, '0')
    }

    fun decrypt(
        sessionId: String,
        receiverKeys: KeyPair,
        senderPublicKey: String,
        nonce: String,
        ciphertext: String,
    ): ByteArray {
        val publicKey = KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(
            X509EncodedKeySpec(decodeBase64Url(senderPublicKey)),
        )
        val agreement = KeyAgreement.getInstance(KEY_AGREEMENT_ALGORITHM).apply {
            init(receiverKeys.private)
            doPhase(publicKey, true)
        }
        val sharedSecret = agreement.generateSecret()
        val encryptionKey = deriveEncryptionKey(sessionId, sharedSecret)
        sharedSecret.fill(0)

        return Cipher.getInstance(CIPHER_TRANSFORMATION).run {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(encryptionKey, AES_ALGORITHM),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, decodeBase64Url(nonce)),
            )
            updateAAD("v1|$sessionId".toByteArray(StandardCharsets.UTF_8))
            doFinal(decodeBase64Url(ciphertext))
        }.also {
            encryptionKey.fill(0)
        }
    }

    fun encodeBase64Url(value: ByteArray): String = Base64.encodeToString(value, BASE64_URL_FLAGS)

    private fun decodeBase64Url(value: String): ByteArray = Base64.decode(value, BASE64_URL_FLAGS)

    private fun deriveEncryptionKey(sessionId: String, sharedSecret: ByteArray): ByteArray {
        val salt = MessageDigest.getInstance(HASH_ALGORITHM).digest(
            "kemi-credential-relay-v1|$sessionId".toByteArray(StandardCharsets.UTF_8),
        )
        val extract = Mac.getInstance(HMAC_ALGORITHM).apply {
            init(SecretKeySpec(salt, HMAC_ALGORITHM))
        }.doFinal(sharedSecret)
        val expand = Mac.getInstance(HMAC_ALGORITHM).apply {
            init(SecretKeySpec(extract, HMAC_ALGORITHM))
        }
        expand.update("KEMI credential transfer v1".toByteArray(StandardCharsets.UTF_8))
        expand.update(1.toByte())
        return expand.doFinal().also {
            salt.fill(0)
            extract.fill(0)
        }
    }

    private const val KEY_ALGORITHM = "EC"
    private const val KEY_AGREEMENT_ALGORITHM = "ECDH"
    private const val CURVE_NAME = "secp256r1"
    private const val HASH_ALGORITHM = "SHA-256"
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val AES_ALGORITHM = "AES"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val VERIFICATION_CODE_LENGTH = 6
    private const val VERIFICATION_CODE_MODULUS = 1_000_000
    private const val BYTE_MASK = 0xff
    private const val FIRST_BYTE_SHIFT_BITS = 16
    private const val SECOND_BYTE_SHIFT_BITS = 8
    private const val BASE64_URL_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
}
