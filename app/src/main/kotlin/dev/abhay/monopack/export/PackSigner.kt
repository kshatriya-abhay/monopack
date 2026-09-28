package dev.abhay.monopack.export

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import com.android.apksig.KeyConfig
import dev.abhay.monopack.iconpack.IconPackApk
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.security.auth.x500.X500Principal

/**
 * Signs icon packs with apksig (AOSP, Apache-2.0), v2 + v3.
 *
 * One EC P-256 key per Monopack install, kept in the Android Keystore (never leaves the device),
 * with the self-signed certificate the Keystore creates. Every pack is signed with it, so
 * re-exporting a pack name produces an update of the installed pack.
 */
class PackSigner(private val alias: String = ALIAS) {

    /** Signs [unsigned] into [signed]. */
    fun sign(unsigned: File, signed: File) {
        val (key, cert) = keyAndCertificate()
        val config = ApkSigner.SignerConfig.Builder("Monopack", KeyConfig.Jca(key), listOf(cert)).build()
        ApkSigner.Builder(listOf(config))
            .setInputApk(unsigned)
            .setOutputApk(signed)
            .setMinSdkVersion(IconPackApk.MIN_SDK)
            .setV1SigningEnabled(false)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
    }

    /** The signing certificate (to compare with an installed pack's signer). */
    fun certificate(): X509Certificate = keyAndCertificate().second

    @Synchronized
    private fun keyAndCertificate(): Pair<PrivateKey, X509Certificate> {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(alias)) generate()
        val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
        return entry.privateKey to entry.certificate as X509Certificate
    }

    private fun generate() {
        val now = System.currentTimeMillis()
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setCertificateSubject(X500Principal("CN=Monopack icon pack"))
            .setCertificateSerialNumber(BigInteger.valueOf(now))
            .setCertificateNotBefore(Date(now - DAY_MS))
            .setCertificateNotAfter(Date(now + VALIDITY_YEARS * 365 * DAY_MS))
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).apply { initialize(spec) }.generateKeyPair()
    }

    companion object {
        const val ALIAS = "monopack_pack_signer"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val VALIDITY_YEARS = 30L
    }
}
