package app.sugarlicious.wear

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.edit
import androidx.wear.watchfacepush.WatchFacePushManager
import androidx.wear.watchfacepush.WatchFacePushManagerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.File

internal data class ManagedWatchFaceSlot(
    val slotId: String,
    val packageName: String,
    val isActive: Boolean,
)

internal fun selectManagedWatchFaceSlot(
    slots: List<ManagedWatchFaceSlot>,
    requestedPackageName: String,
): ManagedWatchFaceSlot? =
    slots.firstOrNull { it.isActive }
        ?: slots.firstOrNull { it.packageName == requestedPackageName }
        ?: slots.firstOrNull()

internal const val SUGARLICIOUS_MANAGED_FACE_COUNT = 3

internal object SugarliciousWatchFacePush {
    const val ACTIVE_PERMISSION =
        "com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE"

    private const val PREFS = "sugarlicious_watchface_push"
    private const val LAST_APPLIED_FACE = "last_applied_face"
    private const val LAST_APPLIED_AT = "last_applied_at"
    private const val DIRECT_ACTIVATION_ATTEMPTED = "direct_activation_attempted"
    private const val ACTIVATION_PACKAGE = "activation_package"
    private const val SETTLING_WINDOW_MS = 15_000L
    private const val MANUAL_ACTIVATION_MESSAGE =
        "Watchface geladen - auf der Uhr lange drücken und Sugarlicious auswählen"
    private const val TAG = "WatchFacePush"

    internal data class FaceSpec(
        val packageName: String,
        val apkAsset: String,
        val tokenAsset: String,
    )

    internal val activeFaceSpecs =
        listOf(
            FaceSpec(
                "app.sugarlicious.watchfacepush.apex",
                "watchfaces/sugarlicious_analog.apk",
                "watchfaces/sugarlicious_analog_token.txt",
            ),
            FaceSpec(
                "app.sugarlicious.watchfacepush.digital",
                "watchfaces/sugarlicious_digital.apk",
                "watchfaces/sugarlicious_digital_token.txt",
            ),
            FaceSpec(
                "app.sugarlicious.watchfacepush.orbit",
                "watchfaces/sugarlicious_orbit.apk",
                "watchfaces/sugarlicious_orbit_token.txt",
            ),
        )

    private val faces = activeFaceSpecs

    fun isSupported(): Boolean =
        runCatching {
            WatchFacePushManagerFactory.isSupported()
        }.getOrDefault(false)

