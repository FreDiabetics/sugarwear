package app.sugarwear

import app.sugarlicious.uishared.ProductDataMigrationProvider
import app.sugarlicious.uishared.ProductMigrationLifecycle

class ProductMigrationProvider : ProductDataMigrationProvider() {
    override val legacyPackage = "app.aapswear.g7watch"
    override val targetPackage = "app.sugarwear"

    override fun beforeSnapshot() = ProductMigrationLifecycle.prepareSource(requireNotNull(context))

    override fun afterMigrationAborted() = ProductMigrationLifecycle.restoreSource(requireNotNull(context))

    override fun afterMigrationAccepted() = ProductMigrationLifecycle.retireSource(requireNotNull(context))

    override fun exportSecrets(): Map<String, String> =
        buildMap {
            val ctx = requireNotNull(context)
            val stored = CgmCredentialStore(ctx).read()
            check(stored != null || CgmSensorStateStore(ctx).read().sensor == null) { "Sensor credentials unavailable" }
            if (stored == null) return@buildMap

            fun encoded(bytes: ByteArray) =
                java.util.Base64
                    .getEncoder()
                    .encodeToString(bytes)
            put("pairingCode", stored.pairingCode)
            stored.sensorSerial?.let { put("sensorSerial", it) }
            stored.gtin?.let { put("gtin", it) }
            put("authority", encoded(stored.gKey.certificateAuthority))
            put("certificate", encoded(stored.gKey.certificate))
            put("privateKey", encoded(stored.gKey.privateKey))
            stored.sharedKey?.let { put("sharedKey", encoded(it)) }
            stored.sharedKeyAddress?.let { put("sharedAddress", it) }
        }

    // KTX edit cannot report whether the required synchronous commit succeeded.
    @Suppress("UseKtx")
    override fun importSecrets(values: Map<String, String>) {
        val ctx = requireNotNull(context)
        val store = CgmCredentialStore(ctx)
        val code = values["pairingCode"]
        if (code == null) {
            store.clearAll()
        } else {
            fun decoded(name: String) =
                java.util.Base64
                    .getDecoder()
                    .decode(requireNotNull(values[name]))
            store.saveSetup(
                app.sugarlicious.sensor.protocol
                    .CgmSetupPayload(code, values["sensorSerial"], values["gtin"]),
                app.sugarlicious.sensor.protocol
                    .CgmGKeyParts(decoded("authority"), decoded("certificate"), decoded("privateKey")),
            )
            if (values["sharedKey"] != null && values["sharedAddress"] != null) store.saveSharedKey(requireNotNull(values["sharedAddress"]), decoded("sharedKey"))
        }
        check(store.commitPendingWrites())
        check(
            ctx
                .getSharedPreferences("g7_setup", android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean("productAccessReview", true)
                .commit(),
        )
    }
}
