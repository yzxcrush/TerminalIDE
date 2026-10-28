package com.terminalide.capture

import android.content.Context
import android.util.Base64
import com.terminalide.R
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory

/** 把抓包 CA 注入系统证书库（通过 root，Android 14+ 走 APEX）。 */
object CaInstaller {

    /** 返回脚本输出（含 CA_OK 表示成功）。 */
    fun install(ctx: Context, ca: CaManager): String {
        val der = ca.caDer() ?: return "CA_NOT_READY"
        val hash = subjectHash(der)
        val pem = buildPem(der)
        val pemFile = File(ctx.cacheDir, "ca_$hash.pem").apply { writeText(pem) }

        val tmpl = ctx.resources.openRawResource(R.raw.ca_inject)
            .bufferedReader().use { it.readText() }
        val script = tmpl
            .replace("__HASH__", hash)
            .replace("__PEM__", pemFile.absolutePath)
        val scriptFile = File(ctx.cacheDir, "ca_inject_run.sh").apply { writeText(script) }

        return runCatching {
            val p = ProcessBuilder("su", "-c", "sh ${scriptFile.absolutePath}").start()
            val out = p.inputStream.bufferedReader().readText()
            val err = p.errorStream.bufferedReader().readText()
            p.waitFor()
            (out + err).trim()
        }.getOrElse { "EXEC_FAIL: ${it.message}" }
    }

    /** 是否已安装（检查证书文件是否存在于 APEX）。 */
    fun isInstalled(ctx: Context, ca: CaManager): Boolean {
        val der = ca.caDer() ?: return false
        val hash = subjectHash(der)
        return runCatching {
            File("/apex/com.android.conscrypt/cacerts/$hash.0").exists()
        }.getOrDefault(false)
    }

    private fun buildPem(der: ByteArray): String = buildString {
        append("-----BEGIN CERTIFICATE-----\n")
        append(Base64.encodeToString(der, Base64.NO_WRAP).chunked(64).joinToString("\n"))
        append("\n-----END CERTIFICATE-----\n")
    }

    private fun subjectHash(der: ByteArray): String = runCatching {
        val cf = CertificateFactory.getInstance("X.509")
        val cert = cf.generateCertificate(java.io.ByteArrayInputStream(der)) as java.security.cert.X509Certificate
        val d = MessageDigest.getInstance("SHA-1").digest(cert.subjectX500Principal.encoded)
        val h = (d[0].toLong() and 0xFF) or
            ((d[1].toLong() and 0xFF) shl 8) or
            ((d[2].toLong() and 0xFF) shl 16) or
            ((d[3].toLong() and 0xFF) shl 24)
        String.format("%08x", h)
    }.getOrDefault("00000000")
}
