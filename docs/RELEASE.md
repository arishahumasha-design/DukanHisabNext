# Release builds, signing and the upload key

App id **`com.taqwatech.dokanhisab`** (permanent, never changes). Version **1.0.0 (versionCode 1)**,
minSdk 26, targetSdk 37. The debug build is `com.taqwatech.dokanhisab.debug` ("দোকান হিসাব (ডিবাগ)"),
a separate app with separate data.

## 1. The upload key (made once, by the owner, never by Claude)

**Play App Signing:** Google keeps the real *app signing key* and signs what phones download. You
sign your uploads with your own **upload key**, which only proves the upload is from you. If the upload
key is ever lost, Google support can reset it; the app itself stays the same.

Create it in **Windows PowerShell** (you type the passwords there, they are never shown or saved):

```powershell
New-Item -ItemType Directory -Force C:\Users\Ibrahim\Keys | Out-Null
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore C:\Users\Ibrahim\Keys\dokanhisab-upload.jks -storetype PKCS12 `
  -alias upload -keyalg RSA -keysize 4096 -validity 10000
```
- 4096-bit RSA; 10,000 days is about 27 years (Play asks for at least 25).
- keytool asks for a password (twice) and your name/organisation; the answers go into the key's
  certificate. With PKCS12 the key password is the same as the store password.
- The file is **outside the project**. `.gitignore` also blocks `*.jks`, `*.keystore`, `*.p12`,
  `*.pem` and `keystore.properties` as a second lock.

**Keep two offline copies** of `dokanhisab-upload.jks`, for example one on a USB stick at home and
one on a second USB stick or an external disk kept somewhere else (not the shop). Write the password
on paper and keep it apart from the sticks. Do not email it or put it in a chat.

## 2. keystore.properties (project root, never committed)

```properties
storeFile=C:/Users/Ibrahim/Keys/dokanhisab-upload.jks
keyAlias=upload
# Better: leave the two password lines out and set them in the terminal (below).
storePassword=
keyPassword=
```
`app/build.gradle.kts` reads it. A password left empty is taken from the environment variables
`DOKAN_STORE_PASSWORD` / `DOKAN_KEY_PASSWORD`, so the password never has to be in a file:

```powershell
$p = Read-Host "Upload key password" -AsSecureString
$env:DOKAN_STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($p))
$env:DOKAN_KEY_PASSWORD = $env:DOKAN_STORE_PASSWORD
```
(These live only in that PowerShell window and are gone when it closes.)

If `keystore.properties` is missing, or something in it is missing or wrong, the release is built
**unsigned** with a warning (`app-release-unsigned.apk`) and debug builds are never blocked.

## 3. Building

```powershell
.\gradlew.bat :app:bundleRelease     # app\build\outputs\bundle\release\app-release.aab  -> upload to Google Play
.\gradlew.bat :app:assembleRelease   # app\build\outputs\apk\release\app-release.apk     -> install on your own phone
```
R8 shrinking and optimisation are on (never add `packageScope`: it crashed the release once).
Sizes at Group D1: unsigned release APK **2,521,408 bytes (2.4 MB)**, release AAB **about 5.3 MB**
(Play then delivers a smaller APK per phone), debug APK about 14.5 MB.

Check a signed APK: `& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk`

A sideloaded APK (upload key) and the Play Store app (Google's key) cannot replace each other; see
`docs/MOVE_TO_NEW_APP.md`, last section.

## 4. mapping.txt: keep one for every release

R8 renames classes, so a crash report from Play shows names like `a.b.c`. The key to read them is
`app\build\outputs\mapping\release\mapping.txt`, written by every release build. **For each version
you upload**, copy it next to your keys, for example `C:\Users\Ibrahim\Keys\mapping\1.0.0-1\mapping.txt`
(Play also accepts it in the Console under the release, "App bundle explorer → Downloads →
ReTrace mapping file"). The AAB already carries a copy, but keep your own: a rebuild of the same code
does not give the same file.

## 5. What the app asks for (merged release manifest, Group D1)

| Item | Why it is there |
|---|---|
| Permission `com.taqwatech.dokanhisab.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Added by androidx.core. A *signature* permission only this app holds, used so the library's own in-app broadcasts cannot be sent by other apps. Not shown to the user. |
| No other permission | No INTERNET, no contacts, no storage, no camera. |
| `MainActivity`, exported | The launcher icon; every app's start screen must be exported. |
| `androidx.profileinstaller.ProfileInstallReceiver`, exported | Lets Google Play / the system install the startup speed-up profile. Protected by the system-only `DUMP` permission, so normal apps cannot call it. |
| `FileProvider`, not exported | Gives the share sheet temporary read access to one file at a time from `cache/shared-backups/` and `cache/pdf/`. |
| `androidx.startup.InitializationProvider`, not exported | Library start-up (lifecycle, profile installer). The emoji-font step is removed. |
| `androidx.room.MultiInstanceInvalidationService`, not exported | Part of Room; unused (single process), cannot be reached from outside. |

- **Contact picker:** the system picker (`ACTION_PICK`) hands back only the one contact the user
  chooses, with a temporary grant, so **READ_CONTACTS is not needed**.
- **No network, no analytics, no ads:** no INTERNET permission and no network/analytics/ads library
  (checked by `ReleaseSetupTest`).
- **Backup:** `allowBackup="true"` with `backup_rules.xml` and `data_extraction_rules.xml`: only
  `dokan_hisab.db`, `dokan_hisab.db-wal` and `backup_prefs.xml` (checked by `BackupRulesTest`).
- **Not debuggable in release** (checked with aapt and `run-as`, which refuses the release app).
- **No logs:** the app code has no `Log`, `println` or `printStackTrace` calls, so no customer name,
  phone number or amount can reach the system log.
