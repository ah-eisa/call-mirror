package com.eisax.callmirror

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
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
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.secretInput.setText(MirrorPrefs.secret(this))
        binding.notificationSwitch.isChecked = MirrorPrefs.mirrorNotifications(this)
        binding.localModeSwitch.isChecked = MirrorPrefs.localEnabled(this)
        binding.localHostInput.setText(MirrorPrefs.localHost(this))
        binding.localPortInput.setText(MirrorPrefs.localPort(this).toString())
        binding.ringtoneSwitch.isChecked = MirrorPrefs.ringtone(this)
        binding.vibrateSwitch.isChecked = MirrorPrefs.vibrate(this)

        val savedRole = MirrorPrefs.role(this)
        binding.roleSender.isChecked = savedRole == MirrorPrefs.ROLE_SENDER
        binding.roleReceiver.isChecked = savedRole == MirrorPrefs.ROLE_RECEIVER

        binding.generateButton.setOnClickListener {
            binding.secretInput.setText(generateSecret())
            binding.secretInput.setSelection(binding.secretInput.text?.length ?: 0)
        }

        binding.notificationAccessButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.roleGroup.setOnCheckedChangeListener { _, _ -> updateVisibleSections() }
        binding.localModeSwitch.setOnCheckedChangeListener { _, _ -> updateVisibleSections() }

        binding.saveButton.setOnClickListener { saveAndStart() }

        updateVisibleSections()
        updateStatusPreview(savedRole)
    }

    private fun saveAndStart() {
        val secret = binding.secretInput.text?.toString()?.trim().orEmpty()
        if (secret.length < 16) {
            binding.secretLayout.error = "Use at least 16 characters"
            binding.secretInput.requestFocus()
            return
        }
        binding.secretLayout.error = null

        val role = currentRole()
        val port = binding.localPortInput.text?.toString()?.toIntOrNull() ?: MirrorPrefs.DEFAULT_PORT
        if (port !in 1..65535) {
            binding.localPortInput.error = "Use a port from 1 to 65535"
            binding.localPortInput.requestFocus()
            return
        }
        binding.localPortInput.error = null

        val localEnabled = binding.localModeSwitch.isChecked
        val localHost = binding.localHostInput.text?.toString()?.trim().orEmpty()
        if (role == MirrorPrefs.ROLE_SENDER && localEnabled && localHost.isBlank()) {
            binding.localHostInput.error = "Enter the receiver IP or turn off local mode"
            binding.localHostInput.requestFocus()
            return
        }
        binding.localHostInput.error = null

        MirrorPrefs.setRole(this, role)
        MirrorPrefs.setSecret(this, secret)
        MirrorPrefs.setMirrorNotifications(this, binding.notificationSwitch.isChecked)
        MirrorPrefs.setLocalEnabled(this, localEnabled)
        MirrorPrefs.setLocalHost(this, localHost)
        MirrorPrefs.setLocalPort(this, port)
        MirrorPrefs.setRingtone(this, binding.ringtoneSwitch.isChecked)
        MirrorPrefs.setVibrate(this, binding.vibrateSwitch.isChecked)

        requestRuntimePermissions(role)

        val serviceIntent = Intent(this, MirrorService::class.java)
        if (role == MirrorPrefs.ROLE_RECEIVER) {
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            stopService(serviceIntent)
        }

        updateStatusPreview(role)
    }

    private fun currentRole(): String {
        return if (binding.roleReceiver.isChecked) MirrorPrefs.ROLE_RECEIVER else MirrorPrefs.ROLE_SENDER
    }

    private fun updateVisibleSections() {
        val role = currentRole()
        val isSender = role == MirrorPrefs.ROLE_SENDER
        binding.senderSettings.visibility = if (isSender) View.VISIBLE else View.GONE
        binding.receiverSettings.visibility = if (isSender) View.GONE else View.VISIBLE
        binding.localSenderFields.visibility = if (isSender && binding.localModeSwitch.isChecked) View.VISIBLE else View.GONE
        binding.roleHelpText.text = if (isSender) {
            "Use this on the phone you leave behind. It securely forwards calls and selected notifications."
        } else {
            "Use this on your primary phone. It receives and shows mirrored alerts."
        }
    }

    private fun updateStatusPreview(role: String) {
        val hasSecret = MirrorPrefs.secret(this).length >= 16
        if (!hasSecret) {
            binding.statusText.text = "Not configured yet"
            return
        }

        binding.statusText.text = if (role == MirrorPrefs.ROLE_RECEIVER) {
            if (MirrorPrefs.localEnabled(this)) "Ready • Receiver • Cloud + Local" else "Ready • Receiver • Secure cloud"
        } else {
            if (MirrorPrefs.localEnabled(this)) "Ready • Transmitter • Local first, cloud fallback" else "Ready • Transmitter • Secure cloud"
        }
    }

    private fun requestRuntimePermissions(role: String) {
        val wanted = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            wanted += Manifest.permission.POST_NOTIFICATIONS
        }
        if (role == MirrorPrefs.ROLE_SENDER) {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                wanted += Manifest.permission.READ_PHONE_STATE
            }
            if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                wanted += Manifest.permission.READ_CALL_LOG
            }
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
