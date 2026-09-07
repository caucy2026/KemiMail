package net.thunderbird.feature.smartassistant.internal

import java.nio.ByteBuffer
import java.security.MessageDigest
import net.thunderbird.feature.smartassistant.SmartAssistantAccountReference
import net.thunderbird.feature.smartassistant.SmartAssistantContext

internal class SmartAssistantRequestHasher {
    fun cacheKey(
        account: SmartAssistantAccountReference,
        contextHash: String,
        request: MinimizedSmartAssistantRequest,
    ): SmartAssistantCacheKey {
        val requestParts = buildList {
            add(request.capability.name)
            for ((field, text) in request.input.asMap()) {
                add(field.name)
                add(text.reveal())
            }
        }
        return SmartAssistantCacheKey(
            accountHash = hashAccount(account),
            contextHash = contextHash,
            requestHash = hashValues(requestParts),
        )
    }

    fun hashAccount(account: SmartAssistantAccountReference): String {
        return hashValues(listOf(account.accountUuid))
    }

    fun hashContext(context: SmartAssistantContext): String {
        val values = buildList {
            add(context.scene.name)
            add(context.account?.accountUuid.orEmpty())
            add(context.folder?.accountUuid.orEmpty())
            add(context.folder?.folderId?.toString().orEmpty())
            add(context.message?.accountUuid.orEmpty())
            add(context.message?.folderId?.toString().orEmpty())
            add(context.message?.messageUid.orEmpty())
            add(context.draft?.accountUuid.orEmpty())
            add(context.draft?.folderId?.toString().orEmpty())
            add(context.draft?.messageUid.orEmpty())
            context.selection?.messages.orEmpty().forEach { message ->
                add(message.accountUuid)
                add(message.folderId.toString())
                add(message.messageUid)
            }
        }
        return hashValues(values)
    }

    private fun hashValues(values: Iterable<String>): String {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        values.forEach { value -> updateDigest(digest, value) }
        return encodeHex(digest.digest())
    }

    private fun updateDigest(digest: MessageDigest, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
        digest.update(bytes)
    }

    private fun encodeHex(bytes: ByteArray): String = buildString(bytes.size * HEX_CHARACTERS_PER_BYTE) {
        bytes.forEach { byte ->
            val value = byte.toInt() and BYTE_MASK
            append(HEX_ALPHABET[value ushr NIBBLE_BITS])
            append(HEX_ALPHABET[value and LOW_NIBBLE_MASK])
        }
    }

    private companion object {
        const val HASH_ALGORITHM = "SHA-256"
        const val HEX_ALPHABET = "0123456789abcdef"
        const val HEX_CHARACTERS_PER_BYTE = 2
        const val BYTE_MASK = 0xff
        const val NIBBLE_BITS = 4
        const val LOW_NIBBLE_MASK = 0x0f
    }
}
