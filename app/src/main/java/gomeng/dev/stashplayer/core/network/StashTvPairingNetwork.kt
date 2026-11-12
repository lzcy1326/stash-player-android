package gomeng.dev.stashplayer.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address

internal fun Context.stashTvPairingAddress(): Inet4Address? {
    val connectivity = getSystemService(ConnectivityManager::class.java)
    return connectivity.localPairingNetworks()
        .flatMap { connectivity.getLinkProperties(it)?.linkAddresses.orEmpty() }
        .mapNotNull { it.address as? Inet4Address }
        .firstOrNull { it.isSiteLocalAddress || it.isLinkLocalAddress }
}

internal suspend fun Context.pairSavedStashProfileWithTv(
    code: StashTvPairingCode,
    confirm: suspend (remainingMs: Long) -> StashServerProfile?,
): StashTvPairingResult? {
    val connectivity = getSystemService(ConnectivityManager::class.java)
    val localNetwork = connectivity.localPairingNetworks().firstOrNull { network ->
        connectivity.getLinkProperties(network)?.routes.orEmpty().any { route ->
            !route.hasGateway() && route.matches(code.address)
        }
    }
    return pairStashProfileWithTv(
        code,
        createSocket = { localNetwork?.socketFactory?.createSocket() ?: java.net.Socket() },
        confirm = confirm,
    )
}

@Suppress("DEPRECATION")
private fun ConnectivityManager.localPairingNetworks() =
    (listOfNotNull(activeNetwork) + allNetworks).distinct().filter { network ->
        val capabilities = getNetworkCapabilities(network) ?: return@filter false
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
