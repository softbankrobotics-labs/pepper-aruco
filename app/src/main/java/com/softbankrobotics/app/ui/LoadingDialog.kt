package com.softbankrobotics.app.ui

import android.app.AlertDialog
import android.content.Context
import android.widget.TextView
import android.view.LayoutInflater
import com.softbankrobotics.app.R

class LoadingDialog(context: Context) {

    val textView: TextView
    val dialog : AlertDialog

    init {
        val builder = AlertDialog.Builder(context)
        builder.setCancelable(false) // if you want user to wait for some process to finish,
        dialog = builder.create()
        val factory = LayoutInflater.from(context)
        val dialogLayout = factory.inflate(R.layout.layout_loading_dialog, null)
        dialog.setView(dialogLayout)
        textView = dialogLayout.findViewById(R.id.loading_text)
    }

    fun show(text: String) {
        textView.setText(text)
        dialog.show()
    }

    fun dismiss() {
        dialog.dismiss()
    }

}