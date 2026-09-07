package app.k9mail.feature.account.setup.ui.credential

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class LocalCredentialTransferRepository(
    private val addressProvider: () -> Inet4Address? = ::findPrivateIpv4Address,
    private val serverSocketFactory: (Inet4Address) -> ServerSocket = ::createServerSocket,
    private val clock: () -> Long = System::currentTimeMillis,
) : RemoteCredentialTransferContract.Repository {
    private val sessionLock = Any()

    @Volatile
    private var localSession: LocalSession? = null

    override val isAvailable: Boolean
        get() = runCatching { addressProvider() != null }.getOrDefault(false)

    override suspend fun start(emailAddress: String): RemoteCredentialTransferContract.StartResult {
        return withContext(Dispatchers.IO) {
            closeSession()
            val address = addressProvider()
                ?: return@withContext RemoteCredentialTransferContract.StartResult.Unavailable

            runCatching {
                val serverSocket = serverSocketFactory(address)
                val sessionId = CredentialTransferCrypto.randomBase64Url(SESSION_ID_BYTES)
                val writeToken = CredentialTransferCrypto.randomBase64Url(TOKEN_BYTES)
                val expiresAt = clock() + SESSION_TTL_MS
                val session = LocalSession(
                    id = sessionId,
                    writeTokenHash = tokenHash(writeToken),
                    serverSocket = serverSocket,
                    expiresAt = expiresAt,
                )
                synchronized(sessionLock) {
                    localSession = session
                }

                val maskedAddress = maskEmailAddress(emailAddress)
                val accountLabel = CredentialTransferCrypto.encodeBase64Url(
                    maskedAddress.toByteArray(StandardCharsets.UTF_8),
                )
                val qrCodeUrl = "http://${address.hostAddress}:${serverSocket.localPort}$ASSIST_PATH" +
                    "#s=$sessionId&w=$writeToken&a=$accountLabel"

                RemoteCredentialTransferContract.StartResult.Success(
                    RemoteCredentialTransferContract.Session(
                        id = sessionId,
                        qrCodeUrl = qrCodeUrl,
                        verificationCode = "",
                        expiresAt = expiresAt,
                    ),
                )
            }.getOrElse {
                closeSession()
                RemoteCredentialTransferContract.StartResult.Failure
            }
        }
    }

    override suspend fun poll(sessionId: String): RemoteCredentialTransferContract.PollResult {
        val session = synchronized(sessionLock) {
            localSession?.takeIf { it.id == sessionId }
        }
        val result = when {
            session == null -> RemoteCredentialTransferContract.PollResult.Expired
            session.expiresAt <= clock() -> {
                closeSession(session)
                RemoteCredentialTransferContract.PollResult.Expired
            }

            else -> pollActiveSession(session)
        }
        return result
    }

    override suspend fun cancel(sessionId: String) {
        val session = synchronized(sessionLock) {
            localSession?.takeIf { it.id == sessionId }
        } ?: return
        closeSession(session)
    }

    private fun handleRequest(socket: Socket, session: LocalSession): RequestResult {
        socket.soTimeout = SOCKET_TIMEOUT_MS
        val output = socket.getOutputStream()
        val request = try {
            readRequest(socket.getInputStream())
        } catch (_: RequestTooLargeException) {
            writeResponse(output, HTTP_PAYLOAD_TOO_LARGE)
            null
        } catch (_: IllegalArgumentException) {
            writeResponse(output, HTTP_BAD_REQUEST)
            null
        } catch (_: IOException) {
            writeResponse(output, HTTP_BAD_REQUEST)
            null
        }

        val result = when {
            request == null -> RequestResult.Pending
            request.method == "GET" && request.path == ASSIST_PATH -> {
                writeResponse(
                    output = output,
                    status = HTTP_OK,
                    contentType = "text/html; charset=utf-8",
                    body = ASSIST_PAGE,
                    extraHeaders = SECURITY_HEADERS,
                )
                RequestResult.Pending
            }

            request.method == "POST" && request.path == "$SESSION_PATH_PREFIX/${session.id}/credential" -> {
                handleCredentialSubmission(request, output, session)
            }

            else -> {
                writeResponse(output, HTTP_NOT_FOUND)
                RequestResult.Pending
            }
        }
        return result
    }

    private fun handleCredentialSubmission(
        request: HttpRequest,
        output: OutputStream,
        session: LocalSession,
    ): RequestResult {
        val submittedToken = request.headers[WRITE_TOKEN_HEADER]
        val result = when {
            submittedToken == null || !tokenMatches(submittedToken, session.writeTokenHash) -> {
                writeResponse(output, HTTP_UNAUTHORIZED)
                RequestResult.Pending
            }

            request.headers[CONTENT_TYPE_HEADER]?.startsWith(JSON_CONTENT_TYPE) != true -> {
                writeResponse(output, HTTP_UNSUPPORTED_MEDIA_TYPE)
                RequestResult.Invalid
            }

            else -> receiveCredential(request, output)
        }
        return result
    }

    private fun receiveCredential(request: HttpRequest, output: OutputStream): RequestResult {
        val credential = runCatching {
            val body = JSONObject(String(request.body, StandardCharsets.UTF_8))
            require(body.getInt("version") == PROTOCOL_VERSION)
            body.getString("authorizationCode")
        }.getOrNull()
        return if (credential.isNullOrBlank() || credential.length > MAX_CREDENTIAL_LENGTH) {
            writeResponse(output, HTTP_BAD_REQUEST)
            RequestResult.Invalid
        } else {
            writeResponse(output, HTTP_NO_CONTENT)
            RequestResult.Received(credential)
        }
    }

    private fun readRequest(input: InputStream): HttpRequest {
        val requestLine = requireNotNull(readLimitedLine(input, MAX_REQUEST_LINE_BYTES)) {
            "missing_request_line"
        }
        val requestParts = requestLine.split(' ')
        require(requestParts.size == REQUEST_PART_COUNT)
        val (method, requestTarget, httpVersion) = requestParts
        require(httpVersion == "HTTP/1.1")
        val path = requestTarget.substringBefore('?')
        require(method == "GET" || method == "POST")
        require(path.startsWith('/') && path.length <= MAX_REQUEST_TARGET_LENGTH)

        val headers = mutableMapOf<String, String>()
        repeat(MAX_HEADER_COUNT) {
            val line = requireNotNull(readLimitedLine(input, MAX_HEADER_LINE_BYTES)) {
                "incomplete_headers"
            }
            if (line.isEmpty()) {
                val contentLength = headers[CONTENT_LENGTH_HEADER]?.toIntOrNull() ?: 0
                if (contentLength !in 0..MAX_BODY_BYTES) throw RequestTooLargeException()
                val body = input.readExactly(contentLength)
                return HttpRequest(method, path, headers, body)
            }
            val separator = line.indexOf(':')
            require(separator > 0)
            val name = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            require(name.matches(HEADER_NAME_PATTERN))
            require(name !in headers)
            headers[name] = value
        }
        throw RequestTooLargeException()
    }

    private suspend fun pollActiveSession(
        session: LocalSession,
    ): RemoteCredentialTransferContract.PollResult = withContext(Dispatchers.IO) {
        try {
            session.serverSocket.accept().use { socket ->
                when (val result = handleRequest(socket, session)) {
                    RequestResult.Pending -> RemoteCredentialTransferContract.PollResult.Pending
                    RequestResult.Invalid -> {
                        closeSession(session)
                        RemoteCredentialTransferContract.PollResult.InvalidPayload
                    }

                    is RequestResult.Received -> {
                        closeSession(session)
                        RemoteCredentialTransferContract.PollResult.Received(result.credential)
                    }
                }
            }
        } catch (_: SocketTimeoutException) {
            RemoteCredentialTransferContract.PollResult.Pending
        } catch (_: IOException) {
            if (localSession === session) {
                RemoteCredentialTransferContract.PollResult.Pending
            } else {
                RemoteCredentialTransferContract.PollResult.Expired
            }
        }
    }

    private fun writeResponse(
        output: OutputStream,
        status: Int,
        contentType: String? = null,
        body: ByteArray = ByteArray(0),
        extraHeaders: Map<String, String> = emptyMap(),
    ) {
        val reason = when (status) {
            HTTP_OK -> "OK"
            HTTP_NO_CONTENT -> "No Content"
            HTTP_BAD_REQUEST -> "Bad Request"
            HTTP_UNAUTHORIZED -> "Unauthorized"
            HTTP_NOT_FOUND -> "Not Found"
            HTTP_PAYLOAD_TOO_LARGE -> "Payload Too Large"
            HTTP_UNSUPPORTED_MEDIA_TYPE -> "Unsupported Media Type"
            else -> "Error"
        }
        val headers = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Connection: close\r\n")
            append("Content-Length: ${body.size}\r\n")
            if (contentType != null) append("Content-Type: $contentType\r\n")
            extraHeaders.forEach { (name, value) -> append("$name: $value\r\n") }
            append("\r\n")
        }
        output.write(headers.toByteArray(StandardCharsets.US_ASCII))
        output.write(body)
        output.flush()
    }

    private fun closeSession(expectedSession: LocalSession? = null) {
        val sessionToClose = synchronized(sessionLock) {
            val currentSession = localSession
            if (expectedSession != null && currentSession !== expectedSession) return
            localSession = null
            currentSession
        }
        sessionToClose?.writeTokenHash?.fill(0)
        runCatching { sessionToClose?.serverSocket?.close() }
    }

    private data class LocalSession(
        val id: String,
        val writeTokenHash: ByteArray,
        val serverSocket: ServerSocket,
        val expiresAt: Long,
    )

    private data class HttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: ByteArray,
    )

    private sealed interface RequestResult {
        data object Pending : RequestResult
        data object Invalid : RequestResult
        data class Received(val credential: String) : RequestResult
    }

    private class RequestTooLargeException : Exception()

    private companion object {
        const val ASSIST_PATH = "/kemi-assist/assist"
        const val SESSION_PATH_PREFIX = "/kemi-assist/v1/sessions"
        const val WRITE_TOKEN_HEADER = "x-kemi-write-token"
        const val CONTENT_TYPE_HEADER = "content-type"
        const val CONTENT_LENGTH_HEADER = "content-length"
        const val JSON_CONTENT_TYPE = "application/json"
        const val PROTOCOL_VERSION = 1
        const val SESSION_ID_BYTES = 24
        const val TOKEN_BYTES = 32
        const val SESSION_TTL_MS = 5 * 60 * 1_000L
        const val SOCKET_TIMEOUT_MS = 5_000
        const val MAX_CREDENTIAL_LENGTH = 4_096
        const val MAX_BODY_BYTES = 8 * 1_024
        const val MAX_REQUEST_LINE_BYTES = 2_048
        const val REQUEST_PART_COUNT = 3
        const val MAX_REQUEST_TARGET_LENGTH = 1_024
        const val MAX_HEADER_LINE_BYTES = 4_096
        const val MAX_HEADER_COUNT = 32
        const val HTTP_OK = 200
        const val HTTP_NO_CONTENT = 204
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
        const val HTTP_PAYLOAD_TOO_LARGE = 413
        const val HTTP_UNSUPPORTED_MEDIA_TYPE = 415
        val HEADER_NAME_PATTERN = Regex("[a-z0-9-]+")
        val SECURITY_HEADERS = mapOf(
            "Cache-Control" to "no-store",
            "Content-Security-Policy" to "default-src 'none'; script-src 'unsafe-inline'; " +
                "style-src 'unsafe-inline'; connect-src 'self'; form-action 'none'; base-uri 'none'; " +
                "frame-ancestors 'none'",
            "Permissions-Policy" to "camera=(), microphone=(), geolocation=(), payment=(), usb=()",
            "Referrer-Policy" to "no-referrer",
            "X-Content-Type-Options" to "nosniff",
            "X-Frame-Options" to "DENY",
        )
        val ASSIST_PAGE = """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
              <meta name="color-scheme" content="light dark">
              <title>KEMI 邮箱手机辅助输入</title>
              <style>
                :root{font-family:system-ui,sans-serif;color-scheme:light dark}
                body{margin:0;padding:24px;background:#f3f5f7;color:#1f2937}
                main{max-width:480px;margin:24px auto;padding:24px;border-radius:18px;background:#fff;box-shadow:0 8px 32px #0002}
                h1{font-size:24px;margin:0 0 12px}.warning{padding:12px;border-radius:10px;background:#fff3cd;color:#664d03}
                label{display:block;margin:20px 0 8px;font-weight:600}input,button{box-sizing:border-box;width:100%;font:inherit}
                input{padding:12px;border:1px solid #9ca3af;border-radius:8px}button{margin-top:14px;padding:12px;border:0;border-radius:8px;background:#1769aa;color:#fff;font-weight:700}
                button:disabled,input:disabled{opacity:.6}.status{min-height:24px}.success{color:#087830}.error{color:#b42318}
                @media(prefers-color-scheme:dark){body{background:#111827;color:#f9fafb}main{background:#1f2937}.warning{background:#4b3d16;color:#ffe69c}}
              </style>
            </head>
            <body>
              <main>
                <h1>手机辅助输入授权码</h1>
                <p id="account" hidden></p>
                <p class="warning">授权码将通过未加密的局域网 HTTP 连接发送。请只在可信 Wi-Fi 中使用，不要输入邮箱主密码。</p>
                <form id="form" autocomplete="off">
                  <label for="credential">邮箱专用授权码</label>
                  <input id="credential" type="password" required maxlength="4096" autocapitalize="none" autocomplete="off" spellcheck="false">
                  <button id="submit" type="submit">直接发送到平板</button>
                </form>
                <p id="status" class="status" role="status" aria-live="polite"></p>
              </main>
              <script>
                "use strict";
                const params=new URLSearchParams(location.hash.slice(1));
                const sessionId=params.get("s")||"";
                const writeToken=params.get("w")||"";
                const accountLabel=params.get("a")||"";
                history.replaceState(null,"",location.pathname);
                const form=document.getElementById("form");
                const input=document.getElementById("credential");
                const submit=document.getElementById("submit");
                const status=document.getElementById("status");
                const account=document.getElementById("account");
                function setStatus(message,kind){status.textContent=message;status.className="status "+(kind||"");}
                function decode(value){const normalized=value.replaceAll("-","+").replaceAll("_","/");const padding="=".repeat((4-normalized.length%4)%4);return decodeURIComponent(escape(atob(normalized+padding)));}
                if(accountLabel){try{account.textContent="当前账号："+decode(accountLabel);account.hidden=false;}catch(error){account.hidden=true;}}
                if(!/^[A-Za-z0-9_-]{32,64}$/.test(sessionId)||!/^[A-Za-z0-9_-]{32,128}$/.test(writeToken)){
                  form.hidden=true;setStatus("二维码无效或已经过期，请返回平板重新生成。","error");
                }else{input.focus();}
                form.addEventListener("submit",async function(event){
                  event.preventDefault();
                  if(!input.value)return;
                  submit.disabled=true;input.disabled=true;setStatus("正在通过局域网发送…");
                  try{
                    const response=await fetch("/kemi-assist/v1/sessions/"+encodeURIComponent(sessionId)+"/credential",{
                      method:"POST",headers:{"Content-Type":"application/json","X-KEMI-Write-Token":writeToken},
                      cache:"no-store",credentials:"omit",referrerPolicy:"no-referrer",
                      body:JSON.stringify({version:1,authorizationCode:input.value})
                    });
                    input.value="";
                    if(!response.ok)throw new Error("submit_failed");
                    form.hidden=true;setStatus("已直接发送，请回到平板继续登录。","success");
                  }catch(error){submit.disabled=false;input.disabled=false;setStatus("发送失败，请确认两台设备位于同一个局域网，或在平板重新生成二维码。","error");}
                });
              </script>
            </body>
            </html>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
    }
}

private fun findPrivateIpv4Address(): Inet4Address? {
    return Collections.list(NetworkInterface.getNetworkInterfaces())
        .filter { networkInterface ->
            runCatching { networkInterface.isUp && !networkInterface.isLoopback }.getOrDefault(false)
        }
        .sortedBy(::interfacePriority)
        .flatMap { Collections.list(it.inetAddresses) }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }
}

private fun interfacePriority(networkInterface: NetworkInterface): Int {
    val name = networkInterface.name.lowercase()
    return when {
        name.startsWith("wlan") || name.startsWith("wifi") -> 0
        name.startsWith("eth") -> 1
        else -> 2
    }
}

private fun createServerSocket(address: Inet4Address): ServerSocket {
    return ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(address, SERVER_BIND_PORT), SERVER_BACKLOG)
        soTimeout = SERVER_ACCEPT_TIMEOUT_MS
    }
}

private fun tokenHash(token: String): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(token.toByteArray(StandardCharsets.UTF_8))
}

private fun tokenMatches(token: String, expectedHash: ByteArray): Boolean {
    return MessageDigest.isEqual(tokenHash(token), expectedHash)
}

private fun maskEmailAddress(emailAddress: String): String {
    val separator = emailAddress.indexOf('@')
    if (separator <= 0) return "***"
    val localPart = emailAddress.substring(0, separator)
    return "${localPart.take(2)}***${emailAddress.substring(separator)}"
}

private fun readLimitedLine(input: InputStream, maxBytes: Int): String? {
    val output = ByteArrayOutputStream()
    while (output.size() <= maxBytes) {
        val value = input.read()
        if (value == -1) {
            require(output.size() == 0) { "incomplete_line" }
            return null
        }
        if (value == '\n'.code) {
            val bytes = output.toByteArray()
            val length = if (bytes.lastOrNull() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
            return String(bytes, 0, length, StandardCharsets.US_ASCII)
        }
        output.write(value)
    }
    throw IllegalArgumentException("line_too_long")
}

private fun InputStream.readExactly(length: Int): ByteArray {
    val result = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val count = read(result, offset, length - offset)
        require(count != -1) { "incomplete_body" }
        offset += count
    }
    return result
}

private const val SERVER_BIND_PORT = 0
private const val SERVER_BACKLOG = 4
private const val SERVER_ACCEPT_TIMEOUT_MS = 250
