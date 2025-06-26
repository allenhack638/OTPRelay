package com.example.otprelay

import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.otprelay.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    private fun isValidHttpsUrl(url: String): Boolean {
        return Patterns.WEB_URL.matcher(url).matches() && url.startsWith("https://")
    }
}