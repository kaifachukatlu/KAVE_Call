# KAVÉ Call

*Beyond the ordinary.* A voice-announcing phone app for Android.

A native Android (Kotlin + Jetpack Compose) phone app that announces incoming callers with a female
text-to-speech voice:

> "Master Kaif, *Ayesha* is calling. Would you like to pick up or not?"

You can then say **"Pick up"** / **"Reject"**, or tap the green / red button. After answering, the app
requests speakerphone, and you can switch back to the earpiece at any time.

Everything runs on the phone. There are no servers, accounts or paid APIs, and no call recording.

---

### Languages & voices

* Announcements in **English, Hindi, Urdu and Telugu**, with matching voice commands
  (for example "pick up" / "उठाओ" / "اٹھاؤ" / "ఎత్తు", and "reject" / "नहीं" / "نہیں" / "వద్దు").
* **Female or male** announcer (auto-picked from installed voices), or choose a specific voice.
* For unknown callers the number is read **digit by digit in English**, even in the other languages,
  because English digits are quicker to follow.

### Call wallpapers

During a call, the caller's photo fills the screen. The app picks the first one available:

1. A photo you set **inside KAVÉ Call** (*Home → Wallpapers*). With *Also update in Google Phone* on
   (the default), it is also saved as the contact's photo, so Google Phone and Contacts show it too.
2. The contact's own photo from your Contacts / Google Phone app. Updates there are picked up automatically
   on the next call.
3. Your **default wallpaper** from *Wallpapers*, used for unknown numbers and contacts without a photo.
4. The built-in background.

### Contact posters (iPhone-style depth effect)

On the incoming-call screen, a wallpaper becomes a **poster**: the caller's name is drawn in large type, and
the person in the photo is cut out and layered **in front of** their name, like iOS Contact Posters.

* The person is detected on the device with **ML Kit Selfie Segmentation**: a bundled model that is free
  and works offline, so no photo leaves the phone.
* Customize per wallpaper (*Wallpapers → Customize*): **depth effect** on/off, **font** (Modern, Classic,
  Elegant, Mono) and **name colour**, with a live full-screen preview.
* Photos without a clear person (landscapes, pets, objects) automatically fall back to the name over
  the photo. The model works best on photos of one or two people from the waist up.

### Placing calls

Use the built-in dial pad (*Home → Dialer*) or tap a number in your Contacts app. While KAVÉ Call is
the default phone app, every call (incoming and outgoing) uses its call screen.

## Tech

| Area | Implementation |
|---|---|
| Language / UI | Kotlin 2.2.20, Jetpack Compose (BOM 2025.10.01, Material 3) |
| Build | Android Gradle Plugin 8.13.1, Gradle 8.14.3 (wrapper), JDK 21 (Android Studio JBR) |
| SDK | minSdk 29 (Android 10), compile/target SDK 36 |
| Calls | Android Telecom `InCallService` + `RoleManager.ROLE_DIALER` |
| Voice out | `android.speech.tts.TextToSpeech` (on-device engine) |
| Voice in | `android.speech.SpeechRecognizer` (short, visible listening window) |
| Contacts | `ContactsContract.PhoneLookup` |
| Audio routing | `requestCallEndpointChange()` + `CallEndpoint` on Android 14+, `setAudioRoute()` on Android 10-13 |
| Storage | Jetpack DataStore (Preferences) |

### Key source files (`app/src/main/java/com/kaif/mastercaller/`)

