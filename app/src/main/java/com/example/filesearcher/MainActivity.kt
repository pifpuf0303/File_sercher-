package com.example.filesercher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
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
    private lateinit var llResultsContainer: LinearLayout
    private lateinit var tvStatus: TextView
    
    // Автоматический список по умолчанию
    private val defaultTargetNames = listOf("libil2cpp.so", "Yandere.zip", "R4x", "Viento", "Spoof_lios")

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

        // Статусная строка для отображения хода процесса
        tvStatus = TextView(this)
        tvStatus.textSize = 14f
        tvStatus.setPadding(0, 16, 0, 16)
        layout.addView(tvStatus)

        // Контейнер, куда мы будем динамически добавлять кликабельные кнопки файлов
        val scrollView = ScrollView(this)
        llResultsContainer = LinearLayout(this)
        llResultsContainer.orientation = LinearLayout.VERTICAL
        scrollView.addView(llResultsContainer)
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
        // Очищаем старые результаты перед новым поиском
        llResultsContainer.removeAllViews()
        
        val query = etSearchInput.text.toString().trim()
        val currentTargets = if (query.isEmpty()) defaultTargetNames else listOf(query)

        tvStatus.text = "Запущено тотальное сканирование памяти и кэша приложений..."
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
                if (llResultsContainer.childCount == 0) {
                    tvStatus.text = "Скрытые файлы с такими именами не найдены."
                } else {
                    tvStatus.text = "Сканирование завершено. Найдено файлов: ${llResultsContainer.childCount}\nНажмите на файл, чтобы открыть его в проводнике."
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
                
                val fileType = file.extension.uppercase(Locale.getDefault()).ifEmpty { "БЕЗ РАСШИРЕНИЯ" }
                val fileSize = formatFileSize(file.length())

                // Формируем красивый текст для отдельного файла
                val itemText = "$prefix Найдено ($match)\n📄 Тип: .$fileType | ⚖️ Вес: $fileSize\n📍 Путь: ${file.absolutePath}\n"

                runOnUiThread {
                    // Создаем отдельный интерактивный элемент для каждого найденного файла
                    val tvFileItem = TextView(this@MainActivity)
                    tvFileItem.text = itemText
                    tvFileItem.textSize = 14f
                    tvFileItem.setPadding(16, 16, 16, 16)
                    
                    // Делаем красивую рамку, чтобы было понятно, что это кнопка
                    tvFileItem.setBackgroundResource(android.R.drawable.btn_default)
                    
                    // Вешаем обработчик нажатия
                    tvFileItem.setOnClickListener {
                        openFileInExplorer(file)
                    }

                    // Добавляем элемент в общий список на экране
                    llResultsContainer.addView(tvFileItem)
                }
            }
            
            if (file.isDirectory && !file.name.startsWith(".")) {
                searchFiles(file, targets)
            }
        }
    }

    private fun openFileInExplorer(file: File) {
        try {
            // Получаем папку, в которой лежит файл
            val folder = file.parentFile ?: return
            val uri = Uri.parse(folder.absolutePath)
            
            // Формируем запрос операционной системе на открытие проводника
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                setDataAndType(uri, "*/*")
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            
            startActivity(Intent.createChooser(intent, "Открыть папку через:"))
        } catch (e: Exception) {
            // Если точный путь заблокирован системой, открываем стандартные файлы
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.fromFile(file.parentFile), "*/*")
                }
                startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Toast.makeText(this, "Не удалось открыть проводник напрямую. Путь: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 Б"
        val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
        val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.getDefault(), "%.2f %s", sizeInBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
