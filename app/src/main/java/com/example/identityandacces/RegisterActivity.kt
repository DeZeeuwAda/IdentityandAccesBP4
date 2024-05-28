package com.example.identityandacces

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {

    private lateinit var userNameEdt: EditText
    private lateinit var passwordEdt: EditText
    private lateinit var userEmailEdt: EditText
    private lateinit var registerBtn: Button
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        userNameEdt = findViewById(R.id.gebruikersnaamInput)
        passwordEdt = findViewById(R.id.wachtwoordInput)
        userEmailEdt = findViewById(R.id.emailInput)
        registerBtn = findViewById(R.id.registreerBtn)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        registerBtn.setOnClickListener {
            val userName = userNameEdt.text.toString()
            val password = passwordEdt.text.toString()
            val email = userEmailEdt.text.toString()

            if (TextUtils.isEmpty(userName) || TextUtils.isEmpty(password) || TextUtils.isEmpty(email)) {
                Toast.makeText(this, "Voer alsjeblieft je gebruikersnaam, wachtwoord en email in", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val passwordValidationMessage = validatePassword(password)
            if (passwordValidationMessage != null) {
                Toast.makeText(this, passwordValidationMessage, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            CoroutineScope(Dispatchers.Main).launch {
                registerUser(userName, password, email)
            }
        }
    }

    // Requirement 10, 11, 12, 13: Validating password and providing specific error messages
    private fun validatePassword(password: String): String? {
        if (password.length < 8) {
            return "Wachtwoord moet minimaal 8 tekens bevatten."
        }
        if (!password.any { it.isLetter() }) {
            return "Wachtwoord moet minimaal 1 letter bevatten."
        }
        if (!password.any { it.isDigit() }) {
            return "Wachtwoord moet minimaal 1 cijfer bevatten."
        }
        if (!password.any { !it.isLetterOrDigit() }) {
            return "Wachtwoord moet minimaal 1 speciale karakter bevatten."
        }
        return null
    }

    private suspend fun registerUser(userName: String, password: String, email: String) {
        try {
            val querySnapshot = db.collection("users").whereEqualTo("username", userName).get().await()
            if (!querySnapshot.isEmpty) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RegisterActivity, "Gebruikersnaam bestaat al.", Toast.LENGTH_SHORT).show()
                }
                return
            }

            val registrationTask = auth.createUserWithEmailAndPassword(email, password).await()
            val user = registrationTask.user
            user?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(userName).build())?.await()
            user?.sendEmailVerification()?.await() // Requirement 16: De identiteit wordt geverifieerd door bevestiging van registratie via e-mail

            val userData = hashMapOf(
                "username" to userName, // Requirement 8: Identificatie gebeurt met een gebruikersnaam
                "email" to email,
                "password" to password, // Requirement 14: Relevant gegeven = {gebruikersnaam, wachtwoord, e-mail}
                "isBlocked" to false,
                "failed_attempts" to 0,
                "isVerified" to false
            )

            user?.uid?.let { uid ->
                db.collection("users").document(uid).set(userData).await()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RegisterActivity, "Account succesvol aangemaakt, check je mail voor de verificatie.", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
                    intent.putExtra("userName", userName)
                    intent.putExtra("password", password)
                    startActivity(intent)
                    finish()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@RegisterActivity, "Foutmelding: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