| File | Role |
|---|---|
| `telecom/MasterInCallService.kt` | Telecom binds this for every call; bridges callbacks to the rest of the app |
| `telecom/CallManager.kt` | Owns the real `Call` objects: answer / reject / hang up exactly once, hold, DTMF, SIM choice |
| `telecom/CallStateManager.kt` | `StateFlow` of call and assistant state for the UI |
| `telecom/AudioRouteManager.kt` | Speaker / earpiece / Bluetooth / wired routing, mute, auto-speaker after answer |
| `contacts/ContactHelper.kt` | Number → saved contact name / photo / custom ringtone |
| `voice/AnnouncementHelper.kt` | TTS engine, voice list, female-voice auto-selection, preview |
| `voice/SpeechCommandManager.kt` | Bounded speech-recognition window |
| `voice/VoiceCommandParser.kt` | "pick up / answer / accept / yes" vs "reject / decline / cut the call / no" |
| `voice/IncomingCallAssistant.kt` | Ring → announce → listen → act, once per call |
| `voice/Ringer.kt` | Ringtone and vibration (the app rings itself so it can pause for the announcement) |
| `ui/call/IncomingCallActivity.kt` | Full-screen incoming call UI (works over the lock screen) |
| `ui/call/OngoingCallActivity.kt` | Duration, speaker / earpiece / headset, mute, hold, keypad, end call |
| `ui/screens/*` | Dashboard, Setup (permissions + role), Settings, Voice selection |
| `ui/dialer/DialerActivity.kt` | Dial pad (Android requires one before it will offer the default phone app role) |

---

## 1. Open the project

1. Start **Android Studio**.
2. **File → Open…** and select `C:\Users\Master\MasterCallerAI`.
3. Let Gradle sync. Android Studio uses its bundled JDK (JBR 21) automatically.

## 2. Build the APK

In Android Studio: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

Or from a terminal (PowerShell):

```powershell
cd C:\Users\Master\MasterCallerAI
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug          # build
.\gradlew.bat testDebugUnitTest      # unit tests
```

The output is `app\build\outputs\apk\debug\app-debug.apk`.

> The debug APK is about 60 MB because it includes the full, un-shrunk Material icon library. A release
> build with `isMinifyEnabled = true` would be much smaller.

## 3. Install the APK

* **USB:** enable *Developer options → USB debugging* on the phone, connect it, accept the
  "Allow USB debugging?" prompt, then run
  `"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk`,
  or press **Run ▶** in Android Studio.
* **File copy:** copy the APK to the phone and open it. Allow "Install unknown apps" for your file
  manager when asked.

### Installing on someone else's phone

1. Send them `app-debug.apk` (WhatsApp as a *document*, Google Drive, Telegram, USB cable, etc.).
2. On their phone, open the file. If asked, allow **"Install unknown apps"** for the app they opened it with
   (WhatsApp / Files / Drive).
3. If Google Play Protect warns "unknown developer", tap **More details → Install anyway**. This happens because
   the app isn't from the Play Store; it doesn't mean anything is wrong.
4. Open KAVÉ Call, follow **Setup**, and set it as the default phone app. The default title is **"Boss"**;
   each person can change it in *Settings → What should she call you?*.
5. **Updates:** KAVÉ checks GitHub Releases for a newer version (on opening the app, at most every 6 hours;
   or *Settings → About → Check*). When one exists, the dashboard shows **Update available → Update now**.
   It downloads the APK and Android asks to install it; settings are kept.

