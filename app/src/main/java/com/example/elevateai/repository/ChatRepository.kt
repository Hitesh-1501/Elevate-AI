package com.example.elevateai.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.elevateai.BuildConfig
import com.example.elevateai.Constants
import com.example.elevateai.model.ChatSession
import com.example.elevateai.model.Message
import com.example.elevateai.model.User
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class ChatRepository {
    // Get an instance of the Firebase Authentication service.
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // Get an instance of the Firebase Realtime Database service.
    private val database:FirebaseDatabase = FirebaseDatabase.getInstance()

    // Get the unique ID of the currently logged-in user. This is crucial for all user-specific data operations
    private val uid = auth.currentUser?.uid

    // Initialize the Gemini Model using the secure API key from your BuildConfig file.
    // We are choosing the 'gemini-1.5-flash' model because it's fast and efficient for chat.
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )
    /**
     * Fetches the current user's profile data from the "/users/{uid}" path in Firebase.
     * @return LiveData containing the User object, or null if no user is logged in.
     */
    fun getUser(): LiveData<User?>{
        val liveData = MutableLiveData<User?>()
        if(uid == null){
            liveData.value = null
            return liveData
        }
        database.reference.child(Constants.USERS_NODE).child(uid).get().addOnSuccessListener {snapshot->
            // When the data is successfully fetched, convert it to a User object and post it to the LiveData.
            liveData.value = snapshot.getValue(User::class.java)
        }
        return liveData
    }

    /**
     * Fetches the list of chat sessions for the current user in real-time.
     * It attaches a listener that will automatically provide updates if the history changes.
     * @return LiveData containing a list of ChatSession objects.
     */

    fun getChatHistory(): LiveData<List<ChatSession>>{
        val livedata = MutableLiveData<List<ChatSession>>()
        if(uid == null){
            livedata.value = emptyList()
            return livedata
        }
        val sessionsRef = database.reference.child(Constants.USERS_NODE).child(uid).child(Constants.CHAT_SESSIONS_NODE)

        sessionsRef.addValueEventListener(object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                // This code runs every time the user's chat history changes in the database.
                val sessions = snapshot.children.mapNotNull {
                    it.getValue(ChatSession::class.java)
                }
                livedata.value = sessions.reversed() // Reverse the list to show the newest chats first.
            }

            override fun onCancelled(error: DatabaseError) {
                // Here could log errors if you fail to read the data.
            }
        })
        return livedata
    }

    /**
     * Fetches all messages for a specific chat ID in real-time.
     * @param chatId The unique ID of the chat to fetch messages for.
     * @return LiveData containing a list of Message objects.
     */

    fun getMessagesForChat(chatId: String): LiveData<List<Message>>{
        val livedata = MutableLiveData<List<Message>>()
        val messagesRef = database.reference.child(Constants.CHATS_NODE).child(chatId).child(Constants.MESSAGES_NODE)

        messagesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // This code runs every time a new message is added to this chat.
                val messages = snapshot.children.mapNotNull { it.getValue(Message::class.java) }
                livedata.value = messages
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        return livedata
    }

    /**
     * Calls the Gemini API with a user's prompt.
     * @param prompt The user's message.
     * @return A Flow that emits the AI's response in text chunks as they are generated.
     */
    /*
        This declares that the function will not return a single String.
        Instead, it returns a Flow object. A Flow is a stream that will "emit" or "produce"
        multiple String values over a period of time.
     */
    fun getAiResponseStream(prompt: String): Flow<String> {
        // We create a 'content' block for the API call. Later, you could add previous messages here to give the AI context.
        val chatHistory = content(role = "user"){
            text(prompt)
        }
        // This calls the Gemini SDK's streaming function.
        return generativeModel.generateContentStream(chatHistory).map {chunk ->
            // We extract the text from each chunk and emit it.
            chunk.text ?: ""
        }
    }
    /**
     * Saves a message object to the database under a specific chat ID.
     * @param message The Message object to save.
     * @param chatId The ID of the chat to save the message to.
     */

    fun saveMessage(message: Message, chatId: String){
        if(uid == null) return
        database.reference
            .child(Constants.CHATS_NODE)
            .child(chatId)
            .child(Constants.MESSAGES_NODE)
            .push() // push() creates a new unique key for each message.
            .setValue(message)
    }

    /**
     * Creates a new chat session in the database for the current user.
     * This is an atomic operation that writes to two different locations in the database
     * at the same time, ensuring data consistency.
     * @param firstMessage The first prompt from the user.
     */

    fun createNewChat(firstMessage: String): String?{
        if(uid == null) return null
        // Get a new unique ID for the chat from the top-level "chats" node.
        val newChatId = database.reference.child(Constants.CHATS_NODE)
            .push().key ?: return null

        //Create a user-friendly title with the current date.
        val formattedDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
        // Use the first message as the title (or a truncated version of it).
        val chatTitle = firstMessage.take(30)
        val newChatTitle = "$chatTitle - $formattedDate"

        val userChatSession  = ChatSession(id = newChatId, title = newChatTitle)
        // Create the metadata for the main chat object.
        val chatMetadata = mapOf(
            "title" to newChatTitle,
            "createdAt" to System.currentTimeMillis()
        )
        // Prepare a map for the multi-location update.
        val updateMap = mapOf(
            "/${Constants.USERS_NODE}/$uid/${Constants.CHAT_SESSIONS_NODE}/$newChatId" to userChatSession,
            "/${Constants.CHATS_NODE}/$newChatId/${Constants.METADATA_NODE}" to chatMetadata
        )
        // Execute the update. Firebase will either complete both writes or none of them.
        database.reference.updateChildren(updateMap)
        return newChatId
    }

    fun logout() {
        auth.signOut() // Only handles Firebase sign out.
    }

}

