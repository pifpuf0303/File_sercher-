package com.example.filesercher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var etSearchInput: EditText
    private lateinit var btnScan: Button
    private lateinit var tvResults: TextView
    
    // Автоматический список по умолчанию
    private val defaultTargetNames = listOf("libil2cpp.so", "Yandere.zip", "R4x", "Viento", "Spoof_lios")
    private val foundPaths = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(32, 32, 32, 32)

        etSearchInput = EditText(this)
        etSearchInput.hint = "Название файла, расширение или имя приложения..."
        layout.addView(etSearchInput)

        btnScan = Button(this)
        btnScan.text = "Глубокий поиск везде"
        layout.addView(btnScan)

        val scrollView = ScrollView(this)
        tvResults = TextView(this)
        tvResults.textSize = 14f
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
        val query = etSearchInput.text.toString().trim()
        val currentTargets = if (query.isEmpty()) defaultTargetNames else listOf(query)

        tvResults.text = "Запущено тотальное сканирование памяти и кэша приложений..."
        btnScan.isEnabled = false

        thread {
            // 1. Сканируем стандартную память устройства
            val rootDir = Environment.getExternalStorageDirectory()
            searchFiles(rootDir, currentTargets)

            // 2. Целенаправленно идем в скрытый кэш всех приложений (Android/data)
            val androidDataDir = File(rootDir, "Android/data")
            if (androidDataDir.exists() && androidDataDir.isDirectory) {
                searchFiles(androidDataDir, currentTargets)
            }

            runOnUiThread {
                btnScan.isEnabled = true
                if (foundPaths.isEmpty()) {
                    tvResults.text = "Скрытые файлы с такими именами не найдены."
                } else {
                    tvResults.text = foundPaths.toString()
                }
                Toast.makeText(this, "Глубокий поиск завершен", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun searchFiles(dir: File, targets: List<String>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            // Проверяем совпадение имени файла с целями поиска
            val match = targets.firstOrNull { target ->
                file.name.contains(target, ignoreCase = true)
            }
            if (match != null) {
                // Если файл найден внутри папки Android/data, помечаем, какому приложению он принадлежит
                val parentName = dir.parentFile?.name ?: ""
                val prefix = if (dir.absolutePath.contains("Android/data")) "[Кэш приложения: $parentName]" else "[Пямять]"
                foundPaths.append("$prefix Найдено совпадение ($match):\n${file.absolutePath}\n\n")
            }
            
            // Рекурсивно идем глубже, если это папка (пропускаем скрытые точки типа .thumbnails)
            if (file.isDirectory && !file.name.startsWith(".")) {
                searchFiles(file, targets)
            }
        }
    }
}
