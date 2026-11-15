# Google Play: facts for the Play Console forms

App name on Play: see `docs/play/texts-en.md` / `texts-bn.md`. Package: **`com.taqwatech.dokanhisab`**
(permanent). Version **1.0.0 (versionCode 1)**, minSdk 26 (Android 8.0), targetSdk 37.
Placeholders you must fill yourself are written `<LIKE THIS>`.

## 1. Quick answers

| Question | Answer |
|---|---|
| Category | **Business** (Apps, not Games) |
| Free or paid | Free (no in-app purchases, no ads) |
| Contact email (public on the store page) | taqwatech.apps@gmail.com |
| Developer / owner name | Md Ibrahim Howlader (Taqwa Tech Online Center) |
| Privacy policy URL | `<URL where docs/privacy/index.html is hosted, e.g. https://USER.github.io/REPO/>` |
| App access | **All functionality is available without login.** No account, no special instructions. |
| Ads | **No** ads |
| Target audience | **18 and over**; the app is **not for children** and is not designed to appeal to them |
| News app / government app / health app | No / No / No |
| Financial features | See section 6 |
| Languages | Bengali (default), English |

## 2. Permissions, and why

The merged release manifest asks for **no permission that the user sees** (checked by `ReleaseSetupTest`):

| Item | Why |
|---|---|
| No INTERNET | The app never uses the network and has no network library. |
| No contacts / storage / camera / location / microphone | Not needed. A customer's phone number is typed by hand or chosen in the **system contact picker** (the system gives the app only the one contact the user picks). Backups and PDFs are saved or shared through the system file picker and share sheet. |
| `com.taqwatech.dokanhisab.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Added automatically by an AndroidX library. A signature permission only this app holds; never shown to the user. |

Details of exported components: `docs/RELEASE.md`, section 5.

## 3. What data is stored and where

Everything the user types (shop name and address, phone, products, stock, prices, customers' names and phone numbers,
sales, dues, payments, expenses, warranty notes) is stored **only on the user's phone**, in the app's private
storage (a Room/SQLite database per shop plus a few small settings). **We (the developer) never receive any of it.**
The app has no server, no account, no analytics, no crash reporting, no ads, no SDK from any third party.

Files the *user* creates and sends elsewhere by their own choice:
- **Backup file** (`.zip`): a complete copy of one shop's data, including customer names and phone numbers. The user
  saves it or shares it (email, Drive, memory card...). It is not encrypted.
- **PDF reports**: contain customer names, phone numbers and amounts. The app warns about this before creating one.
- **Receipt text**: shared through the system share sheet.

## 4. Android auto-backup (important for the Data safety form)

The manifest has `allowBackup="true"` with explicit rules (`backup_rules.xml` for Android 11 and older,
`data_extraction_rules.xml` for 12+). Only the shop databases (`dokan_hisab.db`, its `-wal`, the databases in
`files/shop-databases/`), the shop list (`files/shop-registry/`) and the small `backup_prefs.xml` are included. The
sale in progress, restore work files and caches are not. This is Android's own backup to the **user's own Google
account** (and phone-to-phone transfer). It is done by the operating system, not by our code, and we cannot read it.
The privacy policy says so. If you prefer no cloud backup at all, set `allowBackup="false"`; the manual backup file stays.

## 5. How a user deletes their data

- **One shop:** Shops screen, the shop, Delete (asks to type the shop's name; offers a backup first). Deletes that
  shop's database for good.
- **Everything:** uninstall the app, or Android Settings, Apps, Dokan Hisab, Storage, Clear storage.
- **Backup files and PDFs** the user made are ordinary files in their own storage, email or Drive; the user deletes them there.
- **Google's cloud copy (auto-backup):** Android Settings, Google, Backup, manage backups.
- Because we hold no data, we cannot delete anything on a user's behalf. The Data safety "delete data" questions are
  answered accordingly (section 7).

## 6. Financial features declaration

Answer: the app **does not provide financial services**. It is a bookkeeping notebook: it records sales, dues and
expenses that the shop owner types in. It does not move money, take or make payments, lend, offer credit, trade,
hold crypto, connect to a bank or wallet, or show real-time prices. "Due / khata" is the shop's own list of what
customers owe the shop. Select **"My app doesn't provide any financial features"** (and none of the sub-options).
If Play asks follow-up questions, this text can be pasted:
*"Dokan Hisab is an offline record-keeping tool for small shops. It stores the user's own sales, customer dues and
expenses on the phone. It performs no payments, transfers, lending or banking, and has no internet access."*

## 7. Data safety form, line by line

Play Console, App content, Data safety.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No.** (Data that never leaves the device is not "collected". We have no INTERNET permission.) |
| Is all of the user data collected by your app encrypted in transit? | Not shown when nothing is collected. (There is no transit.) |
| Do you provide a way for users to request that their data is deleted? | Not shown when nothing is collected. If shown: data is only on the device; the user deletes it in the app or by uninstalling. |
| Data types (location, personal info, financial info, messages, photos, files, contacts, app activity, web browsing, health, device IDs...) | **None collected, none shared.** |
| Third-party libraries/SDKs that collect data | None. |
| Independent security review | No. |

Notes for the reviewer (if asked): customer names, phone numbers and money amounts exist only in the app's private
storage and in files the user chooses to create. Android's system auto-backup to the user's Google account is the
operating system's feature, not collection by the developer.
The form must match the privacy policy; both say the same.

## 8. Content rating (IARC questionnaire)

Category: **Utility, Productivity, Communication or Other** (a business tool). Answer **No** to everything: no violence,
no blood, no sexual content, no profanity, no controlled substances, no gambling or simulated gambling, no user-to-user
communication, no user-generated content shared with others, no location sharing, no purchases of digital goods, no
unrestricted web access. Expected result: **Everyone / PEGI 3 / IARC 3+** (the target audience is still set to adults).

## 9. Store listing checklist

- App name, short and full description, release notes, tester text: `docs/play/texts-en.md`, `texts-bn.md`
  (name limit is 30 characters; both names there were counted).
- Icon 512x512: `docs/play/icon-512.png`. Feature graphic 1024x500: `docs/play/feature-graphic-en.png` / `-bn.png`.
- Phone screenshots: `docs/play/screenshots/en/*.png` and `bn/*.png` (8 each, 1080x2160, Play's 2:1 limit).
- Default language: Bengali; add English as a translation and upload the English texts and pictures there.
- Fictional demo data only; no real person, shop or phone number is shown.

## 10. Testing the release build (before every upload)

The release build is the real app (id `com.taqwatech.dokanhisab`, R8 on). To try an unsigned build on the emulator,
sign a *copy* with the debug key (never use this file for Play):

```powershell
.\gradlew.bat :app:assembleRelease
$bt = "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0"
& "$bt\zipalign.exe" -f -p 4 app\build\outputs\apk\release\app-release-unsigned.apk build\release-aligned.apk
& "$bt\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android build\release-aligned.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 install -r build\release-aligned.apk
```
Then walk the screens in both languages (sell with warranty and back date, cart restore, receipt share, products,
khata, expenses, reports, PDF, backup/restore, shops, settings). The Play pictures and demo data are made with
`tools/play-assets/` and `tools/demo-data/` (`docs/play/README.md`).

## 11. Building the signed AAB, step by step (owner, once the upload key exists)

1. Create the upload key and `keystore.properties` exactly as in `docs/RELEASE.md`, sections 1 and 2 (never in chat,
   never committed).
2. In Windows PowerShell, in the project folder, set the password for this window only:
   ```powershell
   $p = Read-Host "Upload key password" -AsSecureString
   $env:DOKAN_STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($p))
   $env:DOKAN_KEY_PASSWORD = $env:DOKAN_STORE_PASSWORD
   ```
3. Build: `.\gradlew.bat :app:bundleRelease` (no "UNSIGNED" warning must appear).
4. Result: `app\build\outputs\bundle\release\app-release.aab`. Check the signature of the AAB:
   `jarsigner -verify -verbose -certs app\build\outputs\bundle\release\app-release.aab` (jarsigner is in Android Studio's `jbr\bin`).
5. Copy `app\build\outputs\mapping\release\mapping.txt` to `C:\Users\Ibrahim\Keys\mapping\1.0.0-1\mapping.txt`.
6. Play Console: create the app (default language Bengali, app, free), Testing, Closed testing, create release,
   upload the `.aab`, accept Play App Signing, paste the release notes, add testers (Gmail addresses or a Google
   Group), send the tester text. Complete App content (sections 1, 6, 7, 8 above) and the store listing. Privacy policy URL: https://arishahumasha-design.github.io/dokanhisab-privacy/
7. Personal developer accounts created after Nov 2023 must run a closed test with **at least 12 testers for 14 days**
   before applying for production; check the Console's current requirement.
8. Every later upload needs a higher `versionCode` in `app/build.gradle.kts`.

## 12. Dry run without a key (done, Group D2)

`bundleRelease` and `assembleRelease` without `keystore.properties` build fine and **unsigned**; R8 writes
`app\build\outputs\mapping\release\mapping.txt`. Sizes: unsigned release APK 2,789,892 bytes (2.7 MB), release AAB
5,596,644 bytes (5.3 MiB).
