package com.example.elevateai.model
/**
 * Represents a single message within a chat conversation.
 *
 * @property text The actual content of the message.
 * @property sender Identifies who sent the message, either "user" or "bot".
 * @property timestamp The time the message was created, used for ordering.
 * @property isStreaming A flag used only on the UI side to indicate if a bot's
 * response is still being generated. This is NOT saved to Firebase.
 * The '@get:Exclude' annotation could be used here if you wanted
 * to be explicit, but since we create the object before saving,
 * it won't be an issue.
 */
data class Message(
    val text: String = "",
    val sender: String = "user",
    val timestamp: Long = System.currentTimeMillis(),
    var isStreaming: Boolean = false
)