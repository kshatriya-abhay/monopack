package dev.abhay.monopack.export

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertThrows
import org.junit.Test

class PackSignerTest {
    private val testKey: Pair<PrivateKey, X509Certificate> = KeyStore.getInstance("PKCS12").run {
        PackSignerTest::class.java.getResourceAsStream("/test-pack-key.p12").use { load(it, "spikepass".toCharArray()) }
        getKey("pack", "spikepass".toCharArray()) as PrivateKey to getCertificate("pack") as X509Certificate
    }

    /** A key store whose reads can fail; [generated] counts new keys. */
    private inner class FakeKeys(var stored: Pair<PrivateKey, X509Certificate>?) : SigningKeys {
        var generated = 0
        var readsMissing = false

        override fun load() = if (readsMissing) null else stored

        override fun generate() {
            generated++
            stored = testKey
        }
    }

    private val dir: File = createTempDirectory().toFile()

    @Test
    fun theFirstUseCreatesTheKeyAndRemembersIt() {
        val keys = FakeKeys(stored = null)
        assertThat(PackSigner(dir, keys).certificate()).isEqualTo(testKey.second)
        assertThat(keys.generated).isEqualTo(1)
        assertThat(File(dir, "pack_signer_key_created").exists()).isTrue()
    }

    @Test
    fun anExistingKeyIsUsedAndMarked() {
        val keys = FakeKeys(stored = testKey)
        PackSigner(dir, keys).certificate()
        assertThat(keys.generated).isEqualTo(0)
        assertThat(File(dir, "pack_signer_key_created").exists()).isTrue()
    }

    @Test
    fun aKeyThatReadsAsMissingAfterItWasMadeIsNeverReplaced() {
        val keys = FakeKeys(stored = testKey)
        PackSigner(dir, keys).certificate()
        // Later (a new process), the store has a transient error and reports the key missing.
        keys.readsMissing = true
        assertThrows(IllegalStateException::class.java) { PackSigner(dir, keys).certificate() }
        assertThat(keys.generated).isEqualTo(0)
        // Once the store recovers, the same key is used.
        keys.readsMissing = false
        assertThat(PackSigner(dir, keys).certificate()).isEqualTo(testKey.second)
    }
}
