package com.aipoka.transfer

import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * The PC server uses a self-signed cert (see server/lib/tlsCert.js), so there's no CA chain
 * to validate against. Trust model is TOFU: during /pair (already gated by the one-time PIN,
 * itself only shown on the PC's screen) we accept whatever cert the server presents and record
 * its SHA-256 fingerprint. Every request after that pins to exactly that fingerprint, so a
 * later MITM swapping certs on the same network gets rejected instead of silently trusted.
 */
object TrustedCert {

    fun sha256Fingerprint(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return digest.joinToString(":") { "%02x".format(it) }
    }

    /** Accepts any server cert but captures its fingerprint for the caller to persist. Use only during pairing. */
    fun capturingSocketFactory(onCaptured: (String) -> Unit): Pair<SSLSocketFactory, X509TrustManager> {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val cert = chain?.firstOrNull() ?: throw CertificateException("No server certificate presented")
                onCaptured(sha256Fingerprint(cert))
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), SecureRandom())
        return sslContext.socketFactory to trustManager
    }

    /** Rejects any cert whose SHA-256 fingerprint doesn't match the pinned one from pairing. */
    fun pinnedSocketFactory(expectedFingerprint: String): Pair<SSLSocketFactory, X509TrustManager> {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val cert = chain?.firstOrNull() ?: throw CertificateException("No server certificate presented")
                if (sha256Fingerprint(cert) != expectedFingerprint) {
                    throw CertificateException("Server certificate does not match pinned fingerprint from pairing")
                }
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), SecureRandom())
        return sslContext.socketFactory to trustManager
    }

    /** Self-signed certs are issued for the PC's LAN IP/hostname already (see tlsCert.js SANs), but
     * hostnames can drift (DHCP re-lease) between pairing and sync — fingerprint pinning above is the
     * real security boundary, so hostname mismatch alone shouldn't break sync. */
    val NO_OP_HOSTNAME_VERIFIER = HostnameVerifier { _, _ -> true }
}
