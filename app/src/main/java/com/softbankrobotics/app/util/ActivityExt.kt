package com.softbankrobotics.app.util

import android.app.Activity

fun Activity.activateImmersiveMode() {
    window.decorView.apply {
        systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_IMMERSIVE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN)
    }
}
