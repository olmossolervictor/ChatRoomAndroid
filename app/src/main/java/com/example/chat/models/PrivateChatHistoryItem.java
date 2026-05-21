package com.example.chat.models;

public class PrivateChatHistoryItem {
    private final int otherUserId;
    private final String otherUserName;
    private final long lastInteractionMs;

    public PrivateChatHistoryItem(int otherUserId, String otherUserName, long lastInteractionMs) {
        this.otherUserId = otherUserId;
        this.otherUserName = otherUserName;
        this.lastInteractionMs = lastInteractionMs;
    }

    public int getOtherUserId() {
        return otherUserId;
    }

    public String getOtherUserName() {
        return otherUserName;
    }

    public long getLastInteractionMs() {
        return lastInteractionMs;
    }
}
