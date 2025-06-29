package com.example.otprelay

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.otprelay.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Check permissions before loading UI
        if (!hasSmsPermissions()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
                ),
                1001
            )
            // Optionally, show a loading or placeholder UI here
            return
        }
        loadMainUi()
    }

    private fun hasSmsPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadMainUi() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Observe API endpoint
        lifecycleScope.launchWhenStarted {
            viewModel.apiEndpoint.collectLatest { endpoint ->
                if (binding.apiEndpointEditText.text.toString() != endpoint) {
                    binding.apiEndpointEditText.setText(endpoint)
                }
            }
        }

        // Observe relay status
        lifecycleScope.launchWhenStarted {
            viewModel.status.collectLatest { status ->
                binding.statusTextView.text = "Status: $status"
            }
        }

        // Observe relay enabled
        lifecycleScope.launchWhenStarted {
            viewModel.relayEnabled.collectLatest { enabled ->
                if (binding.relaySwitch.isChecked != enabled) {
                    binding.relaySwitch.isChecked = enabled
                }
            }
        }

        // Save button click
        binding.saveButton.setOnClickListener {
            val endpoint = binding.apiEndpointEditText.text.toString().trim()
            if (isValidHttpsUrl(endpoint)) {
                viewModel.saveApiEndpoint(endpoint)
                Toast.makeText(this, "API endpoint saved", Toast.LENGTH_SHORT).show()
            } else {
                binding.apiEndpointLayout.error = "Enter a valid HTTPS URL"
            }
        }

        // Clear error on input change
        binding.apiEndpointEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.apiEndpointLayout.error = null
        }

        // Relay switch toggle
        binding.relaySwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setRelayEnabled(isChecked)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                loadMainUi()
            } else {
                showPermissionDeniedUi()
            }
        }
    }

    private fun showPermissionDeniedUi() {
        // Create a simple layout programmatically
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        val message = TextView(this).apply {
            text = "SMS permissions are required for this app to work. Please grant them in Settings."
            textSize = 18f
            setPadding(0, 0, 0, 32)
        }
        val button = Button(this).apply {
            text = "Open Settings"
            setOnClickListener {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        }
        layout.addView(message)
        layout.addView(button)
        setContentView(layout)
    }

    private fun isValidHttpsUrl(url: String): Boolean {
        return Patterns.WEB_URL.matcher(url).matches() && url.startsWith("https://")
    }
}