package com.example.taxiradar

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.util.concurrent.Executors

/** One serialized native engine; screenshot pixels never leave memory or go to logs. */
class ScreenOcrEngine(private val context: Context) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    @Volatile private var tess: TessBaseAPI? = null
    @Volatile private var nativeReading = false
    @Volatile private var generation = 0L
    @Volatile private var closing = false
    fun read(frame: Bitmap, preview: (ScreenOfferParser.Offer) -> Unit = {}, complete: (List<String>) -> Unit) {
        val ticket = ++generation
        // Retain Google's Latin pass, then add Russian/Romanian OCR using the same immutable frame.
        latin.process(InputImage.fromBitmap(frame, 0)).addOnCompleteListener { task ->
            // Строки по месту на экране и только в области карточки: иначе OCR подмешивает в адрес
            // надписи спроса («Cerere mare 1.2x»), отказа и карты («Stăuceni», «31.5 km · 41 min»).
            val google = if (task.isSuccessful) task.result?.let { layout(it) }.orEmpty() else ""
            if (closing || ticket != generation) { complete(emptyList()); return@addOnCompleteListener }
            val parsed = ScreenOfferParser.parse(google)
            if (parsed?.fare != null) preview(parsed)
            if (parsed != null && parsed.addresses.size == 2 && parsed.addresses.all { it.isNotBlank() } &&
                (parsed.fare != null || (parsed.multiplierKnown && parsed.multiplier > 1 && parsed.boltCategory != null))) {
                complete(listOf(google)); return@addOnCompleteListener
            }
            worker.execute {
                if (closing || ticket != generation) { main.post { complete(emptyList()) }; return@execute }
                var prepared: Bitmap? = null
                val russian = try {
                    if (tess == null) {
                        val base = File(context.filesDir, "ocr-models")
                        val data = File(base, "tessdata").apply { mkdirs() }
                        listOf("rus", "ron", "eng").forEach { lang ->
                            val target = File(data, "$lang.traineddata")
                            if (!target.exists()) context.assets.open("ocr/$lang.traineddata").use { input -> target.outputStream().use { input.copyTo(it) } }
                        }
                        val api = TessBaseAPI()
                        if (!api.init(base.absolutePath, "rus+ron+eng", TessBaseAPI.OEM_LSTM_ONLY)) {
                            api.recycle(); error("OCR language models unavailable")
                        }
                        api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT)
                        tess = api
                    }
                    prepared = prepare(frame)
                    if (closing || ticket != generation) error("Stale frame")
                    tess!!.setImage(prepared)
                    nativeReading = true
                    try { tess!!.getUTF8Text().orEmpty() }
                    finally { nativeReading = false; tess!!.clear() }
                } catch (_: Exception) { "" }
                finally { prepared?.recycle() }
                main.post { complete(if (closing || ticket != generation) emptyList() else listOf(russian, google).filter { it.isNotBlank() }) }
            }
        }
    }
    /**
     * Текст ML Kit в порядке чтения по экрану (сверху вниз, слева направо), обрезанный по карточке:
     * от ~7 строк над первой строкой «мин · km» до ~5 строк под второй. Нет двух таких строк — весь текст как есть.
     */
    private fun layout(result: com.google.mlkit.vision.text.Text): String {
        val lines = result.textBlocks.flatMap { it.lines }.filter { it.boundingBox != null && it.text.isNotBlank() }
        if (lines.isEmpty()) return result.text
        val legs = lines.filter { ScreenOfferParser.isLeg(it.text) }.sortedBy { it.boundingBox!!.top }
        val height = lines.map { it.boundingBox!!.height() }.sorted()[lines.size / 2].coerceAtLeast(8)
        val window = if (legs.size >= 2) (legs.first().boundingBox!!.top - height * 7)..(legs.last().boundingBox!!.bottom + height * 5)
            else Int.MIN_VALUE..Int.MAX_VALUE
        // Строки на одной высоте (в пределах половины строки) — слева направо.
        return lines.filter { it.boundingBox!!.centerY() in window }
            .sortedWith(compareBy({ it.boundingBox!!.centerY() / (height / 2 + 1) }, { it.boundingBox!!.left }))
            .joinToString("\n") { it.text }
    }
    fun cancelCurrent() {
        generation++
        if (nativeReading) tess?.stop()
    }
    private fun prepare(source: Bitmap): Bitmap {
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        var sum = 0L; var lo = 255; var hi = 0
        for (i in pixels.indices) {
            val p = pixels[i]; val gray = (((p shr 16) and 255) * 299 + ((p shr 8) and 255) * 587 + (p and 255) * 114) / 1000
            pixels[i] = gray; sum += gray; lo = minOf(lo, gray); hi = maxOf(hi, gray)
        }
        val invert = sum / pixels.size < 128
        for (i in pixels.indices) {
            var g = (pixels[i] - lo) * 255 / (hi - lo).coerceAtLeast(1)
            if (invert) g = 255 - g
            pixels[i] = (255 shl 24) or (g shl 16) or (g shl 8) or g
        }
        return Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
    }
    fun close() {
        closing = true; cancelCurrent(); latin.close()
        worker.execute { tess?.recycle(); tess = null }
        worker.shutdown()
    }
}
