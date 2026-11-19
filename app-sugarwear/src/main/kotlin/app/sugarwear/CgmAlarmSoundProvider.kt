package app.sugarwear

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import java.io.FileNotFoundException

internal fun cgmAlarmSoundUri(
    context: Context,
    type: SugarWearAlarmType,
): Uri =
    Uri
        .Builder()
        .scheme("content")
        .authority("${context.packageName}.alarm-sounds")
        .appendPath(type.id)
        .build()

class CgmAlarmSoundProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "audio/mp4"

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
        require(mode == "r") { "Alarm sounds are read-only" }
        val pathSegment = uri.lastPathSegment ?: throw FileNotFoundException("Unknown alarm sound")
        val type =
            SugarWearAlarmType.fromId(pathSegment)
                ?: runCatching { SugarWearAlarmType.valueOf(pathSegment.uppercase()) }.getOrNull()
                ?: throw FileNotFoundException("Unknown alarm sound: $pathSegment")
        return requireNotNull(context).assets.openFd(cgmAlarmSoundAsset(type))
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
