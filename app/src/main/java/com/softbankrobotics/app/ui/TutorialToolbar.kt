package com.softbankrobotics.app.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import android.util.AttributeSet
import android.view.View
import com.softbankrobotics.app.R
import kotlinx.android.synthetic.main.tutorial_toolbar.view.*

class TutorialToolbar(context: Context, attr: AttributeSet?, defStyleAttr: Int): Toolbar(context, attr, defStyleAttr) {

    constructor(context: Context, attr: AttributeSet?): this(context, attr, 0)
    constructor(context: Context): this(context, null, 0)

    init {
        View.inflate(context, R.layout.tutorial_toolbar, this)
    }

    fun setName(@StringRes nameResId: Int) {
        titleTextView.text = resources.getString(nameResId)
        invalidate()
        requestLayout()
    }

    fun setOnClickListener(callback: () -> Unit) {
        backArrow.setOnClickListener { callback() }
    }
}