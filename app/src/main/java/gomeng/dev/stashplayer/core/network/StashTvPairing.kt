package gomeng.dev.stashplayer.core.network

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val QR_PREFIX = "stashplayer-tv:2:"
private const val MAX_FRAME = 16_384
private const val IV_SIZE = 12
private const val KEY_SIZE = 32
private const val CHALLENGE_SIZE = 16
private const val PEER_TIMEOUT_MS = 4_000
internal const val TV_PAIRING_SECONDS = 180
private val random = SecureRandom()
private val profileJson: JsonAdapter<StashServerProfile> = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory()).build().adapter(StashServerProfile::class.java)

internal class StashTvPairingCode(
    val address: Inet4Address,
    val port: Int,
    private val secret: ByteArray,
) {
    fun encode(): String =
        "$QR_PREFIX${address.hostAddress}:$port:${Base64.getUrlEncoder().withoutPadding().encodeToString(secret)}"

    fun seal(payload: ByteArray, purpose: String, challenge: ByteArray = byteArrayOf()): ByteArray {
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        return iv + cipher(Cipher.ENCRYPT_MODE, iv, purpose, challenge).doFinal(payload)
    }

    fun open(frame: ByteArray, purpose: String, challenge: ByteArray = byteArrayOf()): ByteArray {
        require(frame.size >= IV_SIZE + 16)
        val iv = frame.copyOfRange(0, IV_SIZE)
        return cipher(Cipher.DECRYPT_MODE, iv, purpose, challenge).doFinal(frame, IV_SIZE, frame.size - IV_SIZE)
    }

    private fun cipher(mode: Int, iv: ByteArray, purpose: String, challenge: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, iv))
            updateAAD("$QR_PREFIX$purpose".toByteArray() + challenge)
        }

    companion object {
        fun create(address: Inet4Address, port: Int): StashTvPairingCode =
            StashTvPairingCode(address, port, ByteArray(KEY_SIZE).also(random::nextBytes))

        fun parse(value: String): StashTvPairingCode? {
            if (value.length > 128 || !value.startsWith(QR_PREFIX)) return null
            val fields = value.removePrefix(QR_PREFIX).split(':')
            if (fields.size != 3) return null
            val parts = fields[0].split('.')
            if (parts.size != 4) return null
            val bytes = parts.map { part ->
                val number = part.toIntOrNull() ?: return null
                if (number !in 0..255 || number.toString() != part) return null
                number.toByte()
            }.toByteArray()
            val address = InetAddress.getByAddress(bytes) as Inet4Address
            if (!address.isSiteLocalAddress && !address.isLinkLocalAddress) return null
            val port = fields[1].toIntOrNull()?.takeIf { it in 1024..65535 } ?: return null
            val key = runCatching { Base64.getUrlDecoder().decode(fields[2]) }.getOrNull() ?: return null
            return if (key.size == KEY_SIZE) StashTvPairingCode(address, port, key) else null
        }
    }
}

internal enum class StashTvPairingResult { Saved, Rejected }

/** A single authenticated phone can claim the code while it awaits confirmation. */
internal class StashTvPairingReceiver(address: Inet4Address) : AutoCloseable {
    private val server = ServerSocket(0, 4, address).apply { soTimeout = 1_000 }
    val code = StashTvPairingCode.create(address, server.localPort)
    private val deadline = System.nanoTime() + TV_PAIRING_SECONDS * 1_000_000_000L

