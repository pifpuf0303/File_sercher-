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
    
    // Твой старый автоматический список
    private val defaultTargetNames = listOf("libil2cpp.so", "Yandere.zip", "R4x", "Viento", "Spoof_lios")
    private val foundPaths = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Создаем контейнер для элементов интерфейса
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(32, 32, 32, 32)

        // 1. Добавляем поисковую строку
        etSearchInput = EditText(this)
        etSearchInput.hint = "Введите название файла или расширение..."
        layout.addView(etSearchInput)

        // 2. Наша главная кнопка сканирования
        btnScan = Button(this)
        btnScan.text = "Запустить поиск"
        layout.addView(btnScan)

        // 3. Область вывода результатов с прокруткой
        val scrollView = ScrollView(this)
        tvResults = TextView(this)
        tvResults.textSize = 16f
        scrollView.addView(tvResults)
        layout.addView(scrollView)

        setContentView(layout)

        // Обработка нажатия на кнопку
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
        
        // Читаем, что ввёл пользователь в строку поиска
        val query = etSearchInput.text.toString().trim()
        
        // Определяем список для поиска: если пусто — берём старый список, если написано — ищем это слово
        val currentTargets = if (query.isEmpty()) defaultTargetNames else listOf(query)

        tvResults.text = "Сканирование запущено..."
        btnScan.isEnabled = false

        thread {
            val rootDir = Environment.getExternalStorageDirectory()
            searchFiles(rootDir, currentTargets)

            runOnUiThread {
                btnScan.isEnabled = true
                if (foundPaths.isEmpty()) {
                    tvResults.text = "Ничего не найдено."
                } else {
                    tvResults.text = foundPaths.toString()
                }
                Toast.makeText(this, "Сканирование завершено", Toast.LENGTH_SHORT).show()
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
                foundPaths.append("Найдено совпадение [${match}]:\n${file.absolutePath}\n\n")
            }
            if (file.isDirectory && !file.name.startsWith(".")) {
                searchFiles(file, targets)
            }
        }
    }
}
