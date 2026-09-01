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
import android.widget.HorizontalScrollView
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
    
    // Переключатели области поиска
    private lateinit var cbSearchStorage: CheckBox
    private lateinit var cbSearchCache: CheckBox
    
    private val defaultTargetNames = listOf("libil2cpp.so", "Yandere.zip", "R4x", "Viento", "Spoof_lios")
    private var checkedDirsCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121214"))
            setPadding(32, 32, 32, 32)
        }

        // 1. Красивый золотой заголовок ME PROJECT
        val tvTitle = TextView(this).apply {
            text = "ME PROJECT : SEARCHER"
            textSize = 20f
            setTextColor(Color.parseColor("#FFD700"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 20)
        }
        mainLayout.addView(tvTitle)

        // 2. Поле ввода
        etSearchInput = EditText(this).apply {
            hint = "Введите имя файла для поиска..."
            setHintTextColor(Color.parseColor("#666666"))
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        mainLayout.addView(etSearchInput)

        // 3. Горизонтальная лента быстрых тегов
        val tagsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
        }
        val tags = listOf(".zip", ".so", ".apk", "Telegram")
        for (tag in tags) {
            val btnTag = Button(this).apply {
                text = tag
                textSize = 12f
                // Устанавливаем небольшие отступы для кнопок-тегов
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 10, 0) }
                layoutParams = params
                setOnClickListener {
                    etSearchInput.setText(tag)
                    etSearchInput.setSelection(tag.length) // Переносим курсор в конец текста
                }
            }
            tagsLayout.addView(btnTag)
        }
        val scrollTags = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        scrollTags.addView(tagsLayout)
        mainLayout.addView(scrollTags)

        // 4. Фильтры области поиска (Чекбоксы)
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

        // 5. Наша классическая надежная кнопка сканирования
        btnScan = Button(this).apply {
            text = "ГЛУБОКИЙ ПОИСК"
        }
        mainLayout.addView(btnScan)

        // Индикатор прогресса
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
        }
        mainLayout.addView(progressBar)

        // Статус процесса
        tvStatus = TextView(this).apply {
            text = "Приложение готово к работе"
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 16, 0, 16)
        }
        mainLayout.addView(tvStatus)

        // Область прокрутки результатов
        val scrollView = ScrollView(this)
        llResultsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(llResultsContainer)
        mainLayout.addView(scrollView)

        setContentView(mainLayout)

        btnScan.setOnClickListener {
            // Проверяем, выбран ли хоть один фильтр перед поиском
            if (!cbSearchStorage.isChecked && !cbSearchCache.isChecked) {
                Toast.makeText(this, "Выберите хотя бы одну область поиска!", Toast.LENGTH_SHORT).show()
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
        checkedDirsCount = 0
        
        val query = etSearchInput.text.toString().trim()
        val currentTargets = if (query.isEmpty()) defaultTargetNames else listOf(query)

        btnScan.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Подготовка поискового движка..."

        thread {
            val rootDir = Environment.getExternalStorageDirectory()

            // 1. Сканируем общую память, если чекбокс включен
            if (cbSearchStorage.isChecked) {
                searchFiles(rootDir, currentTargets, scanCacheOnly = false)
            }

            // 2. Сканируем скрытый кэш Android/data, если чекбокс включен
            if (cbSearchCache.isChecked) {
                val androidDataDir = File(rootDir, "Android/data")
                if (androidDataDir.exists() && androidDataDir.isDirectory) {
                    searchFiles(androidDataDir, currentTargets, scanCacheOnly = true)
                }
            }

            runOnUiThread {
                btnScan.isEnabled = true
                progressBar.visibility = View.GONE
                tvStatus.text = "Успешно! Найдено совпадений: ${llResultsContainer.childCount}\nПроверено директорий: $checkedDirsCount"
                Toast.makeText(this@MainActivity, "Поиск завершён!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun searchFiles(dir: File, targets: List<String>, scanCacheOnly: Boolean) {
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
                val parentName = dir.parentFile?.name ?: ""
                val prefix = if (dir.absolutePath.contains("Android/data") || scanCacheOnly) "[Кэш: $parentName]" else "[Память]"
                val fileType = file.extension.uppercase(Locale.getDefault()).ifEmpty { "FILE" }
                val fileSize = formatFileSize(file.length())

                runOnUiThread {
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
            }
            
            if (file.isDirectory && !file.name.startsWith(".")) {
                // Если мы сканируем общую память, то принудительно пропускаем Android/data, 
                // так как для неё у нас есть выделенный второй чекбокс (чтобы папки не дублировались)
                if (!scanCacheOnly && file.name.equals("Android", ignoreCase = true)) {
                    continue
                }
                searchFiles(file, targets, scanCacheOnly)
            }
        }
    }

    
