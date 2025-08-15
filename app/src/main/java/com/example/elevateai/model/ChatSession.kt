package com.example.elevateai.model
/**
 * Represents a single chat session in the navigation drawer's history list.
 * This object is stored under each user's profile in Firebase to quickly
 * populate the history list.
 */
data class ChatSession(
    val id : String = "",
    val title: String = ""
)
