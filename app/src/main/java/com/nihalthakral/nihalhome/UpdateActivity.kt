package com.nihalthakral.nihalhome

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateActivity : ComponentActivity() {

    private lateinit var buttonUpdate: Button
    private lateinit var buttonClose: Button
    private lateinit var progressDownload: ProgressBar
    private lateinit var textStatus: TextView

    private var downloadJob: Job? = null
    private var installerLaunched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update)

        buttonUpdate = findViewById(R.id.buttonUpdate)
        buttonClose = findViewById(R.id.buttonClose)
        progressDownload = findViewById(R.id.progressDownload)
        textStatus = findViewById(R.id.textDownloadStatus)

        applyHindiStaticText()

        UpdateChecker.clearDownloadedFiles(this)

        buttonUpdate.setOnClickListener {
            onUpdateClicked()
        }

        buttonClose.setOnClickListener {
            onCloseClicked()
        }

        applyResponsiveButtonTextSize(buttonUpdate, buttonClose)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onCloseClicked()
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        downloadJob?.cancel()
        UpdateChecker.clearDownloadedFiles(this)
        resetIdleUi()
    }

    override fun onResume() {
        super.onResume()
        installerLaunched = false
    }

    override fun onStop() {
        super.onStop()
        downloadJob?.cancel()
        if (!installerLaunched) {
            UpdateChecker.clearDownloadedFiles(this)
        }
        resetIdleUi()
    }

    private fun applyHindiStaticText() {
        if (!LocalizationHelper.isHindiSelected(this)) return

        buttonUpdate.text = getString(R.string.update_button_hi)
        buttonClose.text = getString(R.string.action_close_hi)
    }

    private fun pick(resId: Int, hiResId: Int): Int {
        return if (LocalizationHelper.isHindiSelected(this)) hiResId else resId
    }

    private fun onUpdateClicked() {
        if (downloadJob?.isActive == true) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        if (downloadedFile().exists()) {
            installApk()
        } else {
            startDownload()
        }
    }

    private fun onCloseClicked() {
        downloadJob?.cancel()
        UpdateChecker.clearDownloadedFiles(this)
        UpdateChecker.updateDismissed = true

        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    private fun downloadedFile(): File {
        return File(UpdateChecker.updateDirectory(this), UpdateChecker.APK_FILE_NAME)
    }

    private fun startDownload() {
        setDownloading()

        downloadJob = lifecycleScope.launch {
            val completed = withContext(Dispatchers.IO) { downloadApk() }
            resetIdleUi()

            if (completed) {
                installApk()
            } else {
                textStatus.text = getString(pick(R.string.update_failed, R.string.update_failed_hi))
                textStatus.visibility = View.VISIBLE
            }
        }
    }

    private fun CoroutineScope.downloadApk(): Boolean {
        val directory = UpdateChecker.updateDirectory(applicationContext)
        directory.mkdirs()
        val partFile = File(directory, UpdateChecker.PART_FILE_NAME)
        val finalFile = File(directory, UpdateChecker.APK_FILE_NAME)
        partFile.delete()
        finalFile.delete()

        var connection: HttpURLConnection? = null
        try {
            connection = URL(UpdateChecker.APK_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept-Encoding", "identity")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return false

            val total = connection.contentLengthLong

            connection.inputStream.use { input ->
                partFile.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var downloaded = 0L
                    var lastPercent = -1

                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break

                        output.write(buffer, 0, read)
                        downloaded += read

                        if (total > 0) {
                            val percent = (downloaded * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                runOnUiThread { showProgress(percent) }
                            }
                        }
                    }
                }
            }

            if (total > 0 && partFile.length() != total) return false

            return partFile.renameTo(finalFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        } finally {
            connection?.disconnect()
            partFile.delete()
        }
    }

    private fun installApk() {
        val file = downloadedFile()
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(this, "$packageName.apkshare", file)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        installerLaunched = true
        startActivity(intent)
    }

    private fun setDownloading() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        buttonUpdate.isEnabled = false
        buttonUpdate.alpha = 0.4f

        progressDownload.isIndeterminate = true
        progressDownload.visibility = View.VISIBLE

        textStatus.text = getString(pick(R.string.update_downloading_plain, R.string.update_downloading_plain_hi))
        textStatus.visibility = View.VISIBLE
    }

    private fun showProgress(percent: Int) {
        progressDownload.isIndeterminate = false
        progressDownload.progress = percent
        textStatus.text = getString(pick(R.string.update_downloading, R.string.update_downloading_hi), percent)
    }

    private fun resetIdleUi() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        buttonUpdate.isEnabled = true
        buttonUpdate.alpha = 1f

        progressDownload.visibility = View.INVISIBLE
        textStatus.visibility = View.INVISIBLE
    }

    private fun applyResponsiveButtonTextSize(vararg buttons: Button) {
        val root = buttons[0].rootView
        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val minHeight = buttons.minOf { it.height }
                if (minHeight <= 0) return

                root.viewTreeObserver.removeOnGlobalLayoutListener(this)

                var textSizePx = minHeight * 0.42f

                buttons.forEach { button ->
                    val availableWidth =
                        (button.width - button.paddingLeft - button.paddingRight).toFloat()
                    if (availableWidth <= 0f) return@forEach

                    val paint = button.paint
                    var size = textSizePx
                    paint.textSize = size
                    while (paint.measureText(button.text.toString()) > availableWidth && size > 1f) {
                        size -= 1f
                        paint.textSize = size
                    }
                    if (size < textSizePx) textSizePx = size
                }

                buttons.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx) }

                buttons.forEach { button -> centerTextVertically(button) }
            }
        })
    }

    private fun centerTextVertically(button: Button) {
        val text = button.text?.toString().orEmpty()
        if (text.isEmpty()) return

        val paint = android.text.TextPaint(button.paint)
        val fm = paint.fontMetrics

        val width = kotlin.math.ceil(paint.measureText(text)).toInt().coerceAtLeast(1)
        val top = kotlin.math.floor(fm.top).toInt()
        val bottom = kotlin.math.ceil(fm.bottom).toInt()
        val height = (bottom - top).coerceAtLeast(1)

        val bitmap = android.graphics.Bitmap.createBitmap(
            width, height, android.graphics.Bitmap.Config.ALPHA_8
        )
        val canvas = android.graphics.Canvas(bitmap)
        val baselineY = -top.toFloat()
        canvas.drawText(text, 0f, baselineY, paint)

        var inkTop = -1
        var inkBottom = -1
        val row = IntArray(width)
        for (y in 0 until height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            if (row.any { (it ushr 24) != 0 }) {
                if (inkTop == -1) inkTop = y
                inkBottom = y
            }
        }
        bitmap.recycle()
        if (inkTop == -1) return

        val inkCenter = (inkTop + inkBottom) / 2f + top

        val fontMetricCenter = (fm.ascent + fm.descent) / 2f
        val shiftDown = fontMetricCenter - inkCenter

        val left = button.paddingLeft
        val right = button.paddingRight
        if (shiftDown > 0f) {
            button.setPadding(left, (shiftDown * 2f).toInt(), right, 0)
        } else {
            button.setPadding(left, 0, right, (-shiftDown * 2f).toInt())
        }
    }
}
