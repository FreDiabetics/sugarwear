package app.sugarlicious.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class LocalNetworkAccessPolicyTest {
    @Test fun `android 17 requests access only for enabled private network destinations`() {
        assertTrue(LocalNetworkAccessPolicy.needsPermission(37, true, "http://192.168.178.2:1337", false))
        assertTrue(LocalNetworkAccessPolicy.needsPermission(37, true, "http://10.0.0.2", false))
        assertTrue(LocalNetworkAccessPolicy.needsPermission(37, true, "http://nightscout.local", false))
        assertTrue(LocalNetworkAccessPolicy.needsPermission(37, true, "http://[fd00::1]", false))
    }

    @Test fun `public destinations and older android do not request local network access`() {
        assertFalse(LocalNetworkAccessPolicy.needsPermission(37, true, "https://example.com", false))
        assertFalse(LocalNetworkAccessPolicy.needsPermission(37, true, "https://fda.gov", false))
        assertFalse(LocalNetworkAccessPolicy.needsPermission(37, true, "https://192.foo.168.1.2", false))
        assertFalse(LocalNetworkAccessPolicy.needsPermission(36, true, "http://192.168.1.2", false))
    }

    @Test fun `hostname resolving to a private address requests local network access`() {
        val privateAddress = InetAddress.getByAddress("nightscout.example", byteArrayOf(192.toByte(), 168.toByte(), 1, 20))

        assertTrue(
            LocalNetworkAccessPolicy.needsPermission(
                sdkInt = 37,
                enabled = true,
                baseUrl = "https://nightscout.example",
                permissionGranted = false,
                resolvedAddresses = listOf(privateAddress),
            ),
        )
    }

    @Test fun `permission check blocks initial sync until local destination check completes`() {
        assertTrue(
            LocalNetworkAccessPolicy.shouldDeferSyncWhileChecking(
                sdkInt = 37,
                enabled = true,
                permissionGranted = false,
            ),
        )
        assertFalse(LocalNetworkAccessPolicy.shouldDeferSyncWhileChecking(36, true, false))
        assertFalse(LocalNetworkAccessPolicy.shouldDeferSyncWhileChecking(37, false, false))
        assertFalse(LocalNetworkAccessPolicy.shouldDeferSyncWhileChecking(37, true, true))
    }

    @Test fun `disabled integration and granted permission do not request again`() {
        assertFalse(LocalNetworkAccessPolicy.needsPermission(37, false, "http://192.168.1.2", false))
        assertFalse(LocalNetworkAccessPolicy.needsPermission(37, true, "http://192.168.1.2", true))
    }

    @Test fun `malformed destinations never trigger a permission prompt`() {
        assertFalse(LocalNetworkAccessPolicy.needsPermission(37, true, "not a url", false))
        assertFalse(LocalNetworkAccessPolicy.needsPermission(37, true, "", false))
    }
}
