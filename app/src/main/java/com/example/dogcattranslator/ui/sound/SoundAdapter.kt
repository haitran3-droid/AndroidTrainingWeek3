package com.example.dogcattranslator.ui.sound

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.dogcattranslator.R
import com.example.dogcattranslator.data.model.AnimalSound
import com.example.dogcattranslator.databinding.ItemAnimalSoundBinding

/**
 * Hiển thị card âm thanh và báo sound được bấm cho SoundFragment.
 *
 * Luồng: ViewModel.sounds → gắn dữ liệu vào ViewHolder → callback chọn sound. RecyclerView tái sử dụng
 * ViewHolder khi cuộn.
 *
 * Cần nhớ: Adapter không tự phát audio. Đổi selectedId chỉ cập nhật tối đa hai card đổi viền.
 */
class SoundAdapter(
    private val items: List<AnimalSound>,
    private val select: (AnimalSound) -> Unit,
) : RecyclerView.Adapter<SoundAdapter.Holder>() {

    var selectedId: String? = null
        set(value) {
            if (value == field) return
            val previousIndex = items.indexOfFirst { it.id == field }
            field = value
            val selectedIndex = items.indexOfFirst { it.id == value }

            // Chỉ cập nhật card cũ/mới hợp lệ; lần chọn đầu có thể chỉ cập nhật một card.
            if (previousIndex >= 0) notifyItemChanged(previousIndex)
            if (selectedIndex >= 0) notifyItemChanged(selectedIndex)
        }

    /**
     * ViewHolder lưu giữ các View con bên trong một Card thông qua ViewBinding.
     */
    class Holder(val ui: ItemAnimalSoundBinding) : RecyclerView.ViewHolder(ui.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemAnimalSoundBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val isSelected = item.id == selectedId
        val displayName = soundDisplayName(holder.itemView.context, item)

        holder.ui.poster.show(item.posterKey)

        // Card đã có tên cho trình đọc màn hình, nên không đọc lặp ảnh con.
        holder.ui.poster.importantForAccessibility =
            android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO

        holder.ui.label.text = displayName
        holder.ui.root.isSelected = isSelected

        holder.ui.root.strokeColor =
            ContextCompat.getColor(
                holder.itemView.context,
                if (isSelected) R.color.orange else R.color.line,
            )

        holder.ui.root.contentDescription = displayName

        holder.ui.root.setOnClickListener { select(item) }
    }
}
