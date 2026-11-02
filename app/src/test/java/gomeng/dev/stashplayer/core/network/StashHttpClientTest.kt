package gomeng.dev.stashplayer.core.network

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Test

class StashHttpClientTest {
    @Test
    fun prefersIpv6WhileKeepingIpv4FallbackAndOrder() {
        val ipv4 = InetAddress.getByName("127.0.0.1")
        val ipv6 = InetAddress.getByName("::1")
        assertEquals(listOf(ipv6, ipv4), preferStashIpv6(listOf(ipv4, ipv6)))
        assertEquals(listOf(ipv4), preferStashIpv6(listOf(ipv4)))
    }
}
