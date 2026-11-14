package app.sugarlicious.mobile

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat

internal data class DeviceVendorLiveTexts(
    val drawerPrimary: String,
    val drawerSecondary: String?,
    val nowBarPrimary: String,
    val nowBarSecondary: String?,
    val chipExpandedText: String,
)

internal object DeviceVendorLiveNotificationAdapter {
    const val EXTRA_STYLE = "android.ongoingActivityNoti.style"
    const val EXTRA_PRIMARY_INFO = "android.ongoingActivityNoti.primaryInfo"
    const val EXTRA_SECONDARY_INFO = "android.ongoingActivityNoti.secondaryInfo"
    const val EXTRA_CHIP_BACKGROUND_COLOR = "android.ongoingActivityNoti.chipBgColor"
    const val EXTRA_CHIP_ICON = "android.ongoingActivityNoti.chipIcon"
    const val EXTRA_CARD_ICON = "android.ongoingActivityCardIcon"
    const val EXTRA_CARD_ICON_BACKGROUND = "android.ongoingActivityCardIconBg"
    const val EXTRA_CHIP_EXPANDED_TEXT = "android.ongoingActivityNoti.chipExpandedText"
    const val EXTRA_CHIP_EXPANDED_VIEW = "android.ongoingActivityNoti.chipExpandedView"
    const val EXTRA_NOWBAR_PRIMARY_INFO = "android.ongoingActivityNoti.nowbarPrimaryInfo"
    const val EXTRA_NOWBAR_SECONDARY_INFO = "android.ongoingActivityNoti.nowbarSecondaryInfo"
    const val EXTRA_NOWBAR_ICON = "android.ongoingActivityNoti.nowbarIcon"
    const val EXTRA_AOD_REMOTE_APP_ICON = "android.ongoingActivityNoti.aodRemoteAppIcon"
    const val EXTRA_EXPANDED_REMOTE_VIEW = "android.ongoingActivityNoti.expandedRemoteView"
    const val EXTRA_NOWBAR_REMOTE_VIEW = "android.ongoingActivityNoti.nowbarRemoteView"
    const val EXTRA_CUSTOM_REMOTE_VIEW = "android.ongoingActivityNoti.chronometerRemoteView"
    const val EXTRA_CUSTOM_REMOTE_VIEW_POSITION = "android.ongoingActivityNoti.chronometerRemoteViewPosition"
    const val EXTRA_CUSTOM_REMOTE_VIEW_TAG = "android.ongoingActivityNoti.chronometerRemoteViewTag"

    private const val STANDARD_STYLE = 1
    private const val CUSTOM_CHIP_ENABLED = false

    fun apply(
        context: Context,
        builder: NotificationCompat.Builder,
        presentation: NotificationPresentation,
        manufacturer: String,
        expandedRemoteView: RemoteViews? = null,
    ): DeviceVendorLiveTexts? {
        if (!manufacturer.equals("samsung", ignoreCase = true)) return null
        val texts = texts(presentation)
        val extras =
            Bundle().apply {
                putInt(EXTRA_STYLE, STANDARD_STYLE)
                putInt(EXTRA_CHIP_BACKGROUND_COLOR, systemAccentColor(context))
                putString(EXTRA_PRIMARY_INFO, texts.drawerPrimary)
                putString(EXTRA_NOWBAR_PRIMARY_INFO, texts.nowBarPrimary)
                putString(EXTRA_CHIP_EXPANDED_TEXT, texts.chipExpandedText)
                if (CUSTOM_CHIP_ENABLED) {
                    putParcelable(
                        EXTRA_CHIP_EXPANDED_VIEW,
                        RemoteViews(context.packageName, R.layout.notification_device_vendor_live_chip).apply {
                            setTextViewText(
                                R.id.live_chip_text,
                                app.sugarlicious.uishared.SystemTypography
                                    .emphasizedText(texts.chipExpandedText),
                            )
                        },
                    )
                }
                expandedRemoteView?.let {
                    putParcelable(EXTRA_CUSTOM_REMOTE_VIEW, it)
                    putInt(EXTRA_CUSTOM_REMOTE_VIEW_POSITION, 1)
                    putString(EXTRA_CUSTOM_REMOTE_VIEW_TAG, "sugarlicious_expanded_notification")
                }
                texts.nowBarSecondary?.let { putString(EXTRA_NOWBAR_SECONDARY_INFO, it) }
            }
        builder.addExtras(extras)
        return texts
    }

    /** Read the device palette directly, before Sugarlicious theme overrides. */
    internal fun systemAccentColor(context: Context): Int {
        if (Build.VERSION.SDK_INT >= 31) {
            val dark = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
            return (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary.toArgb()
        }
        val value = TypedValue()
        return if (context.theme.resolveAttribute(android.R.attr.colorAccent, value, true)) value.data else context.getColor(android.R.color.holo_blue_light)
    }

    /** Notification badges need the counterpart shade, independently of the app's own theme. */
    internal fun systemNotificationIconColor(context: Context): Int {
        if (Build.VERSION.SDK_INT >= 31) {
            val dark = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
            return (if (dark) dynamicLightColorScheme(context) else dynamicDarkColorScheme(context)).primary.toArgb()
        }
        return systemAccentColor(context)
    }

    fun texts(presentation: NotificationPresentation): DeviceVendorLiveTexts {
        val primary = presentation.compactPrimaryText ?: presentation.statusText ?: "Keine Quelle"
        val nowBarPrimary =
            listOfNotNull(
                presentation.glucoseText,
                presentation.nowBarTrendSymbol,
            ).takeIf(List<String>::isNotEmpty)?.joinToString(" ") ?: presentation.statusText ?: "Keine Quelle"
        val nowBarSecondary =
            listOfNotNull(
                presentation.deltaText?.let { delta ->
                    presentation.unitText?.let { unit -> "$delta $unit" } ?: delta
                },
                presentation.ageText,
            ).takeIf(List<String>::isNotEmpty)?.joinToString(" · ")
        return DeviceVendorLiveTexts(
            drawerPrimary = presentation.drawerPrimaryText,
            drawerSecondary = presentation.therapyText,
            nowBarPrimary = nowBarPrimary,
            nowBarSecondary = nowBarSecondary,
            chipExpandedText = primary,
        )
    }
}
