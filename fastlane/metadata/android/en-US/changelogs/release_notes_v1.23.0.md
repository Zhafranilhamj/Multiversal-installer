### 📱 Phone (App)
• Send to TV now asks for your app-lock PIN before anyone on the network can browse or download your APKs
• Fixed the pairing token being dropped when connecting from a scanned QR code
• Typing a TV's IP by hand now reuses the token the TV advertises on the network
• PINs are hashed with PBKDF2-HMAC-SHA256 (100,000 rounds) instead of a single SHA-256 pass; existing PINs upgrade themselves on the next successful unlock
• Backup passwords are stretched with the same KDF, and a hostile iteration count inside a backup file is clamped
• Sync pages escape HTML, send nosniff headers, and report an exact download length
• The sync server now restarts the moment you change the PIN or toggle the app lock

### 📺 Android TV
• The receive server now requires the pairing token; unauthenticated visitors get a clear 401 page and repeated bad attempts are locked out
• Uploads stream through a bounded multipart parser that writes to a temporary file and moves it into place, so a half-written APK can never be offered for install
• Uploaded file names are sanitised before they touch storage
• /ping stays open so the phone can still probe which port the TV is listening on

### ⌚ Wear OS
• Picks up the hardened core shared with the phone and TV builds
• Root installs build correct pm install-create commands (no --user all on Android 8 and older, where the parser rejects it)
• Root shell output is drained while the process runs, so a verbose pm can no longer deadlock the install
