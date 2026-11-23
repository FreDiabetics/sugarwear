package app.sugarwear

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class FingerstickJournal(context: Context) : SQLiteOpenHelper(context, "fingersticks.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE fingersticks(id TEXT PRIMARY KEY, value TEXT NOT NULL, unit TEXT NOT NULL, measured_at INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Unsupported fingerstick journal migration $oldVersion -> $newVersion")
    }

    fun record(id: String, value: String, unit: String, measuredAt: Long) {
        val values = ContentValues().apply {
            put("id", id)
            put("value", value)
            put("unit", unit)
            put("measured_at", measuredAt)
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val exists = db.rawQuery("SELECT 1 FROM fingersticks WHERE id = ?", arrayOf(id)).use { it.moveToFirst() }
            if (!exists) db.insertOrThrow("fingersticks", null, values)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