    suspend fun receive(
        onClaimChanged: suspend (Boolean) -> Unit = {},
        save: suspend (StashServerProfile) -> StashTvPairingResult,
    ): Boolean = withContext(Dispatchers.IO) {
        while (System.nanoTime() < deadline) {
            ensureActive()
            val peer = try {
                server.accept()
            } catch (_: SocketTimeoutException) {
                continue
            } catch (failure: SocketException) {
                if (server.isClosed) return@withContext false
                throw failure
            }
            peer.use { socket ->
                val challenge = ByteArray(CHALLENGE_SIZE).also(random::nextBytes)
                socket.soTimeout = PEER_TIMEOUT_MS
                var claimed = false
                try {
                    socket.writeFrame(code.seal(challenge, "challenge"))
                    require(code.open(socket.readFrame(), "claim", challenge).isEmpty())
                    val remainingMs = ((deadline - System.nanoTime()) / 1_000_000L).toInt()
                    if (remainingMs <= 0) return@withContext false

                    claimed = true
                    onClaimChanged(true)
                    socket.writeFrame(code.seal(ByteBuffer.allocate(4).putInt(remainingMs).array(), "claimed", challenge))
                    socket.soTimeout = remainingMs
                    val bytes = code.open(socket.readConfirmationFrame(), "profile", challenge)
                    val profile = requireNotNull(profileJson.fromJson(bytes.toString(Charsets.UTF_8)))
                    ensureActive()
                    if (System.nanoTime() >= deadline) return@withContext false

                    val result = save(profile)
                    if (result == StashTvPairingResult.Saved) close()
                    // Once saved, the code is consumed even if the phone cannot receive the result.
                    runCatching {
                        socket.writeFrame(code.seal(byteArrayOf(result.ordinal.toByte()), "result", challenge))
                    }
                    if (result == StashTvPairingResult.Saved) return@withContext true
                } catch (_: IOException) {
                    return@use
                } catch (_: GeneralSecurityException) {
                    return@use
                } catch (_: IllegalArgumentException) {
                    return@use
                } catch (_: com.squareup.moshi.JsonDataException) {
                    return@use
                } finally {
                    if (claimed) onClaimChanged(false)
                }
            }
        }
        false
    }

    override fun close() = server.close()
}

internal suspend fun pairStashProfileWithTv(
    code: StashTvPairingCode,
    createSocket: () -> Socket = ::Socket,
    confirm: suspend (remainingMs: Long) -> StashServerProfile?,
): StashTvPairingResult? = withContext(Dispatchers.IO) {
    createSocket().use { socket ->
        socket.connect(InetSocketAddress(code.address, code.port), PEER_TIMEOUT_MS)
        socket.soTimeout = PEER_TIMEOUT_MS
        val challenge = code.open(socket.readFrame(), "challenge")
        require(challenge.size == CHALLENGE_SIZE)
        socket.writeFrame(code.seal(byteArrayOf(), "claim", challenge))
        val acknowledgment = code.open(socket.readFrame(), "claimed", challenge)
        require(acknowledgment.size == 4)
        val remainingMs = ByteBuffer.wrap(acknowledgment).int
        require(remainingMs in 1..TV_PAIRING_SECONDS * 1_000)
        val profile = confirm(remainingMs.toLong()) ?: return@withContext null

        val payload = profileJson.toJson(profile).toByteArray()
        require(payload.size + IV_SIZE + 16 <= MAX_FRAME)
        socket.writeFrame(code.seal(payload, "profile", challenge))
        socket.soTimeout = 45_000
        val response = code.open(socket.readFrame(), "result", challenge)
        require(response.size == 1)
        StashTvPairingResult.entries.getOrNull(response[0].toInt()) ?: throw IOException("Invalid pairing response")
    }
}

private suspend fun Socket.readConfirmationFrame(): ByteArray = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { runCatching { close() } }
    continuation.resumeWith(runCatching { readFrame() })
}

private fun Socket.readFrame(): ByteArray {
    val input = DataInputStream(getInputStream())
    val size = input.readInt()
    if (size !in 1..MAX_FRAME) throw IOException("Invalid pairing frame size")
    return ByteArray(size).also(input::readFully)
}

private fun Socket.writeFrame(frame: ByteArray) {
    DataOutputStream(getOutputStream()).apply {
        writeInt(frame.size)
        write(frame)
        flush()
    }
}