### Publishing an update (free, no Play Store)

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts` (e.g. 3 / `1.3.0`) and build.
2. On GitHub: **Releases → Draft a new release**, tag **`v1.3.0`** (must match `versionName`), attach the APK,
   **Publish**.
3. Every installed copy (v1.2.0 or newer) offers the update. Always build on the same computer:
   the signing key in `%USERPROFILE%\.android\debug.keystore` must stay the same, so back it up.

## 4. Grant permissions

On first launch, the app opens the **Setup** screen. Each item explains why it is needed:

| Permission | Why |
|---|---|
| Contacts | Say the caller's saved name (read locally, never uploaded) |
| Microphone | Only during the short on-screen listening window after the announcement |
| Notifications | Incoming-call alert over the lock screen and the ongoing-call notification |
| Phone | Place calls from the dial pad and pick a SIM on dual-SIM phones |
| Full-screen alerts (Android 14+) | Lets the incoming-call screen appear over the lock screen |

If you deny something, the app keeps working with reduced features. Use **App settings** on that card
to re-enable it later.

## 5. Set KAVÉ Call as the default phone app

Setup → **Default phone app → Set as default**, then confirm in the system dialog.

If the dialog doesn't appear (Android hides it after you deny it twice), tap **Default apps settings**
and choose *Phone app → KAVÉ Call*. On vivo / Funtouch OS this is
*Settings → Apps → Default apps → Phone*.

To undo, pick your old phone app (for example *Phone by Google*) in the same place.

> While KAVÉ Call is the default, it handles **all** calls, including outgoing calls placed
> from the Contacts app. The call log is still recorded by Android and is visible in the Contacts or
> Google Phone app. KAVÉ Call does not have its own call-history screen.

## 6. Test with real incoming calls

1. Finish Setup. The dashboard should show *Default phone app: Active* and *Voice assistant: Ready*.
2. Tap **Test announcement** to hear the voice. This is a preview, not a call.
3. Tap **Test voice command** and say "pick up". You should see "→ Pick up ✓".
4. Put the phone in **normal ring mode** (not silent or vibrate) with ring volume up.
5. Call your phone from another phone, once from a saved contact and once from an unsaved number.
6. Expected flow: about 2.5 s of ringing, then the ringtone pauses and the voice speaks. The mic
   indicator then listens for 6 s (adjustable). Say **"Pick up"** or **"Reject"**, or tap a button.
   After you pick up, the speaker turns on. Tap **Earpiece** or **Speaker** to switch.
7. Repeat with the phone **locked**. The full-screen call screen should appear.

Useful logs while testing:

```powershell
adb logcat -s CallManager AudioRouteManager AnnouncementHelper SpeechCommandManager MasterInCallService
```

## 7. Known Android / device restrictions

* **The default phone app role is required.** Android only lets the default dialer show its own
  call screen, answer, reject and route audio. There is no supported way around this.
* **Silent, vibrate and Do Not Disturb are respected.** In silent mode or when DND blocks the caller,
  nothing is spoken. In vibrate mode the phone vibrates and the screen shows the call, but nothing
  is spoken.
* **Voice commands need a recognizer.** `SpeechRecognizer` uses the phone's recognition service
  (usually the Google app). Offline recognition works only if the language pack is downloaded
  (*Google app → Settings → Voice → Offline speech recognition*). Without it, recognition may need
  internet. The touch buttons always work.
* **The mic works only while the call screen is visible.** Android blocks background microphone use,
  so voice commands run only while the incoming-call screen is visible. If an OEM shows only a
  heads-up notification, use its Answer / Decline buttons.
* **Voice gender is a guess.** Android's TTS API has no gender field. "Likely female" comes from known
  voice-name patterns (Google and Samsung). Preview and pick in *Settings → Female voice*. If voice data
  is missing, use *Install voice data*.
* **Speaker routing can be overridden by some OEMs.** The app requests speaker only after the call is
  active, verifies it, and retries up to 3 times. If a Bluetooth or wired headset is connected, the
  headset is kept and speaker is not forced.
* **Full-screen intents on Android 14+** may be off for sideloaded apps. Enable *Full-screen call alerts*
  in Setup.
* **vivo / Xiaomi / Oppo battery managers** can delay starting apps. If the call screen is slow to
  appear, set KAVÉ Call's battery usage to *Unrestricted* and allow *Display over lock screen*
  and *Pop-up windows* in the app's permission settings.
* **Dual-SIM:** if no default calling SIM is set, the call screen shows a SIM picker. This needs the
  Phone permission.
* **Emulators:** the emulator can simulate incoming calls (`adb emu gsm call 5551234`) but has no real
  SIM or contacts. Final verification must be done on a real phone.

## Privacy

* Contacts, numbers and voice are processed only on the device.
* No internet permission is requested by the app itself. Speech recognition may use the network inside
  the Google app if offline packs are missing.
* No call audio is ever recorded. `allowBackup` is disabled so settings aren't copied to cloud backups.

## Brand & fonts

* Visual identity: KAVÉ monogram and wordmark (from the official brand artwork), with Obsidian, Graphite,
  Champagne and Ivory colours.
* Fonts are bundled in `app/src/main/res/font`, so the app works offline:
  **Manrope** (interface) and **Cormorant Garamond** (display). Both are under the SIL Open Font License; see `licenses/`.
* The package id stays `com.kaif.mastercaller`, so existing installs and settings carry over.
