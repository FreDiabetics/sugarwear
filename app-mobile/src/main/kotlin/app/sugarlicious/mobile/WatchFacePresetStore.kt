package app.sugarlicious.mobile

import android.content.Context
import androidx.core.content.edit

/** Keeps the last phone-side complication configuration for each Sugarlicious watch face. */
internal object WatchFacePresetStore {
    private const val PREFS = "sugarlicious_watchface_presets"
    private const val GLOBAL_PRESET_PREFS = "complication_setup"
    private const val GLOBAL_PRESET_KEY = "selected_ids"

    internal val supportedFaceIndices: IntRange
        get() = sugarliciousWatchFaceCards.indices

    /** Phone-side editor draft only; native provider assignment remains owned by Wear OS. */
    fun readSlots(context: Context, faceIndex: Int): Map<Int, Int> {
        val slots = nativeSlotPreviewEntries(slots(context, faceIndex))
        val encoded =
            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("face_${normalizeFaceIndex(faceIndex)}_native_slots", null) ?: return emptyMap()
        return encoded
            .split(',')
            .mapNotNull { pair ->
                val parts = pair.split(':')
                val slot = parts.getOrNull(0)?.toIntOrNull()?.let { id -> slots.firstOrNull { it.id == id } }
                val variant = parts.getOrNull(1)?.toIntOrNull()
                if (slot != null && variant != null && eligibleNativeVariants(slot).any { it.id == variant }) slot.id to variant else null
            }.toMap()
    }

    fun saveSlots(context: Context, faceIndex: Int, assignments: Map<Int, Int>) {
        val slots = nativeSlotPreviewEntries(slots(context, faceIndex))
        val valid = assignments.filter { (id, variant) -> slots.firstOrNull { it.id == id }?.let { slot -> eligibleNativeVariants(slot).any { it.id == variant } } == true }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString("face_${normalizeFaceIndex(faceIndex)}_native_slots", valid.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" })
        }
    }

    private fun slots(context: Context, faceIndex: Int): List<NativeWatchSlot> =
        when (normalizeFaceIndex(faceIndex)) {
            0 -> context.resources.openRawResource(R.raw.native_apex_slots).use(::readNativeWatchSlots)
            2 -> context.resources.openRawResource(R.raw.native_orbit_slots).use(::readNativeWatchSlots)
            else -> emptyList()
        }

    fun read(
        context: Context,
        faceIndex: Int,
    ): List<Int> {
        val normalized = normalizeFaceIndex(faceIndex)
        val stored =
            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(key(normalized), null)
                ?.let(::decode)

        return stored.orEmpty()
    }

    fun readAll(context: Context): List<List<Int>> = supportedFaceIndices.map { faceIndex -> read(context, faceIndex) }

    fun save(
        context: Context,
        faceIndex: Int,
        complicationIds: List<Int>,
    ) {
        val normalized = normalizeFaceIndex(faceIndex)
        val ids = complicationIds.validPresetIds()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(key(normalized), ids.joinToString(","))
        }
    }

    /**
     * Makes one face's saved preset the active Data-Layer preset before that face is pushed.
     * The existing global key is intentionally kept because the Watch service already consumes it.
     */
    fun activate(
        context: Context,
        faceIndex: Int,
    ): List<Int> {
        val ids = read(context, faceIndex)
        context.getSharedPreferences(GLOBAL_PRESET_PREFS, Context.MODE_PRIVATE).edit {
            putString(GLOBAL_PRESET_KEY, ids.joinToString(","))
        }
        return ids
    }

    private fun key(faceIndex: Int): String = "face_${faceIndex}_complications"

    private fun normalizeFaceIndex(faceIndex: Int): Int = faceIndex.coerceIn(supportedFaceIndices.first, supportedFaceIndices.last)

    private fun decode(value: String): List<Int> =
        value
            .split(',')
            .mapNotNull(String::toIntOrNull)
            .validPresetIds()

    private fun List<Int>.validPresetIds(): List<Int> =
        filter { id -> id in SugarliciousComplicationVariantIds }
            .distinct()
}
