package app.yomu.data.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator

class TrackerSecretsTest {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun encodeDecodeRoundTrip() {
        val secrets = TrackerSecrets(key())
        val encoded = secrets.encode("secret-access")!!

        assertTrue(encoded.startsWith("enc:v1:"))
        assertEquals("secret-access", secrets.decode(encoded))
    }

    @Test
    fun otherKeyCannotDecode() {
        val encoded = TrackerSecrets(key()).encode("secret-access")!!

        assertNull(TrackerSecrets(key()).decode(encoded))
    }

    @Test
    fun legacyPlaintextIsStillReadable() {
        assertEquals("old-token", TrackerSecrets(key()).decode("old-token"))
    }
}
