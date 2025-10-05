package com.example.sagboat

import android.os.Bundle
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogActivity : AppCompatActivity() {

    private lateinit var tableLayout: TableLayout
    private lateinit var dbHelper: DBHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        tableLayout = findViewById(R.id.tableLayout)
        dbHelper = DBHelper(this)

        addTableHeaders()
        loadLogEntries()
    }

    private fun addTableHeaders() {
        val headerRow = TableRow(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@LogActivity, R.color.gray))
            addView(createHeaderTextView(getString(R.string.date)))
            addView(createHeaderTextView(getString(R.string.time)))
            addView(createHeaderTextView(getString(R.string.ph)))
            addView(createHeaderTextView(getString(R.string.ph_level)))
            addView(createHeaderTextView(getString(R.string.turbidity)))
            addView(createHeaderTextView(getString(R.string.turbidity_level)))
            addView(createHeaderTextView(getString(R.string.delete))) // Using string resource for the delete header
        }
        tableLayout.addView(headerRow)
    }

    private fun createHeaderTextView(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setPadding(8, 8, 8, 8)
            setTextColor(ContextCompat.getColor(this@LogActivity, R.color.black))
            textSize = 12f
            gravity = android.view.Gravity.CENTER
        }
    }

    private fun loadLogEntries() {
        lifecycleScope.launch {
            val logEntries = withContext(Dispatchers.IO) {
                try {
                    dbHelper.getAllLogEntries()
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }
            }

            tableLayout.removeAllViews()
            addTableHeaders()

            for ((index, logEntry) in logEntries.withIndex()) {
                val tableRow = TableRow(this@LogActivity).apply {
                    setBackgroundColor(
                        if (index % 2 == 0) ContextCompat.getColor(this@LogActivity, R.color.gray_light)
                        else ContextCompat.getColor(this@LogActivity, R.color.gray)
                    )
                }

                tableRow.addView(createDataTextView(logEntry.date))
                tableRow.addView(createDataTextView(logEntry.time))
                tableRow.addView(createDataTextView(logEntry.ph.toString()))
                tableRow.addView(createDataTextView(logEntry.phLevel))
                tableRow.addView(createDataTextView(logEntry.turbidity.toString()))
                tableRow.addView(createDataTextView(logEntry.turbidityLevel))

                val deleteButton = Button(this@LogActivity).apply {
                    text = getString(R.string.delete) // Using string resource for the delete button text
                    setOnClickListener { showDeleteConfirmation(logEntry) }
                }
                tableRow.addView(deleteButton)
                tableLayout.addView(tableRow)
            }
        }
    }

    private fun createDataTextView(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setPadding(8, 8, 8, 8)
            setTextColor(ContextCompat.getColor(this@LogActivity, R.color.black))
            textSize = 12f
            gravity = android.view.Gravity.CENTER
        }
    }

    private fun showDeleteConfirmation(logEntry: LogEntry) {
        AlertDialog.Builder(this).apply {
            setTitle(getString(R.string.delete_entry_title)) // Using string resource for dialog title
            setMessage(getString(R.string.delete_entry_confirmation)) // Using string resource for dialog message
            setPositiveButton(getString(R.string.yes)) { _, _ -> deleteLogEntry(logEntry) }
            setNegativeButton(getString(R.string.no), null)
            show()
        }
    }

    private fun deleteLogEntry(logEntry: LogEntry) {
        val isDeleted = dbHelper.deleteLogEntry(logEntry.date, logEntry.time)
        if (isDeleted) {
            Toast.makeText(this, getString(R.string.delete_success), Toast.LENGTH_SHORT).show() // Using string resource for success message
            loadLogEntries()
        } else {
            Toast.makeText(this, getString(R.string.delete_failure), Toast.LENGTH_SHORT).show() // Using string resource for failure message
        }
    }
}
