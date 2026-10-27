package com.terminalide.capture

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * 自签 CA + 按域名签发叶子证书，用于 HTTPS 中间人解密。
 *
 * 客户端需要信任本 CA（安装到用户/系统证书），否则 HTTPS 会握手失败。
 */
class CaManager(context: Context) {

    private val dir = File(context.filesDir, "capture").apply { mkdirs() }
    private val caKeyFile = File(dir, "ca.key")
    private val caCertFile = File(dir, "ca.crt")

    private var caKey: PrivateKey? = null
    private var caCert: X509Certificate? = null
    private var caName: X500Name? = null
    private val leafCache = HashMap<String, SSLContext>()

    @Synchronized
    fun ensure() {
        if (caCert != null) return
        if (caCertFile.exists() && caKeyFile.exists()) {
            runCatching {
                val cf = CertificateFactory.getInstance("X.509")
                val cert = cf.generateCertificate(
                    ByteArrayInputStream(caCertFile.readBytes())
                ) as X509Certificate
                val kf = KeyFactory.getInstance("RSA")
                val key = kf.generatePrivate(PKCS8EncodedKeySpec(caKeyFile.readBytes()))
                caCert = cert
                caKey = key
                caName = X500Name(cert.subjectX500Principal.name)
                return
            }
        }
        generateCa()
    }

    private fun generateCa() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        val now = System.currentTimeMillis()
        val name = X500Name("CN=TerminalIDE Capture CA, O=Terminal IDE, C=CN")
        val builder = JcaX509v3CertificateBuilder(
            name,
            BigInteger.valueOf(now),
            Date(now - 86_400_000L),
            Date(now + 3650L * 86_400_000L),
            name,
            kp.public,
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
        val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        caCert = cert
        caKey = kp.private
        caName = name
        caKeyFile.writeBytes(kp.private.encoded)
        caCertFile.writeBytes(cert.encoded)
    }

    /** 导出 CA 证书（DER），供安装到系统/用户证书。 */
    fun caDer(): ByteArray? {
        ensure()
        return caCert?.encoded
    }

    /** 为指定域名生成（或复用）服务端 SSLContext。 */
    @Synchronized
    fun serverContext(host: String): SSLContext {
        ensure()
        leafCache[host]?.let { return it }
        val ctx = buildLeafContext(host)
        leafCache[host] = ctx
        return ctx
    }

    private fun buildLeafContext(host: String): SSLContext {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            caName!!,
            BigInteger.valueOf(now),
            Date(now - 86_400_000L),
            Date(now + 365L * 86_400_000L),
            X500Name("CN=$host"),
            kp.public,
        )
        builder.addExtension(
            Extension.subjectAlternativeName,
            false,
            GeneralNames(arrayOf(GeneralName(GeneralName.dNSName, host))),
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(caKey)
        val leaf = JcaX509CertificateConverter().getCertificate(builder.build(signer))

        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry("leaf", kp.private, charArrayOf(), arrayOf(leaf, caCert))
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, charArrayOf())
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(kmf.keyManagers, null, null)
        return ctx
    }
}
