package app.sugarlicious.mobile

import app.sugarlicious.uishared.ProductDataMigrationProvider
import app.sugarlicious.uishared.ProductMigrationLifecycle

class ProductMigrationProvider : ProductDataMigrationProvider() {
    override val legacyPackage = "app.aapswear"
    override val targetPackage = "app.sugarlicious"

    override fun beforeSnapshot() = ProductMigrationLifecycle.prepareSource(requireNotNull(context))

    override fun afterMigrationAborted() = ProductMigrationLifecycle.restoreSource(requireNotNull(context))

    override fun afterMigrationAccepted() {
        val ctx = requireNotNull(context)
        markLegacyWidgetsMoved(ctx)
        ProductMigrationLifecycle.retireSource(ctx)
    }

    override fun exportSecrets(): Map<String, String> =
        buildMap {
            val ctx = requireNotNull(context)
            val share = AndroidSecretStore("dexcom_share_secret", "sugarlicious_dexcom_share_v1").read(ctx)
            val cloudEndpoint = CloudEndpointSecretStore.read(ctx)
            check(share != null || !ctx.getSharedPreferences("dexcom_share_secret", android.content.Context.MODE_PRIVATE).contains("value"))
            check(cloudEndpoint != null || !ctx.getSharedPreferences("nightscout_treatment_secret", android.content.Context.MODE_PRIVATE).contains("value"))
            share?.let { put("share", it) }
            cloudEndpoint?.let { put("nightscout", it) }
        }

    // KTX edit returns Unit; migration must verify the synchronous disk-commit result.
    @Suppress("UseKtx")
    override fun importSecrets(values: Map<String, String>) {
        val ctx = requireNotNull(context)
        values["share"]?.let { AndroidSecretStore("dexcom_share_secret", "sugarlicious_dexcom_share_v1").save(ctx, it) }
        values["nightscout"]?.let { CloudEndpointSecretStore.save(ctx, it) }
        listOf("dexcom_share_secret", "nightscout_treatment_secret").forEach { check(ctx.getSharedPreferences(it, android.content.Context.MODE_PRIVATE).edit().commit()) }
    }
}

@Suppress("DEPRECATION")
private fun markLegacyWidgetsMoved(context: android.content.Context) {
    val flags = android.content.pm.PackageManager.GET_RECEIVERS or android.content.pm.PackageManager.GET_META_DATA or android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS
    val manager = android.appwidget.AppWidgetManager.getInstance(context)
    val receivers =
        context.packageManager
            .getPackageInfo(context.packageName, flags)
            .receivers
            .orEmpty()
    receivers.filter { it.metaData?.containsKey(android.appwidget.AppWidgetManager.META_DATA_APPWIDGET_PROVIDER) == true }.forEach {
        val ids = manager.getAppWidgetIds(android.content.ComponentName(context.packageName, it.name))
        if (ids.isNotEmpty()) {
            val view =
                android.widget.RemoteViews("android", android.R.layout.simple_list_item_1).apply {
                    setTextViewText(android.R.id.text1, "Sugarlicious aktualisiert\nWidget bitte neu hinzuf�gen")
                    setTextColor(android.R.id.text1, android.graphics.Color.WHITE)
                    setInt(android.R.id.text1, "setBackgroundColor", android.graphics.Color.BLACK)
                    setInt(android.R.id.text1, "setGravity", android.view.Gravity.CENTER)
                    setTextViewTextSize(android.R.id.text1, android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                }
            manager.updateAppWidget(ids, view)
        }
    }
}
