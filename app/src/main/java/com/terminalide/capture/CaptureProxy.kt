package com.terminalide.capture

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * 抓包代理：监听本地端口，支持
 *  - 普通 HTTP 代理（绝对 URI）
 *  - HTTPS CONNECT 隧道 + TLS 中间人解密（需要客户端信任 [CaManager] 的 CA）
 *
 * 抓到后写入 [CaptureStore]，供 UI 展示 / AI 分析 / 重放。
 */
class CaptureProxy(
    private val ca: CaManager,
    private val port: Int = 8888,
) {
    @Volatile
    private var running = false
    private var server: ServerSocket? = null

    fun start(): Boolean {
        if (running) return true
        return runCatching {
            server = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress("127.0.0.1", port))
            }
            running = true
            Thread {
                while (running) {
                    val client = runCatching { server?.accept() }.getOrNull() ?: break
                    Thread { runCatching { handle(client) } }.apply { isDaemon = true }.start()
                }
            }.apply { isDaemon = true }.start()
            true
        }.getOrDefault(false)
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        server = null
    }

    fun isRunning(): Boolean = running

    // ---------------- 连接处理 ----------------

    private fun handle(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 20_000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            val firstLine = readLine(input) ?: return
            val parts = firstLine.split(" ")
            if (parts.size < 3) return

            if (parts[0].equals("CONNECT", true)) {
                handleConnect(socket, input, output, parts[1])
            } else {
                // 普通 HTTP 代理：请求行是绝对 URI
                val target = parts[1]
                val host = target.substringAfter("://").substringBefore('/').substringBefore(':')
                val port = if (target.startsWith("https://")) 443 else 80
                val path = target.substringAfter("://").let { afterHost ->
                    val idx = afterHost.indexOf('/')
                    if (idx >= 0) afterHost.substring(idx) else "/"
                }
                relay(firstLine, path, input, output, host, port, "http")
            }
        }
    }

    private fun handleConnect(client: Socket, clientIn: InputStream, output: OutputStream, target: String) {
        val host = target.substringBeforeLast(':')
        val port = target.substringAfterLast(':').toIntOrNull() ?: 443
        output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
        output.flush()

        // 黑名单 / 非白名单 → 只做透明转发，不解密、不记录
        if (!CaptureFilter.shouldCapture(host)) {
            tunnel(clientIn, output, host, port)
            return
        }

        val sslContext = ca.serverContext(host)
        val tls = sslContext.socketFactory.createSocket(client, host, port, false) as SSLSocket
        tls.useClientMode = false
        runCatching { tls.startHandshake() }

        val tlsIn = BufferedInputStream(tls.inputStream)
        val tlsOut = BufferedOutputStream(tls.outputStream)
        val requestLine = readLine(tlsIn) ?: return
        val path = requestLine.split(" ").getOrNull(1) ?: "/"
        relay(requestLine, path, tlsIn, tlsOut, host, port, "https")
    }

    // ---------------- 转发 + 记录 ----------------

    private fun relay(
        requestLine: String,
        path: String,
        clientIn: InputStream,
        clientOut: OutputStream,
        host: String,
        port: Int,
        scheme: String,
    ) {
        val method = requestLine.substringBefore(' ')

        // 1) 读请求头
        val reqHead = StringBuilder()
        var contentLength = -1L
        var chunked = false
        while (true) {
            val line = readLine(clientIn) ?: return
            reqHead.append(line).append("\r\n")
            if (line.isEmpty()) break
            val lower = line.lowercase()
            if (lower.startsWith("content-length:")) {
                contentLength = line.substringAfter(':').trim().toLongOrNull() ?: -1
            }
            if (lower.startsWith("transfer-encoding:") && lower.contains("chunked")) chunked = true
        }

        // 2) 读请求体
        val reqBody: ByteArray = when {
            chunked -> readChunkedRaw(clientIn)
            contentLength > 0 -> readN(clientIn, contentLength.toInt().coerceAtMost(4 * 1024 * 1024))
            else -> ByteArray(0)
        }

        val entryId = CaptureStore.nextId()
        val startTime = System.currentTimeMillis()

        // 3) 连接真实服务器
        val server = try {
            val raw = Socket()
            raw.soTimeout = 20_000
            raw.connect(InetSocketAddress(host, port), 10_000)
            if (scheme == "https") {
                // 注意：SSLSocketFactory.getDefault() 的返回类型是 SocketFactory，必须强转
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                (factory.createSocket(raw, host, port, true) as SSLSocket).apply {
                    startHandshake()
                }
            } else raw
        } catch (t: Throwable) {
            recordError(entryId, startTime, method, "$scheme://$host$path", host, scheme, reqHead, reqBody, t)
            return
        }

        server.use { srv ->
            // 4) 转发请求
            val serverOut = BufferedOutputStream(srv.getOutputStream())
            serverOut.write((requestLine + "\r\n").toByteArray())
            serverOut.write(reqHead.toString().toByteArray())
            if (reqBody.isNotEmpty()) serverOut.write(reqBody)
            serverOut.flush()

            // 5) 读响应头
            val serverIn = BufferedInputStream(srv.getInputStream())
            val respHead = StringBuilder()
            while (true) {
                val line = readLine(serverIn) ?: break
                respHead.append(line).append("\r\n")
                if (line.isEmpty()) break
            }
            val headText = respHead.toString()
            val status = headText.lineSequence().firstOrNull()
                ?.split(" ")?.getOrNull(1)?.toIntOrNull() ?: 0
            val respCl = headText.lineSequence()
                .firstOrNull { it.lowercase().startsWith("content-length:") }
                ?.substringAfter(':')?.trim()?.toLongOrNull() ?: -1
            val respChunked = headText.lineSequence()
                .firstOrNull { it.lowercase().startsWith("transfer-encoding:") }
                ?.lowercase()?.contains("chunked") == true

            // 6) 回传响应头
            clientOut.write(headText.toByteArray())
            clientOut.flush()

            // 7) 回传响应体 + 记录（最多记 256KB）
            val recorded = ByteArrayOutputStream()
            try {
                when {
                    respChunked -> copyChunked(serverIn, clientOut, recorded)
                    respCl >= 0 -> copyN(serverIn, clientOut, recorded, respCl)
                    else -> copyUntilEof(serverIn, clientOut, recorded)
                }
                clientOut.flush()
            } catch (_: Throwable) {
                // 连接中断：已转发的部分保留
            }

            // 8) 记录
            CaptureStore.add(
                CaptureEntry(
                    id = entryId,
                    time = startTime,
                    method = method,
                    url = "$scheme://$host$path",
                    host = host,
                    scheme = scheme,
                    requestHeaders = reqHead.toString().trim(),
                    requestBody = reqBody.toString(Charsets.UTF_8),
                    statusCode = status,
                    responseHeaders = headText.trim(),
                    responseBody = recorded.toString(Charsets.UTF_8.name()),
                )
            )
        }
    }

    private fun recordError(
        id: Long, time: Long, method: String, url: String, host: String, scheme: String,
        reqHead: StringBuilder, reqBody: ByteArray, err: Throwable,
    ) {
        CaptureStore.add(
            CaptureEntry(
                id = id, time = time, method = method, url = url, host = host, scheme = scheme,
                requestHeaders = reqHead.toString().trim(),
                requestBody = reqBody.toString(Charsets.UTF_8),
                statusCode = 0, responseHeaders = "", responseBody = "",
                error = err.message ?: err.toString(),
            )
        )
    }

    /** 透明隧道：不解密、不记录，纯字节转发（用于黑名单）。 */
    private fun tunnel(clientIn: InputStream, clientOut: OutputStream, host: String, port: Int) {
        val server = try {
            Socket().apply {
                soTimeout = 20_000
                connect(InetSocketAddress(host, port), 10_000)
            }
        } catch (_: Throwable) {
            return
        }
        server.use { srv ->
            val serverIn = srv.getInputStream()
            val serverOut = srv.getOutputStream()
            // 客户端 → 服务端
            val up = Thread {
                runCatching {
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = clientIn.read(buf)
                        if (n < 0) break
                        serverOut.write(buf, 0, n)
                        serverOut.flush()
                    }
                }
                runCatching { srv.close() }
            }.apply { isDaemon = true; start() }
            // 服务端 → 客户端
            runCatching {
                val buf = ByteArray(8192)
                while (true) {
                    val n = serverIn.read(buf)
                    if (n < 0) break
                    clientOut.write(buf, 0, n)
                    clientOut.flush()
                }
            }
            up.interrupt()
        }
    }

    // ---------------- 流工具 ----------------

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        return sb.toString()
    }

    private fun readN(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(buf, off, n - off)
            if (r < 0) break
            off += r
        }
        return if (off == n) buf else buf.copyOf(off)
    }

    private fun readChunkedRaw(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readLine(input) ?: break
            val size = sizeLine.trim().substringBefore(';').toIntOrNull(16) ?: break
            out.write((sizeLine + "\r\n").toByteArray())
            if (size == 0) break
            val data = readN(input, size)
            out.write(data)
            out.write("\r\n".toByteArray())
        }
        return out.toByteArray()
    }

    private fun copyN(input: InputStream, clientOut: OutputStream, rec: ByteArrayOutputStream, total: Long) {
        var remaining = total
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (n < 0) break
            clientOut.write(buf, 0, n)
            clientOut.flush()
            if (rec.size() < MAX_RECORD) rec.write(buf, 0, n)
            remaining -= n
        }
    }

    private fun copyChunked(input: InputStream, clientOut: OutputStream, rec: ByteArrayOutputStream) {
        while (true) {
            val sizeLine = readLine(input) ?: break
            clientOut.write((sizeLine + "\r\n").toByteArray())
            val size = sizeLine.trim().substringBefore(';').toIntOrNull(16) ?: break
            if (size == 0) {
                while (true) {
                    val t = readLine(input) ?: break
                    clientOut.write((t + "\r\n").toByteArray())
                    if (t.isEmpty()) break
                }
                break
            }
            val data = readN(input, size)
            clientOut.write(data)
            clientOut.write("\r\n".toByteArray())
            if (rec.size() < MAX_RECORD) rec.write(data)
            readN(input, 2)
        }
        clientOut.flush()
    }

    private fun copyUntilEof(input: InputStream, clientOut: OutputStream, rec: ByteArrayOutputStream) {
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            clientOut.write(buf, 0, n)
            clientOut.flush()
            if (rec.size() < MAX_RECORD) rec.write(buf, 0, n)
        }
    }

    private companion object {
        const val MAX_RECORD = 256 * 1024
    }
}
