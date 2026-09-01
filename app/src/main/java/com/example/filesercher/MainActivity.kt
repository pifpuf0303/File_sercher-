package com.example.filesercher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
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
    private lateinit var progressBar: ProgressBar
    
    private lateinit var cbSearchStorage: CheckBox
    private lateinit var cbSearchCache: CheckBox
    
    private val defaultTargetNames = listOf("libil2cpp.so", "Yandere.zip", "R4x", "Viento", "Spoof_lios")
    private var checkedDirsCount = 0

    // Временный список для хранения результатов перед их сортировкой
    private val foundFilesList = mutableListOf<FileResult>()

    // Класс для удобного сохранения данных о найденном файле
    data class FileResult(val file: File, val matchName: String, val isCache: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121214"))
            setPadding(32, 32, 32, 32)
        }

        val tvTitle = TextView(this).apply {
            text = "ME PROJECT : SEARCHER"
            textSize = 20f
            setTextColor(Color.parseColor("#FFD700"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 20)
        }
        mainLayout.addView(tvTitle)

        etSearchInput = EditText(this).apply {
            hint = "Введите имя файла для поиска..."
            setHintTextColor(Color.parseColor("#666666"))
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        mainLayout.addView(etSearchInput)

        val tagsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
        }
        
        val tags = listOf(".zip", ".so", ".apk", "Telegram")
        for (tag in tags) {
            val btnTag = Button(this).apply {
                text = tag
                textSize = 12f
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                    setMargins(4, 0, 4, 0)
                }
                layoutParams = params
                setOnClickListener {
                    etSearchInput.setText(tag)
                    etSearchInput.setSelection(tag.length)
                }
            }
            tagsLayout.addView(btnTag)
        }
        mainLayout.addView(tagsLayout)

        cbSearchStorage = CheckBox(this).apply {
            text = "Искать в общей памяти"
            setTextColor(Color.WHITE)
            isChecked = true
        }
        mainLayout.addView(cbSearchStorage)

        cbSearchCache = CheckBox(this).apply {
            text = "Искать в кэше (Android/data)"
            setTextColor(Color.WHITE)
            isChecked = true
        }
        mainLayout.addView(cbSearchCache)

        btnScan = Button(this).apply {
            text = "ГЛУБОКИЙ ПОИСК"
        }
        mainLayout.addView(btnScan)

        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
        }
        mainLayout.addView(progressBar)

        tvStatus = TextView(this).apply {
            text = "Приложение готово к работе"
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 16, 0, 16)
        }
        mainLayout.addView(tvStatus)

        val scrollView = ScrollView(this)
        llResultsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(llResultsContainer)
        mainLayout.addView(scrollView)

        setContentView(mainLayout)

        btnScan.setOnClickListener {
            if (!cbSearchStorage.isChecked && !cbSearchCache.isChecked) {
                Toast.makeText(this, "Выберите область поиска!", Toast.LENGTH_SHORT).show()
            } else {
                if (hasAllFilesPermission()) {
                    runSearch()
                } else {
                    requestAllFilesPermission()
                }
            }
        }
    }

    private fun hasAllFilesPermission(): Boolean = 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

    private fun requestAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            startActivity(intent)
        }
    }

    private fun runSearch() {
        llResultsContainer.removeAllViews()
        foundFilesList.clear() // Полностью очищаем старый список перед поиском
        checkedDirsCount = 0
        
        val query = etSearchInput.text.toString().trim()
        val currentTargets = if (query.isEmpty()) defaultTargetNames else listOf(query)

        btnScan.isEnabled = false
        progressBar.visibility = View.VISIBLE

        thread {
            val rootDir = Environment.getExternalStorageDirectory()

            if (cbSearchStorage.isChecked) {
                collectFiles(rootDir, currentTargets, false)
            }

            if (cbSearchCache.isChecked) {
                val androidDataDir = File(rootDir, "Android/data")
                if (androidDataDir.exists() && androidDataDir.isDirectory) {
                    collectFiles(androidDataDir, currentTargets, true)
                }
            }

            // ИСПРАВЛЕНО: Сортируем собранные файлы по размеру (от самого большого к меньшему)
            foundFilesList.sortByDescending { it.file.length() }

            // Отображаем уже отсортированные файлы на экране телефона
            runOnUiThread {
                for (item in foundFilesList) {
                    displayFileItem(item.file, item.matchName, item.isCache)
                }
                
                btnScan.isEnabled = true
                progressBar.visibility = View.GONE
                tvStatus.text = "Успешно! Найдено совпадений: ${foundFilesList.size}\nПроверено директорий: $checkedDirsCount"
                Toast.makeText(this@MainActivity, "Поиск завершён!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Сканирует папки и просто добавляет файлы во временный список
    private fun collectFiles(dir: File, targets: List<String>, scanCacheOnly: Boolean) {
        val files = dir.listFiles() ?: return
        checkedDirsCount++
        if (checkedDirsCount % 100 == 0) {
            runOnUiThread {
                tvStatus.text = "⚡ Идёт сканирование папок... [$checkedDirsCount]"
            }
        }

        for (file in files) {
            val matchByName = targets.firstOrNull { target -> file.name.contains(target, ignoreCase = true) }

            if (matchByName != null) {
                foundFilesList.add(FileResult(file, matchByName, scanCacheOnly || dir.absolutePath.contains("Android/data")))
            }
            
            if (file.isDirectory && !file.name.startsWith(".")) {
                if (!scanCacheOnly && file.name.equals("Android", ignoreCase = true)) {
                    continue
                }
                collectFiles(file, targets, scanCacheOnly)
            }
        }
    }

    // Создает визуальный красивый элемент интерфейса для конкретного файла
    private fun displayFileItem(file: File, matchByName: String, isCache: Boolean) {
        val parentName = file.parentFile?.name ?: ""
        val prefix = if (isCache) "[Кэш: $parentName]" else "[Память]"
        val fileType = file.extension.uppercase(Locale.getDefault()).ifEmpty { "FILE" }
        
        val bytes = file.length()
        val fileSize = if (bytes >= 1024 * 1024) "${bytes / (1024 * 1024)} МБ" else "${bytes / 1024} КБ"

        val tvFileItem = TextView(this@MainActivity).apply {
            text = "$prefix Найдено ($matchByName)\n📄 Тип: .$fileType | ⚖️ Вес: $fileSize\n📍 Путь: ${file.absolutePath}\n"
            textSize = 13f
            setTextColor(Color.parseColor("#00FF66"))
            setPadding(20, 20, 20, 20)
            
            val itemShape = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1E"))
                cornerRadius = 12f
                setStroke(1, Color.parseColor("#2C2C35"))
            }
            background = itemShape
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 16) }
        tvFileItem.layoutParams = params

        tvFileItem.setOnClickListener { openFolderDirectly(file) }
        llResultsContainer.addView(tvFileItem)
    }

    private fun openFolderDirectly(file: File) {
        try {
            val folder = file.parentFile ?: return
            val relativePath = folder.absolutePath
                .replace("${Environment.getExternalStorageDirectory().absolutePath}/", "")
                .replace(Environment.getExternalStorageDirectory().absolutePath, "")

            val authority = "com.android.externalstorage.documents"
            
