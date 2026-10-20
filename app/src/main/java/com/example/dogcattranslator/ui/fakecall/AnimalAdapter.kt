package com.example.dogcattranslator.ui.fakecall

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dogcattranslator.R
import com.example.dogcattranslator.data.model.Animal
import com.example.dogcattranslator.databinding.ItemAnimalBinding

/**
 * Hiển thị card thú cưng và báo ID được bấm cho FakeCallFragment.
 *
 * Luồng: Animal → ảnh/tên/nút gọi → callback onCall(animal.id). Cả nút gọi và nền card đều kích hoạt
 * callback.
 *
 * Cần nhớ: Adapter không xin quyền hay chuyển màn hình; đó là việc của Fragment.
 */
class AnimalAdapter(
    private val animals: List<Animal>,
    private val onCall: (String) -> Unit,
) : RecyclerView.Adapter<AnimalAdapter.Holder>() {
    class Holder(val ui: ItemAnimalBinding) : RecyclerView.ViewHolder(ui.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val ui = ItemAnimalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(ui)
    }

    override fun getItemCount() = animals.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val animal = animals[position]
        holder.ui.poster.show(animal.posterKey)
        holder.ui.poster.contentDescription = animal.name
        holder.ui.name.text = animal.name

        holder.ui.callButton.contentDescription =
            "${holder.itemView.context.getString(R.string.call)}: ${animal.name}"

        holder.ui.callButton.setOnClickListener { onCall(animal.id) }
        holder.ui.root.setOnClickListener { onCall(animal.id) }
    }
}
