package com.example.dogcattranslator.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.util.AttributeSet
import android.util.LruCache
import androidx.appcompat.widget.AppCompatImageView

/**
 * ImageView dùng chung để hiển thị ảnh thú cưng và minh họa từ assets.
 *
 * Luồng: show(key) → tìm cache → nếu thiếu thì đọc ảnh riêng hoặc cắt ô ảnh ghép → hiển thị.
 * BitmapRegionDecoder chỉ giải mã ô cần dùng trong sound_dogs.png.
 *
 * Cần nhớ: LruCache bỏ tham chiếu ảnh ít truy cập gần đây nhất khi vượt ngân sách khoảng 16 MiB. Ảnh
 * còn được View giữ chưa thể thu hồi; byte/pixel tùy Bitmap.Config.
 */
class AnimalPosterView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatImageView(context, attrs) {

    /**
     * Nạp và hiển thị ảnh dựa trên khóa `key`.
     * @param key Khóa định danh của ảnh (vd: "husky", "persian", "begging", "human").
     */
    fun show(key: String) {
        val portraitKey = key.removeSuffix("_call")
        val cacheKey = if (portraitKey in animalKeys) portraitKey else key

        val cached = images.get(cacheKey)
        if (cached != null) {
            setImageBitmap(cached)
            return
        }

        val soundIndex = soundKeys.indexOf(key)
        val bitmap =
            when {
                soundIndex >= 0 -> loadSoundPortrait(soundIndex)
                portraitKey in animalKeys -> loadAnimalPortrait(portraitKey)
                else -> loadLegacyIcon(key)
            }
        images.put(cacheKey, bitmap)
        setImageBitmap(bitmap)
    }

    /**
     * Nạp ảnh chân dung độc lập của từng con vật trong `assets/portraits/<key>.png`.
     */
    private fun loadAnimalPortrait(key: String): Bitmap =
        context.assets.open("portraits/$key.png").use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) { "Cannot decode portrait: $key" }
        }

    /**
     * Cắt một ô ảnh nhỏ từ tấm ảnh ghép sprite sheet `portraits/sound_dogs.png`.
     * Sử dụng [BitmapRegionDecoder] để chỉ giải mã đúng ô hình cần thiết, tiết kiệm tối đa RAM.
     */
    @Suppress("DEPRECATION")
    private fun loadSoundPortrait(index: Int): Bitmap {
        context.assets.open("portraits/sound_dogs.png").use { stream ->
            val decoder = requireNotNull(BitmapRegionDecoder.newInstance(stream, false))
            try {
                // Ảnh ghép có 3 cột × 2 hàng; index xác định ô cần giải mã.
                val tileWidth = decoder.width / 3
                val tileHeight = decoder.height / 2
                val left = (index % 3) * tileWidth
                val top = (index / 3) * tileHeight
                val region = Rect(left, top, left + tileWidth, top + tileHeight)
                return requireNotNull(decoder.decodeRegion(region, null))
            } finally {
                decoder.recycle() // Luôn recycle decoder khi hoàn thành
            }
        }
    }

    /**
     * Cắt icon người hoặc tranh minh họa từ file ảnh gốc `mockup.png`.
     */
    private fun loadLegacyIcon(key: String): Bitmap {
        val region = requireNotNull(legacyIcons[key]) { "Unknown poster key: $key" }
        val source = context.assets.open("mockup.png").use(BitmapFactory::decodeStream)
        return Bitmap.createBitmap(source, region.left, region.top, region.width(), region.height())
    }

    companion object {
        private val soundKeys = listOf("begging", "sad", "angry", "happy", "playful", "sleepy")
        private val animalKeys = setOf("husky", "persian", "ginger", "chihuahua")
        private val legacyIcons =
            mapOf(
                "permission_art" to Rect(664, 162, 770, 229),
                "human" to Rect(52, 79, 77, 105),
            )

        private val images =
            // Cache dùng chung, ngân sách KiB; bỏ tham chiếu không recycle ảnh còn được View giữ.
            object : LruCache<String, Bitmap>(16 * 1024) {
                override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
            }
    }
}
