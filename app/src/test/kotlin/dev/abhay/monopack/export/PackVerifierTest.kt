package dev.abhay.monopack.export

import com.google.common.truth.Truth.assertThat
import dev.abhay.monopack.R
import dev.abhay.monopack.iconpack.PackNaming
import org.junit.Test

class PackVerifierTest {
    private val ours = byteArrayOf(1, 2, 3)
    private val theirs = byteArrayOf(9, 9, 9)
    private val pack = PackNaming.packageFor("Monopack")

    @Test
    fun ourPackPasses() {
        assertThat(PackVerifier.problem(pack, listOf(ours), pack, ours)).isNull()
        // A file found in the folder (no recorded package) still has to be a signed Monopack pack.
        assertThat(PackVerifier.problem(pack, listOf(ours), null, ours)).isNull()
    }

    @Test
    fun otherAppsAreRefused() {
        assertThat(PackVerifier.problem("com.evil.app", listOf(ours), null, ours)).isEqualTo(R.string.install_not_a_pack)
        assertThat(PackVerifier.problem(null, emptyList(), pack, ours)).isEqualTo(R.string.install_not_a_pack)
        // A pack, but not the one the user tapped.
        assertThat(PackVerifier.problem(PackNaming.packageFor("Other"), listOf(ours), pack, ours)).isEqualTo(R.string.install_not_a_pack)
    }

    @Test
    fun packsSignedByAnotherKeyAreRefused() {
        assertThat(PackVerifier.problem(pack, listOf(theirs), pack, ours)).isEqualTo(R.string.install_wrong_signer)
        assertThat(PackVerifier.problem(pack, emptyList(), pack, ours)).isEqualTo(R.string.install_wrong_signer)
        assertThat(PackVerifier.problem(pack, listOf(ours), pack, trusted = null)).isEqualTo(R.string.install_wrong_signer)
    }
}
