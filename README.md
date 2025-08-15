# 🤖 ELEVATE AI Chatbot

ELEVATE AI is a modern Android chatbot application powered by the **Google Gemini API**, designed for a fast, intelligent, and interactive chat experience.  
Built with **Kotlin**, **Firebase**, and **Material Design 3**, it provides seamless authentication, persistent sessions, and rich messaging features.

---

## ✨ Features

### 🔐 User Authentication & Management
- **Dual Login Methods**: Sign up or log in using **Email/Password** or **Google Sign-In**.
- **Custom Profiles**: Upload your profile picture during sign-up.
- **Session Persistence**: Stay logged in for a smooth experience.
- **Navigation Drawer**: Central access to all features:
  - User header with profile picture, name, and email.
  - "New Chat" button for starting fresh conversations.
  - Real-time, scrollable chat history.
  - **Load More / Load Less** toggle for long histories.
  - Secure **Logout** option.

---

### 💬 Core Chat Experience
- **Intelligent AI**: Powered by **Google Gemini API** for smart, human-like responses.
- **Streaming Responses**: AI messages appear **word-by-word** for a natural feel.
- **Voice-to-Text**: Speak your prompts using the built-in microphone button.
- **Rich Text Formatting**: Supports **Markdown** for lists, bold text, and code blocks.
- **Message Actions**: Copy or Share any bot message easily.

---

## 🛠️ Tech Stack

### Core Technologies
- **Language**: Kotlin  
- **UI**: XML with Material Design 3 components  
- **Backend**: Firebase  
  - Firebase Authentication (User management)  
  - Firebase Realtime Database (Profiles & chat data)  
  - Firebase Cloud Storage (Profile pictures)  
- **Artificial Intelligence**: Google Gemini API  

---

### 📐 Architecture
- **MVVM (Model-View-ViewModel)**: Clean, scalable architecture separating UI & logic.
- **Asynchronous Programming**: Kotlin Coroutines & Flow for background tasks and AI streaming.

---

### 📚 Key Libraries
- **Android Jetpack**: ViewModel, LiveData, Lifecycle Components
- **View Binding**: Safe and efficient access to XML views
- **Coil**: Image loading & caching
- **Markwon**: Markdown rendering in chat messages
- **CircleImageView**: Circular profile images
- **Google Sign-In SDK**: Google authentication integration

---
![AILogin](https://github.com/user-attachments/assets/ed44c356-9b36-4ec4-8fba-026fd5c781d1)
![AISignup](https://github.com/user-attachments/assets/4f1a2d36-7846-4d43-ba81-ee156e6e4725)
![homescreen](https://github.com/user-attachments/assets/94a3536c-b461-4189-be55-587ea62ec075)
![historyNav](https://github.com/user-attachments/assets/3450ba77-8775-4b8b-940d-3c8fce9e7679)

