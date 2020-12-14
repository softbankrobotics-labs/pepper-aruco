package com.softbankrobotics.app.ui

import android.content.Context
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.aldebaran.qi.sdk.`object`.conversation.ConversationStatus
import com.softbankrobotics.app.R


class ConversationView(context: Context, attr: AttributeSet?, defStyleAttr: Int): RecyclerView(context, attr, defStyleAttr) {

    constructor(context: Context, attr: AttributeSet?): this(context, attr, 0)
    constructor(context: Context): this(context, null, 0)

    internal var adapter = ConversationAdapter()

    init {
        setup()
    }

    fun bindConversationTo(conversationStatus: ConversationStatus): ConversationBinder {
        return ConversationBinder.binding(conversationStatus, this)
    }

    fun addLine(text: String, type: ConversationItemType) {
        adapter.addItem(text, type)
        scrollToPosition(adapter.itemCount - 1)
    }

    private fun setup() {
        layoutManager = LinearLayoutManager(context)
        setAdapter(adapter)

        val drawable = ContextCompat.getDrawable(context, R.drawable.empty_divider_big)
        if (drawable != null) {
            val dividerItemDecoration = DividerItemDecoration(
                context,
                DividerItemDecoration.VERTICAL
            )
            dividerItemDecoration.setDrawable(drawable)
            this.addItemDecoration(dividerItemDecoration)
        }
    }
}
