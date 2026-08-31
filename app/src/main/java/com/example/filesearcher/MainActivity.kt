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
import java.util.Locale
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
            val rootDir = Environment.getExternalStorageDirectory()
            // 1. Сканируем стандартную память устройства
            searchFiles(rootDir, currentTargets)

            // 2. Идем в скрытый кэш всех приложений (Android/data)
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
            val match = targets.firstOrNull { target ->
                file.name.contains(target, ignoreCase = true)
            }
            if (match != null) {
                val parentName = dir.parentFile?.name ?: ""
                val prefix = if (dir.absolutePath.contains("Android/data")) "[Кэш: $parentName]" else "[Память]"
                
                // Получаем расширение (тип файла) и его размер в читаемом виде
                val fileType = file.extension.uppercase(Locale.getDefault()).ifEmpty { "БЕЗ РАСШИРЕНИЯ" }
                val fileSize = formatFileSize(file.length())

                foundPaths.append("$prefix Найдено ($match)\n")
                foundPaths.append("📄 Тип: .$fileType | ⚖️ Вес: $fileSize\n")
                foundPaths.append("📍 Путь: ${file.absolutePath}\n\n")
            }
            
            if (file.isDirectory && !file.name.startsWith(".")) {
                searchFiles(file, targets)
            }
        }
    }

    // Функция перевода байт в КБ, МБ или ГБ
    private fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 Б"
        val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
        val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.getDefault(), "%.2f %s", sizeInBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
