package app.yomu.core.common

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM encryption with a key that never leaves the Android Keystore.
 *
 * Every value is prefixed with [PREFIX]; plaintext values written by older versions are returned as is, so a getter
 * never loses data. When the Keystore is unavailable (Robolectric, a broken keystore) [encode] returns the value
 * unchanged instead of failing the settings write.
 */
class SecretCipher(private val alias: String, private val key: SecretKey? = createKey(alias)) {
    fun encode(value: String): String? {
        val k = key ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, k)
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.getEncoder().encodeToString(cipher.iv + encrypted)
        }.getOrNull()
    }

    fun decode(raw: String): String? {
        if (!raw.startsWith(PREFIX)) return raw
        val k = key ?: return null
        return runCatching {
            val bytes = Base64.getDecoder().decode(raw.removePrefix(PREFIX))
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, bytes.copyOfRange(0, IV_BYTES)))
            cipher.doFinal(bytes.copyOfRange(IV_BYTES, bytes.size)).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    companion object {
        const val PREFIX = "enc:v1:"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12

        fun isEncoded(value: String): Boolean = value.startsWith(PREFIX)

        private fun createKey(alias: String): SecretKey? = runCatching {
            val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
            (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build(),
                    )
                }
                .generateKey()
        }.getOrNull()
    }
}
