package com.example.identityandacces

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

class LoginActivity : AppCompatActivity() {

    private lateinit var gebruikersnaamInput: EditText
    private lateinit var wachtwoordInput: EditText
    private lateinit var loginBtn: Button
    private lateinit var registreerBtn: Button
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        gebruikersnaamInput = findViewById(R.id.gebruikersnaamInput)
        wachtwoordInput = findViewById(R.id.wachtwoordInput)
        loginBtn = findViewById(R.id.inlogBtn)
        registreerBtn = findViewById(R.id.registreerBtn)
        gebruikersnaamInput.setText(intent.getStringExtra("userName"))
        wachtwoordInput.setText(intent.getStringExtra("password"))

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        loginBtn.setOnClickListener {
            val userName = gebruikersnaamInput.text.toString()
            val password = wachtwoordInput.text.toString()

            if (TextUtils.isEmpty(userName) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Voer alsjeblieft je gebruikersnaam en wachtwoord in.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            CoroutineScope(Dispatchers.Main).launch {
                loginUser(userName, password)
            }
        }

        registreerBtn.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private suspend fun loginUser(userName: String, password: String) {
        try {
            val querySnapshot = db.collection("users").whereEqualTo("username", userName).get().await()
            if (querySnapshot.isEmpty) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Ongeldige inloggegevens.", Toast.LENGTH_SHORT).show()
                }
                return
            }
            val document = querySnapshot.documents[0]
            val email = document.getString("email")
            val failedAttempts = document.getLong("failed_attempts") ?: 0
            val isBlocked = document.getBoolean("isBlocked") ?: false

            // Requirement 23: <geldig account> is bevestigd en niet geblokkeerd
            if (isBlocked) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Je account is geblokkeerd vanwege meerdere foute inlogpogingen.", Toast.LENGTH_SHORT).show()
                }
                return
            }

            // Requirement 4: Een gebruiker moet worden geauthenticeerd
            // Requirement 9: Authenticatie gebeurt met een wachtwoord
            auth.signInWithEmailAndPassword(email!!, password).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    // Requirement 23: <geldig account> is bevestigd en niet geblokkeerd
                    if (user != null && user.isEmailVerified) {
                        // Reset failed attempts on successful login
                        db.collection("users").document(document.id)
                            .update("failed_attempts", 0)
                        Toast.makeText(this@LoginActivity, "Succesvol ingelogd", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@LoginActivity, HomeActivity::class.java)
                        intent.putExtra("username", userName)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@LoginActivity, "Verifieer alsjeblieft je email.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Reset het aantal gefaalde pogingen na een succesvolle inlogpoging
                    val newFailedAttempts = failedAttempts + 1
                    // Requirement 20: een account wordt geblokkeerd na maximaal <aantal foute wachtwoordpogingen>
                    // Requirement 21: <aantal foute wachtwoordpogingen>=3
                    if (newFailedAttempts >= 3) {
                        db.collection("users").document(document.id)
                            .update("failed_attempts", newFailedAttempts, "isBlocked", true, "timestamp_blocking", Date())
                        Toast.makeText(this@LoginActivity, "Je account is geblokkeerd vanwege meerdere foute inlogpogingen.", Toast.LENGTH_SHORT).show()
                    } else {
                        db.collection("users").document(document.id)
                            .update("failed_attempts", newFailedAttempts)
                        Toast.makeText(this@LoginActivity, "Ongeldige inloggegevens.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@LoginActivity, "Fout: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
