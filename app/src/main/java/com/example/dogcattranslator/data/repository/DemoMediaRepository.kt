package com.example.dogcattranslator.data.repository

import com.example.dogcattranslator.R
import com.example.dogcattranslator.data.model.*

/**
 * Cung cấp dữ liệu thú cưng và âm thanh đóng gói cùng app.
 *
 * Luồng: màn hình/ViewModel lấy danh sách hoặc tìm theo ID; Home tìm sound theo loài và mức peak.
 * Tên/ID ở đây, ảnh trong assets, audio/video trong res/raw.
 *
 * Cần nhớ: object tạo một instance dùng chung khi truy cập lần đầu. Repository gom việc lấy dữ liệu để
 * bên gọi không cần biết chi tiết lưu trữ.
 */
object DemoMediaRepository {
    /**
     * Danh sách các con vật xuất hiện trong màn hình Fake Call (Cuộc gọi giả lập).
     * Tên nằm trong dữ liệu Kotlin, posterKey tra ảnh ở assets/portraits, video nằm ở res/raw.
     */
    val animals =
        listOf(
            Animal("husky", "Husky Ngao", AnimalType.DOG, "husky", videoResId = R.raw.husky_call),
            Animal(
                "persian",
                "Persian Longhair",
                AnimalType.CAT,
                "persian",
                videoResId = R.raw.persian_call,
            ),
            Animal("ginger", "Ginger", AnimalType.CAT, "ginger", videoResId = R.raw.ginger_call),
            Animal(
                "chihuahua",
                "Chihuahua",
                AnimalType.DOG,
                "chihuahua",
                videoResId = R.raw.chihuahua_call,
            ),
        )

    /**
     * Danh sách tất cả các âm thanh mẫu của Chó và Mèo.
     * Được gán kèm [AmplitudeLevel] để phục vụ logic "dịch tiếng người sang tiếng động vật"
     * dựa trên độ to nhỏ của giọng nói người dùng.
     */
    private val sounds =
        listOf(
            AnimalSound(
                "dog_begging",
                AnimalType.DOG,
                R.string.begging,
                "begging",
                audioResId = R.raw.dog_begging,
                amplitudeLevel = AmplitudeLevel.MEDIUM,
            ),
            AnimalSound(
                "dog_sad",
                AnimalType.DOG,
                R.string.sad,
                "sad",
                audioResId = R.raw.dog_sad,
                amplitudeLevel = AmplitudeLevel.LOW,
            ),
            AnimalSound(
                "dog_angry",
                AnimalType.DOG,
                R.string.angry,
                "angry",
                audioResId = R.raw.dog_angry,
                amplitudeLevel = AmplitudeLevel.HIGH,
            ),
            AnimalSound(
                "dog_happy",
                AnimalType.DOG,
                R.string.happy,
                "happy",
                audioResId = R.raw.dog_happy,
            ),
            AnimalSound(
                "dog_playful",
                AnimalType.DOG,
                R.string.playful,
                "playful",
                audioResId = R.raw.dog_playful,
            ),
            AnimalSound(
                "dog_sleepy",
                AnimalType.DOG,
                R.string.sleepy,
                "sleepy",
                audioResId = R.raw.dog_sleepy,
            ),
            AnimalSound(
                "cat_sad",
                AnimalType.CAT,
                R.string.sad,
                "persian",
                audioResId = R.raw.cat_sad,
                amplitudeLevel = AmplitudeLevel.LOW,
            ),
            AnimalSound(
                "cat_happy",
                AnimalType.CAT,
                R.string.happy,
                "ginger",
                audioResId = R.raw.cat_happy,
                amplitudeLevel = AmplitudeLevel.MEDIUM,
            ),
            AnimalSound(
                "cat_angry",
                AnimalType.CAT,
                R.string.angry,
                "persian",
                audioResId = R.raw.cat_angry,
                amplitudeLevel = AmplitudeLevel.HIGH,
            ),
        )

    /**
     * Tìm con vật theo id không null. Nếu ID không khớp, lấy phần tử đầu của danh sách demo.
     * Fallback này dựa vào danh sách animals luôn có ít nhất một phần tử.
     */
    fun animal(id: String) = animals.firstOrNull { it.id == id } ?: animals.first()

    /**
     * Lấy toàn bộ danh sách âm thanh có trong hệ thống.
     */
    fun sounds() = sounds

    /**
     * Lọc danh sách âm thanh theo loài động vật (Chó hoặc Mèo).
     */
    fun sounds(type: AnimalType) = sounds.filter { it.animalType == type }

    /**
     * Tìm âm thanh tương ứng với loài vật và mức âm lượng thu được.
     * Ví dụ: Nếu người dùng chọn Mèo và hét to (HIGH), hàm sẽ trả về âm thanh `cat_angry`.
     */
    fun soundFor(type: AnimalType, level: AmplitudeLevel) = sounds.firstOrNull {
        it.animalType == type && it.amplitudeLevel == level
    }
}