    fun hasActivationPermission(context: Context): Boolean =
        context.checkSelfPermission(ACTIVE_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun activeFaceIndex(context: Context): Int? {
        if (!isSupported()) return null

        val detected =
            runCatching {
                val manager =
                    WatchFacePushManagerFactory
                        .createWatchFacePushManager(context)
                val installed =
                    manager
                        .listWatchFaces()
                        .installedWatchFaceDetails

                faces
                    .indexOfFirst { face ->
                        installed.any { details ->
                            details.packageName == face.packageName &&
                                runCatching {
                                    manager.isWatchFaceActive(details.packageName)
                                }.getOrDefault(false)
                        }
                    }.takeIf { it >= 0 }
            }.getOrNull()

        if (detected != null) return detected

        val prefs = activationPreferences(context)
        val appliedAt = prefs.getLong(LAST_APPLIED_AT, 0L)
        if (System.currentTimeMillis() - appliedAt !in 0..SETTLING_WINDOW_MS) return null
        return prefs
            .getInt(LAST_APPLIED_FACE, -1)
            .takeIf { it in 0 until SUGARLICIOUS_MANAGED_FACE_COUNT }
    }

    suspend fun apply(
        context: Context,
        index: Int,
    ): String {
        if (!isSupported()) {
            return "Direktwechsel braucht Wear OS 6 oder neuer"
        }

        val spec =
            faces.getOrNull(index)
                ?: return "Unbekanntes Watchface"

        val token =
            runCatching {
                context.assets
                    .open(spec.tokenAsset)
                    .bufferedReader()
                    .use { it.readText().trim() }
            }.getOrElse {
                return "Watchface-Token fehlt"
            }

        if (token.isBlank()) {
            return "Watchface-Token ist leer"
        }

        val apk =
            runCatching {
                copyAssetToCache(
                    context,
                    spec.apkAsset,
                )
            }.getOrElse {
                return "Watchface-APK fehlt"
            }

        return try {
            val manager =
                WatchFacePushManagerFactory
                    .createWatchFacePushManager(context)
            val installed =
                manager
                    .listWatchFaces()
                    .installedWatchFaceDetails
            Log.i(
                TAG,
                "Installed slots before update: " +
                    installed.joinToString { details ->
                        "${details.slotId}=${details.packageName}"
                    },
            )

            val managed = installed
            val managedSlots =
                managed.map { details ->
                    ManagedWatchFaceSlot(
                        slotId = details.slotId,
                        packageName = details.packageName,
                        isActive =
                            runCatching {
                                manager.isWatchFaceActive(details.packageName)
                            }.getOrDefault(false),
                    )
                }

            val selectedSlot =
                selectManagedWatchFaceSlot(
                    slots = managedSlots,
                    requestedPackageName = spec.packageName,
                )
            val target =
                selectedSlot?.let { slot ->
                    managed.firstOrNull { details -> details.slotId == slot.slotId }
                }
            val targetWasActive = selectedSlot?.isActive == true

            val details =
                ParcelFileDescriptor
                    .open(
                        apk,
                        ParcelFileDescriptor.MODE_READ_ONLY,
                    ).use { pfd ->
                        if (target == null) {
                            manager.addWatchFace(pfd, token)
                        } else {
                            manager.updateWatchFace(target.slotId, pfd, token)
                        }
                    }
            Log.i(TAG, "Updated slot ${details.slotId}=${details.packageName}; wasActive=$targetWasActive")

            when {
                targetWasActive -> {
                    rememberApplied(context, index)
                    "Watchface aktiv"
                }
                !hasActivationPermission(context) ->
                    "Watchface geladen - Direktwechsel auf der Uhr freigeben"
                directActivationWasAttempted(context) -> MANUAL_ACTIVATION_MESSAGE
                else -> {
                    activateInstalledSlot(
                        context = context,
                        manager = manager,
                        initialSlotId = details.slotId,
                        packageName = details.packageName,
                        index = index,
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            "Watchface-Wechsel fehlgeschlagen: ${error.javaClass.simpleName}"
        } finally {
            apk.delete()
        }
    }

    private fun rememberApplied(
        context: Context,
        index: Int,
    ) {
        activationPreferences(context)
            .edit {
                putInt(LAST_APPLIED_FACE, index)
                    .putLong(LAST_APPLIED_AT, System.currentTimeMillis())
            }
    }

    internal fun directActivationWasAttempted(context: Context): Boolean {
        val prefs = activationPreferences(context)
        return prefs.getBoolean(DIRECT_ACTIVATION_ATTEMPTED, false)
    }

    @Synchronized
    private fun activationPreferences(context: Context): android.content.SharedPreferences {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val owner = preferences.getString(ACTIVATION_PACKAGE, null)
        if (owner != context.packageName) {
            val imported = owner != null || File(context.noBackupFilesDir, "product-migration-complete").takeIf { it.isFile }?.readText() == "acknowledged"
            preferences
                .edit {
                    putString(ACTIVATION_PACKAGE, context.packageName)
                    if (imported) {
                        remove(DIRECT_ACTIVATION_ATTEMPTED)
                        remove(LAST_APPLIED_FACE)
                        remove(LAST_APPLIED_AT)
                    }
                }
        }
        return preferences
    }

    private suspend fun activateInstalledSlot(
        context: Context,
        manager: WatchFacePushManager,
        initialSlotId: String,
        packageName: String,
        index: Int,
    ): String {
        var slotId = initialSlotId
        val settlingDelays = longArrayOf(0L, 750L, 1_500L)

        settlingDelays.forEachIndexed { attempt, settlingDelay ->
            if (settlingDelay > 0L) {
                delay(settlingDelay)
                slotId =
                    runCatching {
                        manager
                            .listWatchFaces()
                            .installedWatchFaceDetails
                            .firstOrNull { it.packageName == packageName }
                            ?.slotId
                    }.getOrNull() ?: slotId
            }

            try {
                manager.setWatchFaceAsActive(slotId)
                markDirectActivationAttempted(context)
                rememberApplied(context, index)
                return "Watchface aktiv"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: WatchFacePushManager.SetWatchFaceAsActiveException) {
                Log.w(
                    TAG,
                    "Direct activation attempt ${attempt + 1} failed " +
                        "(slot=$slotId, code=${error.errorCode})",
                )
                when (error.errorCode) {
                    WatchFacePushManager.SetWatchFaceAsActiveException.ERROR_MISSING_PERMISSION ->
                        return "Watchface geladen - Direktwechsel auf der Uhr freigeben"

                    WatchFacePushManager.SetWatchFaceAsActiveException.ERROR_MAXIMUM_ATTEMPTS_REACHED -> {
                        markDirectActivationAttempted(context)
                        return MANUAL_ACTIVATION_MESSAGE
                    }

                    WatchFacePushManager.SetWatchFaceAsActiveException.ERROR_INVALID_SLOT_ID,
                    WatchFacePushManager.SetWatchFaceAsActiveException.ERROR_UNKNOWN,
                    -> if (attempt == settlingDelays.lastIndex) return MANUAL_ACTIVATION_MESSAGE
                }
            }
        }

        return MANUAL_ACTIVATION_MESSAGE
    }

    private fun markDirectActivationAttempted(context: Context) {
        activationPreferences(context)
            .edit {
                putBoolean(DIRECT_ACTIVATION_ATTEMPTED, true)
            }
    }

    private fun copyAssetToCache(
        context: Context,
        assetPath: String,
    ): File {
        val target =
            File.createTempFile(
                "sugarlicious-watchface-",
                ".apk",
                context.cacheDir,
            )

        context.assets
            .open(assetPath)
            .use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }

        return target
    }
}
