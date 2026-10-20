package com.example.dogcattranslator.ui.fakecall

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.dogcattranslator.R
import com.example.dogcattranslator.data.repository.DemoMediaRepository
import com.example.dogcattranslator.databinding.FragmentFakeCallBinding
import com.example.dogcattranslator.ui.common.CameraPermissionGate

/**
 * Màn danh sách thú cưng để chọn cuộc gọi giả lập.
 *
 * Luồng: bấm card → nhớ pendingAnimal → kiểm tra/xin quyền camera → mở Incoming. Lưu ID đang chờ vào
 * Bundle để giữ lựa chọn khi tạo lại màn hình.
 *
 * Cần nhớ: Adapter chỉ báo ID; Fragment quyết định xin quyền và điều hướng.
 */
class FakeCallFragment : Fragment(R.layout.fragment_fake_call) {
    private var binding: FragmentFakeCallBinding? = null
    private var pendingAnimal = "husky"

    private val permission = CameraPermissionGate(this) { openIncoming() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = FragmentFakeCallBinding.bind(view)
        binding = ui

        pendingAnimal = savedInstanceState?.getString("pendingAnimal") ?: "husky"

        permission.attach()

        ui.animals.layoutManager = GridLayoutManager(requireContext(), 2)
        ui.animals.adapter =
            AnimalAdapter(DemoMediaRepository.animals) { id ->
                pendingAnimal = id
                permission.request()
            }
    }

    /**
     * Mở màn hình cuộc gọi đến (IncomingCallFragment) kèm theo ID con vật đã chọn qua Bundle.
     */
    private fun openIncoming() {
        if (findNavController().currentDestination?.id == R.id.fakeCallFragment)
            findNavController()
                .navigate(R.id.incomingCallFragment, bundleOf("animalId" to pendingAnimal))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pendingAnimal", pendingAnimal)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding?.animals?.adapter = null
        binding = null
        super.onDestroyView()
    }
}
