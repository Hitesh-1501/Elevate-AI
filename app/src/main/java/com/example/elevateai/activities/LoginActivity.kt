package com.example.elevateai.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.load
import com.example.elevateai.R
import com.example.elevateai.model.User
import com.example.elevateai.databinding.ActivityLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.File

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding

    // Firebase services
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var storage:FirebaseStorage

    // google sign in client
    private lateinit var googleSignInClient:GoogleSignInClient

    // A variable to hold the URI of the selected profile image
    private var imageUri: Uri? = null

    private var cameraImageUri :Uri?= null

    // A flag to check if we are in "Login" mode or "Sign Up" mode
    private var isLoginMode = true

    // This launcher opens the phone's gallery to pick an image.
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()){uri->
        // This code runs AFTER the user selects an image from the gallery.
        if(uri != null){
            imageUri = uri // Save the image's location.
            binding.profileImageView.load(imageUri) // Display the chosen image.
        }
    }
    // This launcher opens the camera app.
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        // This code runs AFTER the user takes a picture.
        if(result.resultCode == RESULT_OK){
            // The photo was successfully taken and saved to the URI we provided.
            imageUri = cameraImageUri
            binding.profileImageView.load(imageUri)
        }
    }

    // This launcher starts the Google Sign-In screen.
    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        // This code runs AFTER the user selects a Google account.
        if(result.resultCode == RESULT_OK){
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                // If successful, get the account details and use them to sign into Firebase.
                val account = task.getResult(ApiException::class.java)!!
                showLoading(true) // Show progress bar HERE
                firebaseAuthWithGoogle(account.idToken!!)
            }catch (e:ApiException){
                // If it fails, show an error.
                Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // This launcher asks the user for a permission (e.g., CAMERA).
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){isGranted->
        // This code runs AFTER the user taps "Allow" or "Deny" on the permission pop-up.
        if(isGranted){
            openCamera()
        }else{
            Toast.makeText(this, "Camera permission is required to take a picture", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase services
        firebaseAuth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        storage = FirebaseStorage.getInstance()


       // Configure the Google Sign-In options, requesting an ID token for Firebase.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this,gso)

        setupUI()

    }
    private fun setupUI(){
        // When the user clicks the "Need an account?" text...
        binding.tvToggleMode.setOnClickListener {
            isLoginMode = !isLoginMode // Flip the mode
            updateUI()
        }
        // When the user clicks the main action button ("Login" or "Sign Up")...
        binding.btnAction.setOnClickListener {
            if(isLoginMode){
                handleLogin()
            }else{
                handleSignUp()
            }
        }
        // When the user clicks the Google Sign-In button...
        binding.btnGoogleSignIn.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)  // Start the Google flow.
        }
        // When the user clicks the profile image view...
        binding.profileImageView.setOnClickListener {
            showImagePickerDialog() // Show the pop-up to choose between camera/gallery.
        }
    }

    // This function sets up all the click listeners for your buttons and views.
    private fun updateUI(){
        if(isLoginMode){
            binding.toggleLogintv.text = "Login"
            binding.profileImageView.visibility = View.GONE
            binding.signUpFields.visibility = View.GONE
            binding.btnAction.text = "Login"
            binding.tvToggleMode.text = "Need an account? Sign Up"
        }else{
            binding.toggleLogintv.text = "Sign Up"
            binding.profileImageView.visibility = View.VISIBLE
            binding.signUpFields.visibility = View.VISIBLE
            binding.btnAction.text = "SignUp"
            binding.tvToggleMode.text = "Already have an account? Login"
        }
    }
    // This function handles the entire sign-up process.
    private fun handleSignUp(){
        val name = binding.nameEt.text.toString()
        val email = binding.emailEt.text.toString()
        val password = binding.passwordEt.text.toString()

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || imageUri == null) {
            Toast.makeText(this, "Please fill all fields and select a profile picture", Toast.LENGTH_SHORT).show()
            return // Stop the function if validation fails.
        }
        showLoading(true)
        // Use Firebase Auth to create a new user with email and password.
        firebaseAuth.createUserWithEmailAndPassword(email,password)
            .addOnCompleteListener {task->
                if(task.isSuccessful){
                    //If user creation succeeds, start the next step: uploading their data.
                    val user = firebaseAuth.currentUser!!
                    uploadProfileImageAndSaveData(user.uid,name,email)
                }else{
                    // If it fails, show an error message.
                    showLoading(false)
                    Toast.makeText(this, "Sign-Up Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // This function uploads the profile picture and saves the user's info.
    private fun uploadProfileImageAndSaveData(uid:String, name: String, email: String){
        // A. Define the path in Firebase Storage where the image will be saved.
        val storageRef = storage.reference.child("profile_pictures/$uid")

        // B. Upload the file from the imageUri we saved earlier.
        storageRef.putFile(imageUri!!)
            .addOnSuccessListener {
                // C. If the upload is successful, get the public URL of the uploaded image.
                storageRef.downloadUrl.addOnSuccessListener {downloadUrl->
                    // D. Create a User object with all the info, including the image URL
                    val user = User(uid,name,email,downloadUrl.toString())
                    // E. Save this User object to the Realtime Database under the user's unique ID (uid).
                    database.reference.child("users").child(uid).setValue(user)
                        .addOnSuccessListener {
                            // 6. If everything is successful, show a message and go to the main screen
                            Toast.makeText(this, "Sign-Up Successful!", Toast.LENGTH_SHORT).show()
                            showLoading(false) // Hide the progress bar now.
                            goToMainActivity()
                        }
                        .addOnFailureListener {
                            // Also hide on failure
                            showLoading(false)
                            Toast.makeText(this, "Failed to save user data.", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Image Upload Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleLogin(){
        // 1. Get user input.
        val email = binding.emailEt.text.toString().trim()
        val password = binding.passwordEt.text.toString().trim()

        // 2. Validate the input.
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }
        showLoading(true) // Show progress bar
        // 3. Use Firebase Auth to sign the user in.
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                // The task is complete, so hide the progress bar
                showLoading(false)
                if (task.isSuccessful) {
                    // 4. If successful, show a message and go to the main app screen.
                    Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                    goToMainActivity()
                } else {
                    // If it fails, show an error.
                    Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
    // This function is called by the googleSignInLauncher.

    private fun firebaseAuthWithGoogle(idToken:String){
       // Create a Firebase credential using the token from Google.
        val credential = GoogleAuthProvider.getCredential(idToken,null)

        // Use the credential to sign into Firebase.

        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this){task->
                // The Firebase task is complete, so hide the progress bar
                showLoading(false)
                if(task.isSuccessful){
                    val firebaseUser = firebaseAuth.currentUser!!
                    // Check if this is a brand-new user.
                    val isNewUser = task.result?.additionalUserInfo?.isNewUser ?: false
                    if(isNewUser){
                        // If they are new, save their info from Google to our Realtime Database.
                        val user = User(
                            uid = firebaseUser.uid,
                            name = firebaseUser.displayName ?:"N/A",
                            email = firebaseUser.email ?: "",
                            profileImageUrl = firebaseUser.photoUrl?.toString() ?:""
                        )
                        database.reference.child("users").child(firebaseUser.uid).setValue(user)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Google Sign-In Successful!", Toast.LENGTH_SHORT).show()
                                goToMainActivity()
                            }
                            .addOnFailureListener {
                                // Handle potential error
                                Toast.makeText(this, "Failed to save user data.", Toast.LENGTH_SHORT).show()
                            }
                    }else{
                        goToMainActivity()
                    }
                }else{
                    Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Creates the pop-up dialog to choose between Camera and Gallery.
    private fun showImagePickerDialog(){
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Choose your profile picture")
        builder.setItems(options){dialog, which ->
            when(which) {
                0 -> checkCameraPermissionAndOpen()  // User chose "Take Photo".
                1 -> galleryLauncher.launch("image/*") //// User chose "Choose from Gallery".
                2 -> dialog.dismiss()
            }
        }
        builder.show()
    }

    // Safely checks for camera permission before trying to open the camera
    private fun checkCameraPermissionAndOpen(){
        when{
            // If permission is already granted...
            ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ->{
                openCamera()
            }
            // If we should show an explanation why we need the permission...
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)->{
                AlertDialog.Builder(this)
                    .setTitle("Permission needed")
                    .setMessage("This permission is needed to take a picture for your profile.")
                    .setPositiveButton("OK"){_,_ ->
                        // Request the permission again.
                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .create().show()
            }
            // Otherwise, just request the permission.
            else->{
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }


    //This new function will create an empty image file and generate a secure Uri for it using our FileProvider.
    private fun createImageUri(): Uri {
        // Create an image file with a unique name
        val image = File(applicationContext.filesDir,"camera_photo.png")
        // Return the secure URI for that file using your provider authority
        return FileProvider.getUriForFile(
            applicationContext,
            "com.example.elevateai.fileprovider",
            image
        )
    }

    private fun openCamera(){
        // Get a secure URI for the file where the photo will be saved.
        cameraImageUri = createImageUri()
        // Launch the camera and pass the URI as an extra.
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT,cameraImageUri)
        }
        cameraLauncher.launch(cameraIntent)
    }

    private fun showLoading(isLoading: Boolean){
        if(isLoading){
            binding.progressBar.visibility = View.VISIBLE
            binding.btnAction.isEnabled = false
            binding.btnGoogleSignIn.isEnabled = false
        }else{
            binding.progressBar.visibility = View.GONE
            binding.btnAction.isEnabled = true
            binding.btnGoogleSignIn.isEnabled = true
        }
    }

    // A simple helper function to navigate to the MainActivity.
    private fun goToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish() // Call finish() so the user can't press "back" to return to the login screen.
    }
}