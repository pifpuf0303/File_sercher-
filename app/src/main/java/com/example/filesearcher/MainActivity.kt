package com.example.filesercher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
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

        tvStatus = TextView(this)
        tvStatus.textSize = 14f
        tvStatus.setPadding(0, 16, 0, 16)
        layout.addView(tvStatus)

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
        llResultsContainer.removeAllViews()
        val query = etSearchInput.text.toString().trim()
        val currentTargets = if (query.isEmpty()) defaultTargetNames else listOf(query)

        tvStatus.text = "Запущено тотальное сканирование памяти и кэша приложений..."
        btnScan.isEnabled = false

        thread {
            val rootDir = Environment.getExternalStorageDirectory()
            searchFiles(rootDir, currentTargets)

            val androidDataDir = File(rootDir, "Android/data")
            if (androidDataDir.exists() && androidDataDir.isDirectory) {
                searchFiles(androidDataDir, currentTargets)
            }

            runOnUiThread {
                btnScan.isEnabled = true
                if (llResultsContainer.childCount == 0) {
                    tvStatus.text = "Скрытые файлы с такими именами не найдены."
                } else {
                    tvStatus.text = "Сканирование завершено. Найдено файлов: ${llResultsContainer.childCount}\nНажмите на файл, чтобы открыть его ПРЯМО в папке."
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

                val itemText = "$prefix Найдено ($match)\n📄 Тип: .$fileType | ⚖️ Вес: $fileSize\n📍 Путь: ${file.absolutePath}\n"

                runOnUiThread {
                    val tvFileItem = TextView(this@MainActivity)
                    tvFileItem.text = itemText
                    tvFileItem.textSize = 14f
                    tvFileItem.setPadding(16, 16, 16, 16)
                    tvFileItem.setBackgroundResource(android.R.drawable.btn_default)
                    
                    tvFileItem.setOnClickListener {
                        openFolderDirectly(file)
                    }

                    llResultsContainer.addView(tvFileItem)
                }
            }
            
            if (file.isDirectory && !file.name.startsWith(".")) {
                searchFiles(file, targets)
            }
        }
    }

    // Новая мощная функция для точного открытия папки
    private fun openFolderDirectly(file: File) {
        try {
            val folder = file.parentFile ?: return
            
            // Вычисляем относительный путь для системного провайдера DocumentsContract
            val relativePath = folder.absolutePath
                .replace("${Environment.getExternalStorageDirectory().absolutePath}/", "")
                .replace(Environment.getExternalStorageDirectory().absolutePath, "")

            // Создаем правильный системный Uri для глубокого перехода в конкретную папку
            val authority = "com.android.externalstorage.documents"
            val documentId = if (relativePath.isEmpty()) "primary:" else "primary:$relativePath"
            val uri = DocumentsContract.buildDocumentUri(authority, documentId)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(intent)
        } catch (e: Exception) {
            // Резервный вариант, если системный проводник вырезали из прошивки
            try {
                val fallbackUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:" + 
                    file.parentFile.absolutePath.replace("${Environment.getExternalStorageDirectory().absolutePath}/", ""))
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fallbackUri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Toast.makeText(this, "Ошибка перехода в папку. Путь: ${file.parentFile.absolutePath}", Toast.LENGTH_LONG).show()
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
