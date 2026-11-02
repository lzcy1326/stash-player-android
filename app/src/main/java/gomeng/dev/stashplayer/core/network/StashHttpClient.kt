package gomeng.dev.stashplayer.core.network

import java.net.InetAddress
import java.net.Inet6Address
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient

/**
 * Shared connection pool for Stash GraphQL, media metadata, and thumbnail requests.
 *
 * Routes and screens create short-lived API clients frequently. Reusing one
 * client keeps DNS and TLS connections warm across those screen transitions.
 */
object StashHttpClient {
    val dns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            preferStashIpv6(Dns.SYSTEM.lookup(hostname))
    }

    val shared: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(dns)
            .connectTimeout(5, TimeUnit.SECONDS)
            .build()
    }
}

internal fun preferStashIpv6(addresses: List<InetAddress>): List<InetAddress> =
    addresses.sortedBy { if (it is Inet6Address) 0 else 1 }
