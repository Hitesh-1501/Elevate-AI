package com.example.elevateai.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.elevateai.databinding.MessageItemBotBinding
import com.example.elevateai.databinding.MessageItemUserBinding
import com.example.elevateai.model.Message
import io.noties.markwon.Markwon

class ChatAdapter(
    private val onCopyClicked: (String) -> Unit,
    private val onShareClicked: (String) -> Unit
): RecyclerView.Adapter<RecyclerView.ViewHolder>(){

    private val messages : MutableList<Message> = mutableListOf()

    // These constants help the adapter distinguish between message types.
    companion object{
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_BOT = 2
    }
    // A ViewHolder for the user's message layout (message_item_user.xml)
    class UserViewHolder(binding: MessageItemUserBinding): RecyclerView.ViewHolder(binding.root){
        val messageTextView = binding.messageTextView
    }

    // A ViewHolder for the bots message layout (message_item_bot.xml)

    class BotViewHolder(binding: MessageItemBotBinding): RecyclerView.ViewHolder(binding.root){
        val messageTextView = binding.messageTextView
        val copyButton = binding.btnCopy
        val shareButton = binding.btnShare
        val actionsLayout = binding.actionsLayout
    }
    /**
     * This function is called by the RecyclerView to determine which type of view
     * to use for a specific item in the list.
     */
    override fun getItemViewType(position: Int): Int {
        return if (messages[position].sender == "user"){
            VIEW_TYPE_USER
        }else{
            VIEW_TYPE_BOT
        }
    }
    /**
     * This function is called when the RecyclerView needs a new ViewHolder. It inflates
     * the correct XML layout file based on the view type.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if(viewType == VIEW_TYPE_USER){
            val binding = MessageItemUserBinding.inflate(LayoutInflater.from(parent.context),parent,false)
            UserViewHolder(binding)
        }else{
            val binding = MessageItemBotBinding.inflate(LayoutInflater.from(parent.context),parent,false)
            BotViewHolder(binding)
        }
    }
    /**
     * This function is called by the RecyclerView to display the data at a specific position.
     * It binds the message data to the views inside the ViewHolder.
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        // Markwon is the library we use to render Markdown (like code blocks, bold text, etc.)
        val markwon = Markwon.create(holder.itemView.context)

        when(holder){
            is UserViewHolder -> {
                // For user messages, we just set the text
                holder.messageTextView.text = message.text
            }
            is BotViewHolder -> {
                // For bot messages, we use Markwon to render the text.
                markwon.setMarkdown(holder.messageTextView,message.text)
                // If the message is still streaming from the AI...
                if(message.isStreaming){
                    holder.messageTextView.append(" ▌") // Add a blinking cursor effect
                    holder.actionsLayout.visibility  = View.GONE
                }else{
                    // Once streaming is done, show the copy/share buttons.
                    holder.actionsLayout.visibility = View.VISIBLE
                }
                // Set up the click listeners for the action buttons.
                holder.copyButton.setOnClickListener { onCopyClicked(message.text) }
                holder.shareButton.setOnClickListener { onShareClicked(message.text) }
            }
        }
    }
    /**
     * Returns the total number of items in the list.
     */
    override fun getItemCount(): Int {
        return messages.size
    }
    /**
     * A helper function to update the list of messages. This is called from the MainActivity
     * when the ViewModel provides a new list of messages.
     */
    fun submitMessages(newMessages: List<Message>, streamingMessages: Message?){
        messages.clear()
        messages.addAll(newMessages)
        // If there's a message currently being streamed, add it to the end of the list.
        streamingMessages?.let {
            messages.add(it)
        }
        // This tells the adapter to redraw the entire list.
        notifyDataSetChanged()
    }
}