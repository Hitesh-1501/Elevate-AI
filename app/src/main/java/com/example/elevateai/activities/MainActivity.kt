package com.example.elevateai.activities

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Menu
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.core.view.size
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.elevateai.R
import com.example.elevateai.adapters.ChatAdapter
import com.example.elevateai.databinding.ActivityMainBinding
import com.example.elevateai.model.ChatSession
import com.example.elevateai.model.User
import com.example.elevateai.viewmodel.MainViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    // The toggle for the navigation drawer hamburger icon.
    private lateinit var toggle: ActionBarDrawerToggle

    // The adapter for our chat messages RecyclerView.
    private lateinit var chatAdapter: ChatAdapter

    // Instance of our GoogleSignInClient needed for the complete logout.
    private lateinit var googleSignInClient: GoogleSignInClient

    // Get an instance of our MainViewModel using the 'by viewModels()' KTX delegate.
    // The Android framework automatically handles the lifecycle of this ViewModel.
    private val mainViewModel: MainViewModel by viewModels()


    // A local list to hold the current chat history for easy access when a user clicks an item.
    private var currentChatHistory: List<ChatSession> = emptyList()

    // Launcher for the Voice-to-Text activity. This waits for a result from the speech recognizer.
    private val speechResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        if(result.resultCode == Activity.RESULT_OK){
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if(!spokenText.isNullOrEmpty()){
                // Set the recognized text into the message box.
                binding.etMessage.setText(spokenText[0])
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Call our setup helper functions.
        initializeGoogleSignInClient()
        setupUI()
        observeViewModel() // This is where we start listening for data.
    }

    private fun initializeGoogleSignInClient(){
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }
    /**
     * Sets up all the initial UI components and listeners.
     */

    private fun setupUI(){
        // Set the custom toolbar as the app's action bar.
        setSupportActionBar(binding.toolbar)
        setupRecyclerView()
        setupDrawer()

        // Set up click listeners for the Send and Mic buttons.
        binding.btnSend.setOnClickListener {
            val prompt = binding.etMessage.text.toString().trim()
            mainViewModel.sendMessage(prompt)
            binding.etMessage.text?.clear()
        }
        binding.btnMic.setOnClickListener { startVoiceInput() }
    }

    private fun setupRecyclerView(){
        // Initialize the adapter, passing the functions to handle copy/share clicks.
        chatAdapter = ChatAdapter(
            onCopyClicked = { text -> copyToClipboard(text) },
            onShareClicked = { text -> shareText(text) }
        )
        binding.chatRecyclerView.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true // New messages will appear at the bottom.
            }
        }
    }

    private fun setupDrawer(){
        toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        // Set up the listener for when a navigation item is clicked.
        binding.navView.setNavigationItemSelectedListener { menuItem->
            handleNavClick(menuItem.itemId)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    // --- VIEWMODEL OBSERVERS ---


    /**
     * This is the core of the View in MVVM. We "observe" LiveData for changes
     * and update the UI accordingly.
     */

    /*
        This is the most important part of the MainActivity.
        Think of the MainViewModel as a TV station broadcasting several channels.
        The observeViewModel function is your TV, and it tunes into all those channels.
     */

    private fun observeViewModel(){
        //1 Tunes into the "User Profile" channel.
        // Observe user data to update the navigation drawer header.
        mainViewModel.userData.observe(this){user->
            // When user data arrives, it calls a function to update the nav drawer header.
            user?.let { updateNavHeader(it) }
        }
        //2. Tunes into the "Chat History" channel.
        // Observe the chat history list to update the navigation drawer menu.
        mainViewModel.chatHistory.observe(this) { history ->
            currentChatHistory = history
            // When the history list changes, it calls a function to update the nav drawer menu.
            updateChatHistoryInNavDrawer(history)
            // If this is the first time loading and no chat is active, select the latest one.
            if (mainViewModel.activeChatId.value == null && history.isNotEmpty()) {
                mainViewModel.setActiveChat(history.first().id)
            }
        }
        // 3. Watches which chat is currently active.
        // Observe the currently active chat ID to control UI visibility.
        mainViewModel.activeChatId.observe(this){chatId->
                if(chatId == null){
                    // Based on the active chat, it either shows the "Welcome Screen" or the chat messages.
                    // If no chat is active, show the welcome screen.
                    binding.welcomeScreen.visibility = View.VISIBLE
                    binding.chatRecyclerView.visibility = View.GONE
                    binding.toolbar.title = "Elevate AI"
                    // Explicitly set "New Chat" as the checked item.
                    binding.navView.menu.findItem(R.id.nav_new_chat)?.isChecked = true
                }else{
                    // If a chat is active, show the RecyclerView and hide the welcome screen.and show the chat list
                    binding.welcomeScreen.visibility = View.GONE
                    binding.chatRecyclerView.visibility = View.VISIBLE
                    val chatTitle = currentChatHistory.find { it.id == chatId }?.title
                    binding.toolbar.title = chatTitle ?: "Elevate AI"
                    // Set the correct history item as checked using
                    binding.navView.setCheckedItem(chatId.hashCode())
                }
        }
        // 4. Tunes into the "Messages" channels (for both final and streaming messages).
        // This combined observer handles updating the message list, including the streaming response.
        mainViewModel.messages.observe(this){messageList->
            // The more efficient, combined observer
            mainViewModel.messages.observe(this) { messageList ->
                // When the main message list updates, get the current value of the streaming message
                val streamingMessage = mainViewModel.streamingBotMessage.value
                chatAdapter.submitMessages(messageList, streamingMessage)
                scrollToBottom()
            }

            mainViewModel.streamingBotMessage.observe(this) { streamingMessage ->
                // When the streaming message updates, get the current value of the main message list
                val messageList = mainViewModel.messages.value ?: emptyList()
                chatAdapter.submitMessages(messageList, streamingMessage)
                scrollToBottom()
            }
            // When new messages arrive, it tells the ChatAdapter to display them.
        }
        // Observe the logout signal to navigate back to the login screen.
        mainViewModel.logoutSignal.observe(this){hasLoggedOut ->
            if(hasLoggedOut == true){
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
        }
    }


    // --- UI HELPER AND ACTION FUNCTIONS ---

    /**
     * Handles clicks from the navigation drawer menu by telling the ViewModel what to do.
     */

    private fun handleNavClick(itemId: Int){
        when(itemId){
            R.id.nav_new_chat ->{
                mainViewModel.createNewChat()
            }
            R.id.nav_logout -> mainViewModel.logout(googleSignInClient)
            else ->{
                // For history items, find the session and tell the ViewModel to make it active.
                val clickedSession =  currentChatHistory.find {
                    it.id.hashCode() == itemId
                }
                clickedSession?.let { mainViewModel.setActiveChat(it.id) }
            }
        }
    }

    private fun updateNavHeader(user: User){
        val headerView = binding.navView.getHeaderView(0)
        val navUsername = headerView.findViewById<TextView>(R.id.tvNavUserName)
        val navUserEmail = headerView.findViewById<TextView>(R.id.tvNavUserEmail)
        val navProfileImage = headerView.findViewById<ImageView>(R.id.ivNavProfileImage)
        navUsername.text = user.name
        navUserEmail.text = user.email
        if(user.profileImageUrl.isNotEmpty()){
            navProfileImage.load(user.profileImageUrl)
        }
    }

    private fun updateChatHistoryInNavDrawer(historyList: List<ChatSession>){
        val menu = binding.navView.menu
        val historySubMenu = menu.findItem(R.id.nav_history_menu).subMenu ?: return
        // Clear everything from the sub-menu first
        historySubMenu.clear()

        historyList.forEach{session ->
            historySubMenu.add(R.id.nav_history_group, session.id.hashCode(), Menu.NONE,session.title)
                .setIcon(R.drawable.baseline_history_24)
        }
    }

    private fun startVoiceInput(){
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT,"Speak now...")
        }
        try {
            speechResultLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Your device does not support voice input", Toast.LENGTH_SHORT).show()
        }
    }
    

    private fun copyToClipboard(text:String){
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Copied Text",text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    private fun shareText(text: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }
    private fun scrollToBottom() {
        if (chatAdapter.itemCount > 0) {
            binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
        }
    }
    //That function synchronizes the hamburger icon (☰) with the state of your navigation drawer (whether it's open or closed).
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        toggle.syncState()
    }
}