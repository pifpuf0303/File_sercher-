package com.example.filesercher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var btnScan: Button
    private lateinit var tvResults: TextView
    private val targetNames = listOf("libil2cpp.so", "Yandere.zip", "R4x", "Viento", "Spoof_lios")
    private val foundPaths = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Создаем интерфейс прямо в коде, чтобы не зависеть от XML-файлов разметки
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(32, 32, 32, 32)

        btnScan = Button(this)
        btnScan.text = "Запустить сканирование"
        layout.addView(btnScan)

        val scrollView = ScrollView(this)
        tvResults = TextView(this)
        tvResults.textSize = 16f
        scrollView.addView(tvResults)
        layout.addView(scrollView)

        setContentView(layout)

        btnScan.setOnClickListener {
            if (hasAllFilesPermission()) {
                runSearch()
            } else {
                requestAllFilesPermission()
            }
        }
    }

    private fun hasAllFilesPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun requestAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            startActivity(intent)
        }
    }

    private fun runSearch() {
        foundPaths.setLength(0)
        tvResults.text = "Сканирование запущено..."
        btnScan.isEnabled = false

        thread {
            val rootDir = Environment.getExternalStorageDirectory()
            searchFiles(rootDir)

            runOnUiThread {
                btnScan.isEnabled = true
                if (foundPaths.isEmpty()) {
                    tvResults.text = "Указанные файлы не найдены."
                } else {
                    tvResults.text = foundPaths.toString()
                }
                Toast.makeText(this, "Сканирование завершено", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun searchFiles(dir: File) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            val match = targetNames.firstOrNull { target ->
                file.name.contains(target, ignoreCase = true)
            }
            if (match != null) {
                foundPaths.append("Найдено совпадение [${match}]:\n${file.absolutePath}\n\n")
            }
            if (file.isDirectory && !file.name.startsWith(".")) {
                searchFiles(file)
            }
        }
    }
}
