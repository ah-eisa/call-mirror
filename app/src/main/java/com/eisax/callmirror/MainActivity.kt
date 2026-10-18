package com.eisax.callmirror

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.eisax.callmirror.databinding.ActivityMainBinding
import java.security.SecureRandom

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.secretInput.setText(MirrorPrefs.secret(this))
        binding.notificationSwitch.isChecked = MirrorPrefs.mirrorNotifications(this)
        binding.roleSender.isChecked = MirrorPrefs.role(this) == MirrorPrefs.ROLE_SENDER
        binding.roleReceiver.isChecked = MirrorPrefs.role(this) == MirrorPrefs.ROLE_RECEIVER

        binding.generateButton.setOnClickListener {
            val secret = generateSecret()
            binding.secretInput.setText(secret)
        }

        binding.notificationAccessButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.saveButton.setOnClickListener {
            val secret = binding.secretInput.text?.toString()?.trim().orEmpty()
            if (secret.length < 16) {
                binding.secretLayout.error = "Use at least 16 characters"
                return@setOnClickListener
            }
            binding.secretLayout.error = null
            val role = if (binding.roleReceiver.isChecked) MirrorPrefs.ROLE_RECEIVER else MirrorPrefs.ROLE_SENDER
            MirrorPrefs.setRole(this, role)
            MirrorPrefs.setSecret(this, secret)
            MirrorPrefs.setMirrorNotifications(this, binding.notificationSwitch.isChecked)
            requestRuntimePermissions()
            ContextCompat.startForegroundService(this, Intent(this, MirrorService::class.java))
            binding.statusText.text = if (role == MirrorPrefs.ROLE_RECEIVER) "Receiver active" else "Sender configured"
        }
    }

    private fun requestRuntimePermissions() {
        val wanted = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            wanted += Manifest.permission.POST_NOTIFICATIONS
        }
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            wanted += Manifest.permission.READ_PHONE_STATE
        }
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            wanted += Manifest.permission.READ_CALL_LOG
        }
        if (wanted.isNotEmpty()) permissionsLauncher.launch(wanted.toTypedArray())
    }

    private fun generateSecret(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        val random = SecureRandom()
        return buildString {
            repeat(32) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }
}
