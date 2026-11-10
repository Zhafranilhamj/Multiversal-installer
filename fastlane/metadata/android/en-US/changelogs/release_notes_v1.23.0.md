### 📱 Phone (App)
• Send to TV now asks for your app-lock PIN before anyone on the network can download your APKs
• Fixed the pairing token being dropped after scanning a QR code
• Typing a TV's IP by hand now reuses its advertised token
• PINs now use PBKDF2-HMAC-SHA256 and upgrade themselves on next unlock
• Backup passwords use the same KDF, with hostile iteration counts clamped
• Sync pages escape HTML and send nosniff headers
• The sync server restarts when you change the PIN or app lock

### 📺 Android TV
• The receive server now requires the pairing token; unauthenticated visitors get a 401 and repeated bad attempts are locked out
• Uploads stream to a temporary file and are moved into place, so a half-written APK can never be offered for install
• Uploaded file names are sanitised before they touch storage
• Root installs build correct pm commands and drain shell output as it runs, so a verbose pm can no longer deadlock

### ⌚ Wear OS
• Picks up the hardened core shared with the phone and TV builds
• Root installs build correct pm commands (no --user all on Android 8 and older, where the parser rejects it)
• Root shell output is drained while the process runs, so a verbose pm can no longer deadlock
