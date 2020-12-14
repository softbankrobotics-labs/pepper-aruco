package com.softbankrobotics.app.ui

import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.softbankrobotics.app.R
import kotlinx.android.synthetic.main.layout_info_log_view.view.*
import java.util.ArrayList

enum class ConversationItemType {
    INFO_LOG,
    ERROR_LOG,
    ROBOT_OUTPUT,
    HUMAN_INPUT
}
data  class ConversationItem(val text: String, val type: ConversationItemType)

class ConversationAdapter : RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder>() {

    private val items = ArrayList<ConversationItem>()

    class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val layout = layoutFromViewType(viewType)
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val conversationItem = items.get(position)
        holder.itemView.textView.text = conversationItem.text
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getItemViewType(position: Int): Int {
        val conversationItem = items.get(position)
        when (conversationItem.type) {
            ConversationItemType.INFO_LOG -> return INFO_LOG_VIEW_TYPE
            ConversationItemType.ERROR_LOG -> return ERROR_LOG_VIEW_TYPE
            ConversationItemType.HUMAN_INPUT -> return HUMAN_INPUT_VIEW_TYPE
            ConversationItemType.ROBOT_OUTPUT -> return ROBOT_OUTPUT_VIEW_TYPE
            else -> throw IllegalArgumentException("Unknown conversation item type: ${conversationItem.type}")
        }
    }

    /**
     * Add an item to the view.
     * @param text the item text
     * @param type the item type
     */
    fun addItem(text: String, type: ConversationItemType) {
        items.add(ConversationItem(text, type))
        notifyItemInserted(items.size - 1)
    }

    @LayoutRes
    private fun layoutFromViewType(viewType: Int): Int {
        when (viewType) {
            INFO_LOG_VIEW_TYPE -> return R.layout.layout_info_log_view
            ERROR_LOG_VIEW_TYPE -> return R.layout.layout_error_log_view
            ROBOT_OUTPUT_VIEW_TYPE -> return R.layout.layout_robot_output_view
            HUMAN_INPUT_VIEW_TYPE -> return R.layout.layout_human_input_view
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    companion object {
        private val INFO_LOG_VIEW_TYPE = 0
        private val ERROR_LOG_VIEW_TYPE = 1
        private val ROBOT_OUTPUT_VIEW_TYPE = 2
        private val HUMAN_INPUT_VIEW_TYPE = 3
    }
}
