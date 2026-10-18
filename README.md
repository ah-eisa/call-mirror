# Call Mirror

Secure Android call and notification mirroring between your own devices.

This repository contains a hardened implementation inspired by the original Callmirror concept, with authenticated encryption, fail-closed behavior, secret-safe cloud topics, and no sensitive notification logging.

## Security changes

- AES-256-GCM authenticated encryption
- PBKDF2-HMAC-SHA256 key derivation with per-message salt
- Random nonce for every encrypted payload
- Fail closed if encryption/decryption fails
- Cloud topic derived from SHA-256 of the pairing secret instead of exposing the secret
- No SMS, OTP, caller number, title, or notification body written to Logcat

## Current scope

- Mirror incoming-call alerts
- Mirror Android notifications, including SMS/OTP when the source app exposes the text in its notification
- Cloud relay through ntfy.sh using end-to-end encrypted payloads
- Receiver device displays mirrored alerts

Use only on devices and accounts you own or are authorized to manage.
