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
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.security.SecureRandom

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var advancedVisible = false

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private val qrScanner = registerForActivityResult(ScanContract()) { result ->
        val raw = result.contents ?: return@registerForActivityResult
        val secret = parsePairingQr(raw)
        if (secret == null) {
            binding.statusText.text = "That QR is not a Call Mirror pairing code"
            return@registerForActivityResult
        }
        binding.secretInput.setText(secret)
        binding.statusText.text = "Pairing QR scanned successfully"
    }

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
            binding.qrImage.visibility = View.GONE
        }

        binding.showQrButton.setOnClickListener { showPairingQr() }
        binding.scanQrButton.setOnClickListener { scanPairingQr() }

        binding.notificationAccessButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.advancedButton.setOnClickListener {
            advancedVisible = !advancedVisible
            binding.advancedConnectionCard.visibility = if (advancedVisible) View.VISIBLE else View.GONE
            binding.advancedButton.text = if (advancedVisible) "Hide advanced settings" else "Advanced connection settings"
            updateVisibleSections()
        }

        binding.roleGroup.setOnCheckedChangeListener { _, _ -> updateVisibleSections() }
        binding.localModeSwitch.setOnCheckedChangeListener { _, _ -> updateVisibleSections() }

        binding.saveButton.setOnClickListener { saveAndStart() }
        binding.testButton.setOnClickListener { sendTestAlert() }

        updateVisibleSections()
        updateStatusPreview(savedRole)
    }

    private fun showPairingQr() {
        var secret = binding.secretInput.text?.toString()?.trim().orEmpty()
        if (secret.length < 16) {
            secret = generateSecret()
            binding.secretInput.setText(secret)
        }
        val payload = "CMPAIR1:$secret"
        val bitmap = BarcodeEncoder().encodeBitmap(payload, BarcodeFormat.QR_CODE, 700, 700)
        binding.qrImage.setImageBitmap(bitmap)
        binding.qrImage.visibility = View.VISIBLE
        binding.statusText.text = "Scan this QR on your other phone"
    }

    private fun scanPairingQr() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scan the Call Mirror pairing QR")
            setBeepEnabled(false)
            setOrientationLocked(false)
        }
        qrScanner.launch(options)
    }

    private fun parsePairingQr(raw: String): String? {
        if (!raw.startsWith("CMPAIR1:")) return null
        return raw.removePrefix("CMPAIR1:").trim().takeIf { it.length >= 16 }
    }

    private fun sendTestAlert() {
        if (!saveAndStart()) return
        if (currentRole() != MirrorPrefs.ROLE_SENDER) {
            binding.statusText.text = "Test alerts are sent from the SIM / messages phone"
            return
        }
        MirrorSender.send(
            this,
            MirrorPayload(
                type = MirrorPayload.TYPE_NOTIFICATION,
                appName = "Call Mirror",
                title = "Test alert",
                text = "Secure connection test from your paired phone"
            )
        )
        binding.statusText.text = "Test alert sent • check the receiver phone"
    }

    private fun saveAndStart(): Boolean {
        val secret = binding.secretInput.text?.toString()?.trim().orEmpty()
        if (secret.length < 16) {
            binding.secretLayout.error = "Generate a key or scan the QR from the other phone"
            binding.secretInput.requestFocus()
            return false
        }
        binding.secretLayout.error = null

        val role = currentRole()
        val port = binding.localPortInput.text?.toString()?.toIntOrNull() ?: MirrorPrefs.DEFAULT_PORT
        if (port !in 1..65535) {
            binding.localPortInput.error = "Use a port from 1 to 65535"
            advancedVisible = true
            binding.advancedConnectionCard.visibility = View.VISIBLE
            binding.localPortInput.requestFocus()
            return false
        }
        binding.localPortInput.error = null

        val localEnabled = binding.localModeSwitch.isChecked
        val localHost = binding.localHostInput.text?.toString()?.trim().orEmpty()
        if (role == MirrorPrefs.ROLE_SENDER && localEnabled && localHost.isBlank()) {
            binding.localHostInput.error = "Enter the receiver IP or turn off local mode"
            advancedVisible = true
            binding.advancedConnectionCard.visibility = View.VISIBLE
            binding.localHostInput.requestFocus()
            return false
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
        return true
    }

    private fun currentRole(): String {
        return if (binding.roleReceiver.isChecked) MirrorPrefs.ROLE_RECEIVER else MirrorPrefs.ROLE_SENDER
    }

    private fun updateVisibleSections() {
        val role = currentRole()
        val isSender = role == MirrorPrefs.ROLE_SENDER
        binding.senderSettings.visibility = if (isSender) View.VISIBLE else View.GONE
        binding.receiverSettings.visibility = if (isSender) View.GONE else View.VISIBLE
        binding.showQrButton.visibility = if (isSender) View.VISIBLE else View.GONE
        binding.scanQrButton.visibility = if (isSender) View.GONE else View.VISIBLE
        binding.testButton.visibility = if (isSender) View.VISIBLE else View.GONE
        binding.localSenderFields.visibility = if (isSender && binding.localModeSwitch.isChecked) View.VISIBLE else View.GONE
        binding.roleHelpText.text = if (isSender) {
            "Choose this on the phone that stays with the SIM. It sends call and message alerts."
        } else {
            "Choose this on the phone you carry. Scan the QR from the SIM phone and you are paired."
        }
    }

    private fun updateStatusPreview(role: String) {
        val hasSecret = MirrorPrefs.secret(this).length >= 16
        if (!hasSecret) {
            binding.statusText.text = "Not paired yet"
            return
        }

        binding.statusText.text = if (role == MirrorPrefs.ROLE_RECEIVER) {
            if (MirrorPrefs.localEnabled(this)) "Ready • Receiver • Cloud + Local" else "Ready • Receiver • Automatic secure cloud"
        } else {
            if (MirrorPrefs.localEnabled(this)) "Ready • SIM phone • Local first, cloud fallback" else "Ready • SIM phone • Automatic secure cloud"
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
