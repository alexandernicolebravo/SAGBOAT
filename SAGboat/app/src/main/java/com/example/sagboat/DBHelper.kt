package com.example.sagboat

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

data class LogEntry(
    val date: String,
    val time: String,
    val ph: Double,
    val phLevel: String,
    val turbidity: Double,
    val turbidityLevel: String
)

class DBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "log.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "log"
        private const val COLUMN_DATE = "date"
        private const val COLUMN_TIME = "time"
        private const val COLUMN_PH = "ph"
        private const val COLUMN_PH_LEVEL = "phLevel"
        private const val COLUMN_TURBIDITY = "turbidity"
        private const val COLUMN_TURBIDITY_LEVEL = "turbidityLevel"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_DATE TEXT,
                $COLUMN_TIME TEXT,
                $COLUMN_PH REAL,
                $COLUMN_PH_LEVEL TEXT,
                $COLUMN_TURBIDITY REAL,
                $COLUMN_TURBIDITY_LEVEL TEXT
            )
        """
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun addLogEntry(logEntry: LogEntry) {
        val db = writableDatabase
        try {
            val values = ContentValues().apply {
                put(COLUMN_DATE, logEntry.date)
                put(COLUMN_TIME, logEntry.time)
                put(COLUMN_PH, logEntry.ph)
                put(COLUMN_PH_LEVEL, logEntry.phLevel)
                put(COLUMN_TURBIDITY, logEntry.turbidity)
                put(COLUMN_TURBIDITY_LEVEL, logEntry.turbidityLevel)
            }
            db.insert(TABLE_NAME, null, values)
        } catch (e: Exception) {
            Log.e("DBHelper", "Error adding log entry: ${e.message}")
        } finally {
            db.close()
        }
    }

    fun getAllLogEntries(): List<LogEntry> {
        val logEntries = mutableListOf<LogEntry>()
        val db = readableDatabase
        val query = "SELECT * FROM $TABLE_NAME"
        try {
            db.rawQuery(query, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE))
                    val time = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIME))
                    val ph = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_PH))
                    val phLevel = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PH_LEVEL))
                    val turbidity = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_TURBIDITY))
                    val turbidityLevel = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TURBIDITY_LEVEL))
                    logEntries.add(LogEntry(date, time, ph, phLevel, turbidity, turbidityLevel))
                }
            }
        } catch (e: Exception) {
            Log.e("DBHelper", "Error fetching log entries: ${e.message}")
        } finally {
            db.close()
        }
        return logEntries
    }

    fun deleteLogEntry(date: String, time: String): Boolean {
        val db = writableDatabase
        val success = db.delete(TABLE_NAME, "$COLUMN_DATE = ? AND $COLUMN_TIME = ?", arrayOf(date, time)) > 0
        db.close()
        return success
    }
}
