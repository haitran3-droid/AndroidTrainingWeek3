package com.example.dogcattranslator.ui.common

import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.example.dogcattranslator.R
import com.google.android.material.snackbar.Snackbar

/** Neo thông báo phía trên thanh tab, bao gồm khoảng chừa cho navigation bar của hệ thống. */
fun Fragment.appSnackbar(@StringRes message: Int): Snackbar? {
    val root = view ?: return null
    return Snackbar.make(root, message, Snackbar.LENGTH_LONG).apply {
        activity?.findViewById<View>(R.id.bottom_nav)?.takeIf { it.isShown }?.let {
            setAnchorView(it)
        }
    }
}
