package com.example.filesearcher

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var btnScan: Button
    private lateinit var tvResults: TextView
    
    private val targetNames = listOf(
        "libil2cpp.so",
        "Yandere.zip",
        "R4x",
        "Viento",
        "Spoof_lios"
    )
    
    private val foundPaths = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnScan = findViewById(R.id.btnScan)
        tvResults = findViewById(R.id.tvResults)

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
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
    }

    private fun runSearch() {
        foundPaths.clear()
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
