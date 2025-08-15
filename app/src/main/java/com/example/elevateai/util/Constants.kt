package com.example.elevateai

/**
 * An 'object' in Kotlin is a singleton, meaning only one instance of it exists.
 * We use it here to hold constant values that are accessible from anywhere in the app.
 */
object Constants {
    // Top-level nodes in Firebase Realtime Database
    const val USERS_NODE = "users"
    const val CHATS_NODE = "chats"

    // Child nodes
    const val CHAT_SESSIONS_NODE = "chat_sessions"
    const val METADATA_NODE = "metadata"
    const val MESSAGES_NODE = "messages"
}