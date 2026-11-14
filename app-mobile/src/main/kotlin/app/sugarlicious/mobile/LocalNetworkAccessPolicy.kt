package app.sugarlicious.mobile

import java.net.InetAddress
import java.net.URI

internal object LocalNetworkAccessPolicy {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    private const val ANDROID_17_API = 37

    fun needsPermission(
        sdkInt: Int,
        enabled: Boolean,
        baseUrl: String,
        permissionGranted: Boolean,
        resolvedAddresses: List<InetAddress> = emptyList(),
    ): Boolean =
        sdkInt >= ANDROID_17_API &&
            enabled &&
            !permissionGranted &&
            (isLocalDestination(baseUrl) || resolvedAddresses.any(::isLocalAddress))

    fun shouldDeferSyncWhileChecking(
        sdkInt: Int,
        enabled: Boolean,
        permissionGranted: Boolean,
    ): Boolean = sdkInt >= ANDROID_17_API && enabled && !permissionGranted

    fun needsPermissionAfterResolution(
        sdkInt: Int,
        enabled: Boolean,
        baseUrl: String,
        permissionGranted: Boolean,
    ): Boolean {
        if (!shouldDeferSyncWhileChecking(sdkInt, enabled, permissionGranted)) return false
        val host = parseHost(baseUrl) ?: return false
        val addresses = runCatching { InetAddress.getAllByName(host).toList() }.getOrDefault(emptyList())
        return needsPermission(sdkInt, enabled, baseUrl, permissionGranted, addresses)
    }

    internal fun isLocalDestination(baseUrl: String): Boolean {
        val host = parseHost(baseUrl) ?: return false
        if (host == "localhost" || host.endsWith(".local")) return true
        if (':' in host) {
            return runCatching { InetAddress.getByName(host) }
                .map(::isLocalAddress)
                .getOrDefault(false)
        }

        val labels = host.split('.')
        if (labels.size != 4 || labels.any { label -> label.isEmpty() || label.any { !it.isDigit() } }) return false
        val octets = labels.mapNotNull(String::toIntOrNull)
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return octets[0] == 10 ||
            octets[0] == 127 ||
            (octets[0] == 169 && octets[1] == 254) ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
    }

    private fun parseHost(baseUrl: String): String? =
        runCatching { URI(baseUrl.trim()).host?.lowercase()?.removeSurrounding("[", "]") }.getOrNull()

    private fun isLocalAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) {
            return true
        }
        val bytes = address.address
        return bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
    }
}
