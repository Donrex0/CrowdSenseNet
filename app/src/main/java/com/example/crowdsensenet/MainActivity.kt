package com.example.crowdsensenet

import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var percentageLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.splash_progress)
        percentageLabel = findViewById(R.id.percentage_label)

        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Start the progress simulation
        CoroutineScope(Dispatchers.Main).launch {
            for (i in 0..100) {
                progressBar.progress = i
                percentageLabel.text = "$i%"
                delay(20) // Simulate loading time
            }

            // Start DashboardActivity
            val intent = Intent(this@MainActivity, DashboardActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}