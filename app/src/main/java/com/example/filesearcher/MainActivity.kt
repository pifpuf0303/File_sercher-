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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var etSearchInput: EditText
    private lateinit var btnScan: Button
    private lateinit var llResultsContainer: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    
    private val defaultTargetNames = listOf("libil2cpp.so", "Yandere.zip", "R4x", "Viento", "Spoof_lios")
    private val textExtensions = listOf("txt", "log", "cfg", "json", "xml", "ini", "yaml", "yml", "conf")
    
    // Потокобезопасный счётчик проверенных папок
    private val checkedDirsCount = AtomicInteger(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Главный контейнер с тёмным футуристичным фоном
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121214")) // Тёмно-угольный цвет
            setPadding(40, 50, 40, 40)
        }

        // Заголовок приложения в стиле ME PROJECT
        val tvTitle = TextView(this).apply {
            text = "ME PROJECT : SEARCHER"
            textSize = 20f
            setTextColor(Color.parseColor("#FFD700")) // Золотой цвет
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        mainLayout.addView(tvTitle)

        // Красивое закруглённое поле ввода
        val inputShape = GradientDrawable().apply {
            setColor(Color.parseColor("#1A1A1E")) // Чуть светлее фона
            cornerRadius = 20f
            setStroke(2, Color.parseColor("#33333C")) // Тёмно-серая рамка
        }
        etSearchInput = EditText(this).apply {
            hint = "Введите имя файла или текст внутри..."
            setHintTextColor(Color.parseColor("#666666"))
            setTextColor(Color.WHITE)
            textSize = 15f
            background = inputShape
            setPadding(30, 30, 30, 30)
        }
        mainLayout.addView(etSearchInput)

        // Разделитель
        val spacer = View(this).apply { minimumHeight = 30 }
        mainLayout.addView(spacer)

        // Стильная золотая кнопка с закруглёнными углами
        val buttonShape = GradientDrawable().apply {
            setColor(Color.parseColor("#FFD700")) // Золотой цвет кнопки
            cornerRadius = 25f
        }
        btnScan = Button(this).apply {
            text = "ГЛУБОКИЙ ПОИСК ВЕЗДЕ"
            setTextColor(Color.BLACK)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 15f
            background = buttonShape
            setPadding(0, 25, 0, 25)
        }
        mainLayout.addView(btnScan)

        // Горизонтальный индикатор прогресса (ProgressBar)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            setPadding(0, 20, 0, 10)
        }
        mainLayout.addView(progressBar)

        // Статусная строка для вывода живого счётчика папок
        tvStatus = TextView(this).apply {
            text = "Приложение готово к работе"
            textSize = 13f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 20, 0, 20)
        }
        mainLayout.addView(tvStatus)

        // Контейнер для списков результатов
        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
        }
        llResultsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(llResultsContainer)
        mainLayout.addView(scrollView)

        setContentView(mainLayout)

        btnScan.setOnClickListener {
            if (hasAllFilesPermission()) {
                runSearch()
            } else {
                requestAllFilesPermission()
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
        checkedDirsCount.set(0) // Сбрасываем счётчик папок
        
        val query = etSearchInput.text.toString().trim()
        val currentTargets = if (query.isEmpty()) defaultTargetNames else listOf(query)

        btnScan.isEnabled = false
        btnScan.alpha = 0.5f
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true // Бегущая полоса анимации

        thread {
            val rootDir = Environment.getExternalStorageDirectory()
            searchFiles(rootDir, currentTargets, query)

            val androidDataDir = File(rootDir, "Android/data")
            if (androidDataDir.exists() && androidDataDir.isDirectory) {
                searchFiles(androidDataDir, currentTargets, query)
            }

            runOnUiThread {
                btnScan.isEnabled = true
                btnScan.alpha = 1.0f
                progressBar.visibility = View.GONE
                
                if (llResultsContainer.childCount == 0) {
                    tvStatus.text = "Ничего не найдено. Проверено папок: ${checkedDirsCount.get()}"
                } else {
                    tvStatus.text = "Успешно! Найдено совпадений: ${llResultsContainer.childCount}\nПроверено папок в системе: ${checkedDirsCount.get()}"
                }
                Toast.makeText(this, "Поиск успешно завершён!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun searchFiles(dir: File, targets: List<String>, originalQuery: String) {
        val files = dir.listFiles() ?: return
        
        // Увеличиваем счётчик при входе в каждую новую папку
        val currentCount = checkedDirsCount.incrementAndGet()
        if (currentCount % 50 == 0) { // Обновляем текст на экране каждые 50 папок, чтобы интерфейс не лагал
            runOnUiThread {
                tvStatus.text = "⚡ Идёт сканирование... Проверено папок: $currentCount"
            }
        }

        for (file in files) {
            var isMatched = false
            var matchReason = ""

            val matchByName = targets.firstOrNull { target -> file.name.contains(target, ignoreCase = true) }

            if (matchByName != null) {
                isMatched = true
                matchReason = "Имя: $matchByName"
            } else if (originalQuery.isNotEmpty() && file.isFile && textExtensions.contains(file.extension.lowercase(Locale.getDefault()))) {
                if (file.length() < 5 * 1024 * 1024) {
                    try {
                        val content = file.readText(Charsets.UTF_8)
                        if (content.contains(originalQuery, ignoreCase = true)) {
                            isMatched = true
                            matchReason = "Текст внутри"
                        }
                    } catch (e: Exception) {}
                }
            }

            if (isMatched) {
                val parentName = dir.parentFile?.name ?: ""
                val prefix = if (dir.absolutePath.contains("Android/data")) "[Кэш: $parentName]" else "[Память]"
                val fileType = file.extension.uppercase(Locale.getDefault()).ifEmpty { "БЕЗ РАСШИРЕНИЯ" }
                val fileSize = formatFileSize(file.length())

                runOnUiThread {
                    // Стильная тёмная карточка для каждого найденного файла
                    val tvFileItem = TextView(this@MainActivity).apply {
                        text = "$prefix Найдено ($matchReason)\n📄 Тип: .$fileType | ⚖️ Вес: $fileSize\n📍 Путь: ${file.absolutePath}\n"
                        textSize = 13f
                        setTextColor(Color.parseColor("#00FF66")) // Ярко-зелёный неоновый текст для совпадений
                        setPadding(25, 25, 25, 25)
                        
                        // Задний фон карточки файла с рамкой
                        val itemShape = GradientDrawable().apply {
                            setColor(Color.parseColor("#1A1A1E"))
                            cornerRadius = 15f
                            setStroke(1, Color.parseColor("#2C2C35"))
                        }
                        background = itemShape
                    }

                    // Отступ между карточками файлов
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, 20) }
                    tvFileItem.layoutParams = params

                    tvFileItem.setOnClickListener { openFolderDirectly(file) }
                    llResultsContainer.addView(tvFileItem)
                }
            }
            
            if (file.isDirectory && !file.name.startsWith(".")) {
                
