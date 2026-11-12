package app.sugarwear

/** Supplies an existing session at the Keystore boundary, unavailable on the host JVM. */
@org.robolectric.annotation.Implements(CgmCredentialStore::class, isInAndroidSdk = false)
internal class PausedOverviewCredentialShadow {
    @org.robolectric.annotation.Implementation
    fun read(): StoredCgmCredentials = StoredCgmCredentials("1234", null, null, app.sugarlicious.sensor.protocol.CgmDefaultGKey.parts, null, null)
}
