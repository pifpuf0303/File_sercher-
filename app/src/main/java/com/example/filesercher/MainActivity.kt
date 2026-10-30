package com.example.filesercher
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val defaultTargetNames = listOf(
        "libil2cpp.so", "Yandere.zip", 
        "R4x", "Viento", "Spoof_lios"
    )
    private val targetGamePackage = 
        "com.herogame.gplay.lastdayrulessurvival"
    private val targetTelegramPackage = 
        "org.telegram.messenger"
       // 🔴 ЗАМЕНИ ЭТИ ЦИФРЫ НА СВОЙ ПАРОЛЬ ИЗ 100 ЗНАКОВ
    private val HARDCODED_PASSWORD = "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"

    private lateinit var etSearchInput: EditText
    private lateinit var btnScan: Button
    private lateinit var llResultsContainer: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var cbSearchStorage: CheckBox
    private lateinit var cbSearchCache: CheckBox
    private lateinit var cbSearchTelegram: CheckBox
    
    private var checkedDirsCount = 0
    private val foundFilesList = mutableListOf<FileResult>()

    data class FileResult(
        val file: File, 
        val matchName: String, 
        val typeLabel: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showActivationScreen()
    }

    private fun showActivationScreen() {
        val root = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121214"))
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64) 
        }
        root.addView(TextView(this).apply { 
            text = "Fils ME ᴘʀᴏᴊᴇｃｔ"
            t(this, "#FFD700", 22f)
            setPadding(0, 0, 0, 8) 
        })
        root.addView(TextView(this).apply { 
            text = "ВХОД В ПРИЛОЖЕНИЕ"
            t(this, "#8E8E93", 12f)
            setPadding(0, 0, 0, 48) 
        })
        val etKey = EditText(this).apply { 
            hint = "Введите 100-значный пароль..."
            setHintTextColor(Color.parseColor("#555555"))
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        root.addView(etKey)
        root.addView(Button(this).apply { 
            text = "ВОЙТИ"
            setBackgroundColor(Color.parseColor("#FFD700"))
            setTextColor(Color.BLACK)
            setOnClickListener {
                val entered = etKey.text.toString().trim()
                if (entered == HARDCODED_PASSWORD) { 
                    initMainSearchUi() 
                } else {
                    Toast.makeText(
                        this@MainActivity, 
                        "Неверный пароль доступа!", 
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } 
        })
        setContentView(root)
    }
        private fun initMainSearchUi() {
        val mainLayout = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121214"))
            setPadding(32, 32, 32, 32) 
        }
        mainLayout.addView(TextView(this).apply { 
            text = "Fils ME ᴘʀᴏᴊᴇｃｔ"
            t(this, "#FFD700", 20f)
            setPadding(0, 10, 0, 20) 
        })
        etSearchInput = EditText(this).apply { 
            hint = "Введите имя файла..."
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
            tagsLayout.addView(Button(this).apply { 
                text = tag; textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
                ).apply { setMargins(4, 0, 4, 0) }
                setOnClickListener { 
                    etSearchInput.setText(tag)
                    etSearchInput.setSelection(tag.length) 
                } 
            })
        }
        mainLayout.addView(tagsLayout)
        cbSearchStorage = CheckBox(this).apply { 
            text = "Искать в общей памяти"
            setTextColor(Color.WHITE); isChecked = true 
        }
        mainLayout.addView(cbSearchStorage)
        cbSearchCache = CheckBox(this).apply { 
            text = "Искать в кэше игры"
            setTextColor(Color.WHITE); isChecked = true 
        }
        mainLayout.addView(cbSearchCache)
        cbSearchTelegram = CheckBox(this).apply { 
            text = "Искать в Telegram"
            setTextColor(Color.WHITE); isChecked = true 
        }
        mainLayout.addView(cbSearchTelegram)
        btnScan = Button(this).apply { text = "ГЛУБОКИЙ ПОИСК" }
        mainLayout.addView(btnScan)
        progressBar = ProgressBar(this).apply { visibility = View.GONE }
        mainLayout.addView(progressBar)
        tvStatus = TextView(this).apply { 
            text = "Приложение готово"; textSize = 14f
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
            val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) 
                Environment.isExternalStorageManager() else true
            if (hasPerm) { runSearch() } else { 
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            } 
        }
    }

    private fun runSearch() {
        llResultsContainer.removeAllViews()
        foundFilesList.clear()
        checkedDirsCount = 0
        val query = etSearchInput.text.toString().trim()
        val currentTargets = if (query.isEmpty()) defaultTargetNames else listOf(query)
        btnScan.isEnabled = false
        progressBar.visibility = View.VISIBLE
        thread {
            val rootDir = Environment.getExternalStorageDirectory()
            if (cbSearchStorage.isChecked) { 
                collectFiles(rootDir, currentTargets, "[Память]") 
            }
            if (cbSearchCache.isChecked) {
                val gameDataDir = File(rootDir, "Android/data/$targetGamePackage")
                if (gameDataDir.exists() && gameDataDir.isDirectory) { 
                    collectFiles(gameDataDir, currentTargets, "[Кэш игры]") 
                }
            }
            if (cbSearchTelegram.isChecked) {
                val tgDownloadDir = File(rootDir, "Download/Telegram")
                if (tgDownloadDir.exists() && tgDownloadDir.isDirectory) { 
                    collectFiles(tgDownloadDir, currentTargets, "[ТГ: Загрузки]") 
                }
                val tgCacheDir = File(rootDir, "Android/data/$targetTelegramPackage")
                if (tgCacheDir.exists() && tgCacheDir.isDirectory) { 
                    collectFiles(tgCacheDir, currentTargets, "[ТГ: Кэш чатов]") 
                }
            }
            foundFilesList.sortByDescending { it.file.length() }
            runOnUiThread {
                for (item in foundFilesList) { 
                    displayFileItem(item.file, item.matchName, item.typeLabel) 
                }
                btnScan.isEnabled = true
                progressBar.visibility = View.GONE
                tvStatus.text = "Успешно! Найдено совпадений: ${foundFilesList.size}"
            }
        }
    }

    private fun collectFiles(dir: File, targets: List<String>, label: String) {
        val files = dir.listFiles() ?: return
        checkedDirsCount++
        for (file in files) {
            val matchByName = targets.firstOrNull { 
                file.name.contains(it, ignoreCase = true) 
            }
            if (matchByName != null) { 
                foundFilesList.add(FileResult(file, matchByName, label)) 
            }
            if (file.isDirectory && !file.name.startsWith(".")) {
                if (label == "[Память]" && file.name.equals("Android", ignoreCase = true)) { 
                    continue 
                }
                collectFiles(file, targets, label)
            }
        }
    }
        private fun displayFileItem(file: File, matchByName: String, typeLabel: String) {
        val fileType = file.extension.uppercase(Locale.getDefault()).ifEmpty { "FILE" }
        val bytes = file.length()
        val fileSize = if (bytes >= 1024 * 1024) "${bytes / (1024 * 1024)} МБ" else "${bytes / 1024} КБ"
        val itemLayout = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply { 
                setColor(Color.parseColor("#1A1A1E"))
                cornerRadius = 12f
                setStroke(1, Color.parseColor("#2C2C35")) 
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
            setOnClickListener {
                val clipManager = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipManager.setPrimaryClip(android.content.ClipData.newPlainText("File Path", file.absolutePath))
                Toast.makeText(context, "📍 Путь скопирован! Открываю проводник...", Toast.LENGTH_SHORT).show()
                try {
                    val folder = file.parentFile ?: return@setOnClickListener
                    val baseStorage = Environment.getExternalStorageDirectory().absolutePath
                    val relativePath = folder.absolutePath.replace("$baseStorage/", "").replace(baseStorage, "")
                    val encPath = Uri.encode(if (relativePath.isEmpty() || relativePath == folder.absolutePath) "primary:" else "primary:$relativePath")
                    val uri = Uri.parse("content://com.android.externalstorage.documents/document/$encPath")
                    startActivity(Intent(Intent.ACTION_VIEW).apply { 
                        setDataAndType(uri, "vnd.android.document/directory")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK) 
                    })
                } catch (e: Exception) {}
            }
        }
        itemLayout.addView(TextView(this@MainActivity).apply { 
            text = "$typeLabel Совпадение: ($matchByName)\n📄 Тип: .$fileType | ⚖️ Вес: $fileSize\n📍 Путь: ${file.absolutePath}\n"
            textSize = 13f; setTextColor(Color.parseColor("#00FF66")) 
        })
        if (typeLabel != "[Кэш игры]") {
            val buttonsLayout = LinearLayout(this@MainActivity).apply { 
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END 
            }
            buttonsLayout.addView(Button(this@MainActivity).apply { 
                text = "ИНЖЕКТ"; setBackgroundColor(Color.parseColor("#FFD700"))
                setTextColor(Color.BLACK); textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setPadding(32, 16, 32, 16)
                setOnClickListener { 
                    thread { 
                        val success = injectFileToGame(file)
                        runOnUiThread { 
                            if (success) Toast.makeText(context, "Файл скопирован в кэш!", Toast.LENGTH_SHORT).show() 
                        } 
                    } 
                } 
            })
            itemLayout.addView(buttonsLayout)
        }
        llResultsContainer.addView(itemLayout)
    }

    private fun injectFileToGame(sourceFile: File): Boolean {
        try {
            val destDir = File(Environment.getExternalStorageDirectory(), "Android/data/$targetGamePackage/files")
            if (!destDir.exists()) destDir.mkdirs()
            val destFile = File(destDir, sourceFile.name)
            FileInputStream(sourceFile).use { input -> 
                FileOutputStream(destFile).use { output -> 
                    val buffer = ByteArray(1024)
                    var len: Int
                    while (input.read(buffer).also { len = it } > 0) { 
                        output.write(buffer, 0, len) 
                    } 
                } 
            }
            return true
        } catch (e: Exception) { return false }
    }

    private fun t(tv: TextView, c: String, s: Float) { 
        tv.apply { 
            setTextColor(Color.parseColor(c))
            textSize = s
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER 
        } 
    }
}
