package com.example.elevateai.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.elevateai.model.ChatSession
import com.example.elevateai.model.Message
import com.example.elevateai.model.User
import com.example.elevateai.repository.ChatRepository
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.lang.StringBuilder
/*The main job of the MainViewModel is to be the "brain" for your MainActivity.
   It manages all the logic and data,
   so the MainActivity can focus only on showing things to the user.
 */
class MainViewModel: ViewModel() {
    // Create a private instance of our ChatRepository to get data.
    private val repository = ChatRepository()

   // The ViewModel's most important job is to prepare data for the MainActivity to display.
    // We use LiveData for this because it automatically notifies the UI whenever the data changes.



    // --- LIVE DATA FOR THE UI ---

    // Expose the user data directly from the repository. The UI will observe this.
    val userData :LiveData<User?> = repository.getUser()

    // Expose the chat history list directly from the repository
    val chatHistory : LiveData<List<ChatSession>> = repository.getChatHistory()

    // A private LiveData to signal when logout is complete.
    private val _logoutSignal = MutableLiveData<Boolean>()

    // A public, read-only version of the logout signal for the UI to observe.
    val logoutSignal: LiveData<Boolean> get() = _logoutSignal

    // A private LiveData to hold the ID of the currently active chat.
    private val _activeChatId = MutableLiveData<String?>(null)

    // A public, read-only version for the UI.
    val activeChatId: LiveData<String?> get() = _activeChatId

    // A LiveData to hold the bot's message while it's being streamed.
    val streamingBotMessage = MutableLiveData<Message?>()



    /**
     * This LiveData will hold the list of messages for the currently active chat.
     * It uses a `switchMap` transformation, which is a powerful way to create a
     * dependency between two LiveData objects.
     */
    /*
        This is the most powerful part of the ViewModel.
        It creates an automatic link between the currently selected chat and the messages that are displayed.
     */
     val messages: LiveData<List<Message>> = _activeChatId.switchMap {id->
        // The code inside this block runs every time the value of `_activeChatId` changes.
        // The new ID is provided as the variable 'id'.
         id?.let {
            // The switchMap will automatically start observing
             // the new LiveData<List<Message>> that the repository returns.
             repository.getMessagesForChat(it)
         }?: MutableLiveData(emptyList())
    }

    // --- USER ACTION FUNCTIONS ---
    /**
     * Sets the currently active chat. This is called when a user clicks on a
     * chat in their history. Changing this value triggers the `switchMap` above.
     * @param chatId The ID of the chat to make active.
     */

    fun setActiveChat(chatId: String?){
        // We only update the value if it's a new chat ID to avoid unnecessary reloads.
        if(_activeChatId.value != chatId){
            _activeChatId.value = chatId
        }
    }
    /**
     * Handles the entire process of sending a message to the AI.
     * It now also handles creating a new chat if none is active.
     * @param prompt The user's text message.
     */
    fun sendMessage(prompt: String){
        // Don't do anything if there's no active chat or the message is blank.
        if(prompt.isBlank()) return

        val currentChatId = _activeChatId.value

        if(currentChatId == null){
            // This is the first message of a NEW chat.
            viewModelScope.launch {
                // 1. Create the new chat in the repository using the prompt as the title.
                val newChatId = repository.createNewChat(prompt)
                if(newChatId != null){
                    // 2. Set this new chat as the active one.
                    setActiveChat(newChatId)
                    // 3. Send the message to the newly created chat.
                    proceedWithSendingMessage(prompt,newChatId)
                }
            }
        }else{
            // This is an EXISTING chat.
            proceedWithSendingMessage(prompt, currentChatId)
        }

    }
    /**
     * A private helper function to handle the logic of saving the user's message
     * and getting a response from the AI.
     * @param prompt The user's message.
     * @param chatId The ID of the chat to add messages to.
     */
    private fun proceedWithSendingMessage(prompt: String, chatId: String){
        // 1. Immediately save the user's message to Firebase.
        val userMessage = Message(text = prompt, sender = "user")
        repository.saveMessage(userMessage,chatId)

        // 2. Launch a coroutine to handle the asynchronous AI call without blocking the UI.
        viewModelScope.launch {
            val fullResponse = StringBuilder()
            // 3. Set an initial, empty streaming message to show the "typing" indicator.
            streamingBotMessage.value = Message(text = "",sender="bot", isStreaming = true)

            // 4. Collect the stream of text chunks from the repository.
            repository.getAiResponseStream(prompt)
                .onEach {chunk->
                    // For each chunk received, append it to our full response.
                    fullResponse.append(chunk)
                    // Update the LiveData with the latest version of the streamed text.
                    streamingBotMessage.value = streamingBotMessage.value?.copy(text = fullResponse.toString())
                }
                .onCompletion {
                    // 5. When the stream is complete, save the final, full message to Firebase.
                    val finalMessage = Message(text = fullResponse.toString(), sender = "bot")
                    repository.saveMessage(finalMessage,chatId)
                    // 6. Clear the temporary streaming message.
                    streamingBotMessage.postValue(null)
                }
                .collect() // This function starts the flow collection.
        }
    }
    /**
     * Creates a new chat session. It first deactivates the current chat to show the
     * welcome screen, then tells the repository to create the new chat entry.
     */
    fun createNewChat(){
        setActiveChat(null)
    }
    /**
     * Handles the complete logout process for both Google and Firebase.
     * @param googleSignInClient The client instance from the MainActivity.
     */
    fun logout(googleSignInClient: GoogleSignInClient) {
        // 1. Sign out from the Google Sign-In client first.
        googleSignInClient.signOut().addOnCompleteListener {
            // 2. Once Google sign-out is complete, sign out from Firebase.
            repository.logout()
            // 3. Finally, send the signal to the MainActivity that the logout is complete.
            _logoutSignal.value = true
        }
    }

}