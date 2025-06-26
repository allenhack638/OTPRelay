package com.example.otprelay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.otprelay.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.INTERNET
    )

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val denied = permissions.filterValues { !it }.keys
            if (denied.isNotEmpty()) {
                showPermissionExplanation(denied)
            } else {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request permissions on startup
        checkAndRequestPermissions()

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
            if (isChecked) {
                if (!hasAllPermissions()) {
                    checkAndRequestPermissions()
                    binding.relaySwitch.isChecked = false
                } else {
                    viewModel.setRelayEnabled(true)
                }
            } else {
                viewModel.setRelayEnabled(false)
            }
        }
    }

    private fun isValidHttpsUrl(url: String): Boolean {
        return Patterns.WEB_URL.matcher(url).matches() && url.startsWith("https://")
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkAndRequestPermissions() {
        val toRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            permissionLauncher.launch(toRequest.toTypedArray())
        }
    }

    private fun showPermissionExplanation(denied: Set<String>) {
        val message = buildString {
            append("This app requires the following permissions to relay OTPs:\n\n")
            denied.forEach {
                when (it) {
                    Manifest.permission.RECEIVE_SMS -> append("- Receive SMS: To detect OTP messages\n")
                    Manifest.permission.READ_SMS -> append("- Read SMS: To access OTP content\n")
                    Manifest.permission.INTERNET -> append("- Internet: To relay OTPs to your endpoint\n")
                }
            }
            append("\nPlease grant these permissions in settings.")
        }
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}