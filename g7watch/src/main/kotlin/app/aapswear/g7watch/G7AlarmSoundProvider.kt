package app.aapswear.g7watch

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import app.aapswear.g7.CgmAlarmType
import java.io.FileNotFoundException

internal fun g7AlarmSoundUri(
    context: Context,
    type: CgmAlarmType,
): Uri =
    Uri
        .Builder()
        .scheme("content")
        .authority("${context.packageName}.alarm-sounds")
        .appendPath(type.name.lowercase())
        .build()

class G7AlarmSoundProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "audio/mp4"

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
        require(mode == "r") { "Alarm sounds are read-only" }
        val type =
            uri.lastPathSegment
                ?.uppercase()
                ?.let { runCatching { CgmAlarmType.valueOf(it) }.getOrNull() }
                ?: throw FileNotFoundException("Unknown alarm sound")
        return requireNotNull(context).assets.openFd(g7AlarmSoundAsset(type))
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
