package com.example.dogcattranslator.ui.fakecall

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.example.dogcattranslator.R
import com.example.dogcattranslator.data.repository.DemoMediaRepository
import com.example.dogcattranslator.databinding.FragmentIncomingCallBinding
import com.example.dogcattranslator.ui.common.CameraPermissionGate

/**
 * Màn cuộc gọi đến giả lập với hai nút Chấp nhận/Từ chối.
 *
 * Luồng: nhận animalId → tra Animal → Từ chối về list; Chấp nhận kiểm tra quyền rồi mở InCall.
 *
 * Cần nhớ: popUpTo(incoming, inclusive=true) xóa Incoming khỏi lịch sử để Back/End từ InCall không quay
 * lại cuộc gọi đến vừa chấp nhận.
 */
class IncomingCallFragment : Fragment(R.layout.fragment_incoming_call) {
    private var binding: FragmentIncomingCallBinding? = null
    private val permission = CameraPermissionGate(this) { accept() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = FragmentIncomingCallBinding.bind(view)
        binding = ui
        permission.attach()

        val id = arguments?.getString("animalId") ?: "husky"
        val animal = DemoMediaRepository.animal(id)

        ui.poster.show("${animal.posterKey}_call")
        ui.poster.contentDescription = animal.name
        ui.name.text = animal.name

        ui.declineButton.setOnClickListener { findNavController().popBackStack() }

        ui.acceptButton.setOnClickListener { permission.request() }
    }

    /**
     * Bắt đầu cuộc gọi video: Chuyển tới InCallFragment và xóa IncomingCallFragment khỏi BackStack.
     */
    private fun accept() {
        if (findNavController().currentDestination?.id == R.id.incomingCallFragment) {
            findNavController()
                .navigate(
                    R.id.inCallFragment,
                    bundleOf("animalId" to (arguments?.getString("animalId") ?: "husky")),
                    // Xóa Incoming để Back/End từ InCall trở về list.
                    navOptions { popUpTo(R.id.incomingCallFragment) { inclusive = true } },
                )
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
