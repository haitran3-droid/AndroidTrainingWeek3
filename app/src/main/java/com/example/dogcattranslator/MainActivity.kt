package com.example.dogcattranslator

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.dogcattranslator.databinding.ActivityMainBinding

/**
 * Khung chứa các màn hình và thanh tab của app.
 *
 * Luồng: activity_main.xml chứa NavHostFragment → NavController chuyển Fragment theo tab. Màn
 * Incoming/InCall ẩn tab và thanh hệ thống.
 *
 * Cần nhớ: giao diện tràn viền vẫn phải chừa phần đệm (insets) để nội dung cần thiết tránh thanh hệ
 * thống.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        var callScreen = false
        val systemBars = WindowCompat.getInsetsController(window, binding.root)

        // Màn thường chừa insets; màn gọi phủ hình nền nên bỏ phần đệm trên/dưới.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
            view.setPadding(
                bars.left,
                if (callScreen) 0 else bars.top,
                bars.right,
                if (callScreen) 0 else bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }

        val host = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        binding.bottomNav.setupWithNavController(host.navController)

        host.navController.addOnDestinationChangedListener { _, destination, _ ->
            callScreen =
                destination.id == R.id.incomingCallFragment || destination.id == R.id.inCallFragment

            binding.bottomNav.isVisible = !callScreen

            binding.root.setBackgroundResource(if (callScreen) R.color.ink else R.color.white)

            if (callScreen) {
                systemBars.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                systemBars.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                systemBars.show(WindowInsetsCompat.Type.systemBars())
            }

            // Đổi màn gọi làm đổi cách chừa phần đệm, nên yêu cầu tính lại insets.
            ViewCompat.requestApplyInsets(binding.root)
        }
    }
}
