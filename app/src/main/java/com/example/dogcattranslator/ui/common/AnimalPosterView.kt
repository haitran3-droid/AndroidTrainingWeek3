package com.example.dogcattranslator.ui.common

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.util.LruCache
import androidx.appcompat.widget.AppCompatImageView
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Nạp ảnh assets trên luồng nền sau khi biết kích thước view.
 * Cache theo khóa và kích thước hiển thị; view bị tháo hoặc bind lại sẽ hủy yêu cầu cũ.
 * Giữ ngân sách cache 16 MiB nhưng chỉ lưu bitmap đã lấy mẫu, không giữ ảnh gốc 6 MiB.
 */
class AnimalPosterView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatImageView(context, attrs) {

    private var requestedKey: String? = null
    private var loadedRequest: String? = null
    private var loadingRequest: String? = null
    private var imageScope: CoroutineScope? = null
    private var imageJob: Job? = null

    fun show(key: String) {
        val portraitKey = key.removeSuffix("_call")
        requestedKey = if (portraitKey in animalKeys) portraitKey else key
        loadIfSized()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        imageScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        loadIfSized()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        loadIfSized()
    }

    override fun onDetachedFromWindow() {
        imageScope?.cancel()
        imageScope = null
        imageJob = null
        loadedRequest = null
        loadingRequest = null
        setImageDrawable(null)
        super.onDetachedFromWindow()
    }

    private fun loadIfSized() {
        val key = requestedKey ?: return
        val scope = imageScope ?: return
        val targetWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val targetHeight = (height - paddingTop - paddingBottom).coerceAtLeast(0)
        if (targetWidth == 0 || targetHeight == 0) return
        val request = "$key:$targetWidth:$targetHeight"
        if (loadedRequest == request || (loadingRequest == request && imageJob?.isActive == true)) {
            return
        }
        imageJob?.cancel()
        loadingRequest = request
        loadedRequest = null
        setImageDrawable(null)
        images.get(request)?.let {
            setImageBitmap(it)
            loadedRequest = request
            return
        }

        val assets = context.applicationContext.assets
        imageJob = scope.launch {
            try {
                val bitmap = withContext(decodeDispatcher) {
                    // Hàng đợi một decoder tránh cấp phát trùng khi nhiều view xin cùng ảnh.
                    images.get(request) ?: decode(assets, key, targetWidth, targetHeight).also {
                        images.put(request, it)
                    }
                }
                setImageBitmap(bitmap)
                loadedRequest = request
            } catch (error: IOException) {
                Log.w("AnimalPosterView", "Cannot load poster: $key", error)
            } catch (error: IllegalArgumentException) {
                Log.w("AnimalPosterView", "Invalid poster: $key", error)
            }
        }
    }

    companion object {
        private val soundKeys = listOf("begging", "sad", "angry", "happy", "playful", "sleepy")
        private val animalKeys = setOf("husky", "persian", "ginger", "chihuahua")
        private val legacyIcons =
            mapOf(
                "permission_art" to Rect(664, 162, 770, 229),
                "human" to Rect(52, 79, 77, 105),
            )
        private val decodeDispatcher = Dispatchers.IO.limitedParallelism(1)
        private val images =
            object : LruCache<String, Bitmap>(16 * 1024) {
                override fun sizeOf(key: String, value: Bitmap) =
                    (value.allocationByteCount / 1024).coerceAtLeast(1)
            }

        @Suppress("DEPRECATION")
        private fun decode(assets: AssetManager, key: String, width: Int, height: Int): Bitmap {
            val soundIndex = soundKeys.indexOf(key)
            val path = when {
                key in animalKeys -> "portraits/$key.png"
                soundIndex >= 0 -> "portraits/sound_dogs.png"
                else -> "mockup.png"
            }
            return assets.open(path).use { stream ->
                val decoder = requireNotNull(BitmapRegionDecoder.newInstance(stream, false))
                try {
                    val region = when {
                        key in animalKeys -> Rect(0, 0, decoder.width, decoder.height)
                        soundIndex >= 0 -> {
                            val tileWidth = decoder.width / 3
                            val tileHeight = decoder.height / 2
                            val left = (soundIndex % 3) * tileWidth
                            val top = (soundIndex / 3) * tileHeight
                            Rect(left, top, left + tileWidth, top + tileHeight)
                        }
                        else -> requireNotNull(legacyIcons[key]) { "Unknown poster key: $key" }
                    }
                    var sample = 1
                    // Giữ đủ pixel cho centerCrop, đồng thời bỏ những pixel view không cần.
                    while (region.width() / (sample * 2) >= width &&
                        region.height() / (sample * 2) >= height) {
                        sample *= 2
                    }
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    requireNotNull(decoder.decodeRegion(region, options))
                } finally {
                    decoder.recycle()
                }
            }
        }
    }
}
