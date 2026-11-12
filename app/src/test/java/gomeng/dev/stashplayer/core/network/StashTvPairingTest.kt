package gomeng.dev.stashplayer.core.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.security.GeneralSecurityException

class StashTvPairingTest {
    @Test
    fun qrContainsOnlyLocalPairingDetails() {
        val code = StashTvPairingCode.create(address("192.168.1.12"), 42000).encode()
        assertNotNull(StashTvPairingCode.parse(code))
        assertNull(StashTvPairingCode.parse(code.replace("192.168.1.12", "8.8.8.8")))
        assertNull(StashTvPairingCode.parse(code.replace("192.168.1.12", "example.org")))
        assertNull(StashTvPairingCode.parse(code.replace(":42000:", ":80:")))
        assertNull(StashTvPairingCode.parse(code.replace("stashplayer-tv:2:", "stashplayer-tv:1:")))
        assertNull(StashTvPairingCode.parse("https://example.org"))
    }

    @Test
    fun savedProfileRoundTripsAndRejectedSaveCanBeRetried() = runBlocking {
        StashTvPairingReceiver(address("127.0.0.1")).use { receiver ->
            val profile = StashServerProfile(
                name = "家里的 Stash",
                baseUrl = "https://stash.example.org",
                fallbackBaseUrl = "http://192.168.1.12:9999",
                apiKey = "sample-api-key",
                authMode = StashServerAuthMode.ApiKey,
            )
            val received = mutableListOf<StashServerProfile>()
            val listening = async {
                receiver.receive {
                    received += it
                    if (received.size == 1) StashTvPairingResult.Rejected else StashTvPairingResult.Saved
                }
            }
            val wrongKey = StashTvPairingCode.create(receiver.code.address, receiver.code.port)
            assertTrue(runCatching { pairStashProfileWithTv(wrongKey) { profile } }.exceptionOrNull() is GeneralSecurityException)
            assertEquals(StashTvPairingResult.Rejected, pairStashProfileWithTv(receiver.code) { profile })
            assertFalse(listening.isCompleted)
            assertEquals(StashTvPairingResult.Saved, pairStashProfileWithTv(receiver.code) { profile })
            assertTrue(listening.await())
            assertEquals(listOf(profile, profile), received)
            assertTrue(runCatching { pairStashProfileWithTv(receiver.code) { profile } }.exceptionOrNull() is IOException)
        }
    }

    @Test
    fun scanClaimsCodeBeforeSendingAndCancelReopensIt() = runBlocking {
        withTimeout(10_000) {
            StashTvPairingReceiver(address("127.0.0.1")).use { receiver ->
                val states = Channel<Boolean>(Channel.UNLIMITED)
                val saved = CompletableDeferred<StashServerProfile>()
                val profile = StashServerProfile(baseUrl = "https://stash.example.org")
                val listening = async {
                    receiver.receive(onClaimChanged = { states.send(it) }) {
                        saved.complete(it)
                        StashTvPairingResult.Saved
                    }
                }

                val decision = CompletableDeferred<StashServerProfile?>()
                val firstPhone = async { pairStashProfileWithTv(receiver.code) { decision.await() } }
                assertTrue(states.receive())
                assertFalse(saved.isCompleted)
                decision.complete(null)
                assertNull(firstPhone.await())
                assertFalse(states.receive())
                assertFalse(saved.isCompleted)

                val confirmed = CompletableDeferred<StashServerProfile>()
                val secondPhone = async { pairStashProfileWithTv(receiver.code) { confirmed.await() } }
                assertTrue(states.receive())
                assertFalse(saved.isCompleted)
                confirmed.complete(profile)
                assertEquals(StashTvPairingResult.Saved, secondPhone.await())
                assertEquals(profile, saved.await())
                assertTrue(listening.await())
            }
        }
    }

    @Test
    fun capturedProfileCannotBeReplayedWithADifferentChallenge() {
        val code = StashTvPairingCode.create(address("192.168.1.12"), 42000)
        val frame = code.seal("secret".toByteArray(), "profile", byteArrayOf(1))
        assertThrows(GeneralSecurityException::class.java) {
            code.open(frame, "profile", byteArrayOf(2))
        }
    }

    private fun address(ip: String) = InetAddress.getByName(ip) as Inet4Address
}
