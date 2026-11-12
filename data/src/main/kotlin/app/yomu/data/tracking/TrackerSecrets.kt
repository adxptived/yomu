package app.yomu.data.tracking

import app.yomu.core.common.SecretCipher
import javax.crypto.SecretKey

/**
 * Encrypts tracking tokens and client secrets with a key that never leaves the Android Keystore.
 *
 * A token stored by an older version stays readable until the next save re-encrypts it. If the Keystore is
 * unavailable (for example in some Robolectric tests) the store degrades to plaintext instead of losing the session.
 */
internal class TrackerSecrets(private val key: SecretKey? = null) {
    private val cipher = if (key == null) SecretCipher(ALIAS) else SecretCipher(ALIAS, key)

    fun encode(value: String): String? = cipher.encode(value)

    fun decode(raw: String): String? = cipher.decode(raw)

    private companion object {
        const val ALIAS = "yomu_tracker_secrets"
    }
}
