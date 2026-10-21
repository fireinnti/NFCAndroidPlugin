# Godot Android NFC plugin

Android plugin v2 for Godot **4.7.2 stable**, Kotlin/JDK 17, Android Gradle Plugin 8.9.1 and Gradle 8.11.1. The library compiles against SDK 35 with minimum SDK 24; the game's matching Godot export template determines its final device requirements (the 4.7.2 default export uses API 29+ and targets API 36).

## Build and install

Set `JAVA_HOME` to JDK 17 and configure `sdk.dir` in the untracked `local.properties` (or `ANDROID_HOME`). Then run:

```powershell
.\gradlew.bat :plugin:assemble :plugin:testDebugUnitTest :plugin:lintDebug
.\update-game-plugin.ps1 # Builds and copies both variants and scripts to ../NFCGame
```

The complete addon is generated at `plugin/demo/addons/GodotAndroidNFCPlugin`. Enable it in Godot's Plugins settings and enable Gradle Build in Android export settings. Use the editor and export templates from the same Godot release. The Godot library is `compileOnly`: the exported game supplies the engine, avoiding duplicate engine classes.

The demo displays decoded scans and NFC availability. This plugin does not write tags.

## API

Connect before enabling; enabling drains the bounded queue of scans received while Godot was starting:

```gdscript
var nfc = Engine.get_singleton("GodotAndroidNFCPlugin")
nfc.connect("tag_read", func(text: String): print(text))
nfc.connect("nfc_error", func(message: String): push_warning(message))
nfc.enableNFC()
```

Methods: `enableNFC()`, `disableNFC()`, `isNFCAvailable()`, `isNFCEnabled()`.

Signals: `tag_read(String)`, `nfc_error(String)`, and legacy `tag_readed(PackedByteArray)`. Legacy bytes now contain decoded UTF-8, without NDEF status/language headers. Subscribe to one read signal to avoid processing a scan twice.

NFC is optional hardware and requires no runtime permission prompt. Foreground dispatch is enabled on resume and disabled on pause. There is no polling thread and no dependence on a hardcoded Godot activity. The bridge activity queues incoming values before launching the actual package launcher. Unsupported records (including Android Application Records) are ignored. Empty/unformatted tags have no card value and are ignored.

## Links and card tags

Write an **NDEF URI record** in NFC Tools (custom URI/URL), not a Text record containing the same URL. Use these formats:

| Card | URI format | Effect on the scan/selection screen |
| --- | --- | --- |
| Basic | `deadright://card/basic/<color>` | Starts a question from the category mapped to that color. |
| Skip power | `deadright://card/power/<color>/skip` | Uses that color's category and plays the skip animation when animations are enabled, then starts a question. |
| Difficulty token | `deadright://card/token/<difficulty>` | Sets the next question's difficulty; scan a basic or power card afterward. |

Replace `<color>` with `red`, `blue`, `orange`, or `green`; replace `<difficulty>` with `easy` or `hard`. Do not write the angle-bracket placeholders to a tag. `skip` is currently the only supported power; `placehold` is not accepted.

Copy-ready examples:

```text
deadright://card/basic/red
deadright://card/power/red/skip
deadright://card/token/easy
deadright://card/token/hard
```

Use lowercase and the exact path shown, without a trailing slash, query string, or fragment. Rewrite old `nfcgame://` URI tags to use `deadright://`.

In `deadright://card/power/red/skip`, `deadright` is the app scheme, `card` is the host, and `power/red/skip` is the card data. The game validates the URI and turns that path into `["power", "red", "skip"]` before passing it to the selection scene. A URI scan opens or brings forward the app, but applies the card only on the scan/selection screen; scans elsewhere are not queued for later.

The companion game validates colors (`red`, `blue`, `orange`, `green`), power (`skip`) and token difficulty (`easy`, `hard`). Existing NDEF Text cards such as `basic,red`, `power,blue,skip`, and `token,easy` also work. UTF-8 and UTF-16 Text records, URI records, Smart Posters, and `text/plain` MIME records are decoded appropriately.

Android uses the first NDEF record for dispatch: put the URI first when using multiple records. A Text record with a URL does not get URI routing. Legacy `text/plain` tags may show an app chooser because other NFC apps also register for that type. Custom schemes need no website, but another installed app can register the same scheme.

The addon exposes these Project Settings (enable Advanced Settings):

```ini
[nfc]
links/custom_scheme="deadright"
links/custom_host="card"
links/web_host=""
links/web_path_prefix="/nfc/"
```

Keep scheme/host lowercase. `web_host` is a hostname only, without scheme or path; `web_path_prefix` must begin and end with `/`. The game and export plugin read the same settings.

### Verified Android App Links

For links such as `https://YOUR-DOMAIN/nfc/basic/red`, set `nfc/links/web_host` to a domain you control. The exporter adds scoped HTTP/HTTPS `VIEW` filters with `autoVerify=true`, plus matching NFC `NDEF_DISCOVERED` filters. Android 16+ dispatches NFC web URLs using `ACTION_VIEW`; older Android uses `ACTION_NDEF_DISCOVERED`.

Host this JSON at `https://YOUR-DOMAIN/.well-known/assetlinks.json`, replacing the fingerprint with the SHA-256 fingerprint of the actual app signing certificate:

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.nfc.testproject",
    "sha256_cert_fingerprints": ["YOUR:SHA256:SIGNING:CERTIFICATE:FINGERPRINT"]
  }
}]
```

With Play App Signing, use the Play app signing certificate, not the upload certificate. Debug APKs have a different signing certificate. No web domain is configured by default, and this repository does not publish the association file. A manifest filter alone cannot verify website ownership; an unverified web link can open the browser.

## Verification

On a connected Android device, install the game's APK and check both a stopped app and an already-running app:

```powershell
adb shell am force-stop com.nfc.testproject
adb shell am start -W -a android.intent.action.VIEW -d 'deadright://card/basic/red'
adb shell am start -W -a android.intent.action.VIEW -d 'deadright://card/token/hard'
```

Do not specify `-n` or `-p` when checking system link resolution. For a configured web host:

```powershell
adb shell pm verify-app-links --re-verify com.nfc.testproject
adb shell pm get-app-links com.nfc.testproject
```

Also scan real Text and URI tags with the app stopped, on the selection screen, and after pausing/resuming. Repeated physical scans should each work. A tag opens or brings forward the game, but gameplay commands apply only on its scan/selection screen. Startup, main-menu, setup and active-question scans are ignored for gameplay and are not replayed later. The setup NFC test displays received text.

Automated coverage includes NDEF text headers/encoding, URI prefix decoding, malformed payloads, unsupported records, and intent handling on API 24 and 35. The game has parser and scene-flow tests under `../NFCGame/tests`.

## Documentation used

- [Godot Android plugins](https://docs.godotengine.org/en/stable/tutorials/platform/android/android_plugin.html)
- [Godot Android export](https://docs.godotengine.org/en/stable/tutorials/export/exporting_for_android.html)
- [Android NFC dispatch and NDEF](https://developer.android.com/develop/connectivity/nfc/nfc)
- [Android foreground dispatch](https://developer.android.com/develop/connectivity/nfc/advanced-nfc)
- [Android App Links](https://developer.android.com/training/app-links/add-applinks)
- [Website association](https://developer.android.com/training/app-links/configure-assetlinks)

Use the SDK/NDK versions in the installed 4.7.2 `android_source.zip` as the authority for the game export: SDK 36, Build Tools 36.1.0 and NDK 29.0.14206865. Some stable documentation pages still list older SDK versions.
