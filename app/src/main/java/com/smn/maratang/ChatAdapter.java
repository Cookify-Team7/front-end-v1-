package com.smn.maratang;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private List<ChatMessage> chatMessages;

    public ChatAdapter(List<ChatMessage> chatMessages) {
        this.chatMessages = chatMessages;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage message = chatMessages.get(position);
        holder.messageContent.setText(message.getText());

        // Adjust layout params based on sender
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.messageContent.getLayoutParams();
        if (message.isUser()) {
            params.gravity = Gravity.END;
            holder.messageContent.setBackgroundResource(R.drawable.bg_chat_message_user);
            holder.messageContent.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.white));
        } else {
            params.gravity = Gravity.START;
            holder.messageContent.setBackgroundResource(R.drawable.bg_chat_message_ai);
            holder.messageContent.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.black));
        }
        holder.messageContent.setLayoutParams(params);
    }

    @Override
    public int getItemCount() {
        return chatMessages.size();
    }

    public void addMessage(ChatMessage message) {
        chatMessages.add(message);
        notifyItemInserted(chatMessages.size() - 1);
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView messageContent;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            messageContent = itemView.findViewById(R.id.text_message_content);
        }
    }
}