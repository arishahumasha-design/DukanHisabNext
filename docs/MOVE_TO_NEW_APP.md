# Moving the shop's data to the new app (one time only)

Until Group D the app was installed as `com.example.dukanhisabnext` (a test name). From now on its
permanent name is **`com.taqwatech.dokanhisab`**, the name Google Play will know it by.

For Android these are **two different apps**. The new app cannot read the old app's data, and the
old app cannot be "upgraded" into the new one. The data moves only one way: **a backup from the old
app, restored in the new app.** Nothing else (no copying of files, no cloud sync) is needed or used.

> **Never uninstall the old app until step 6 says so.** Uninstalling it deletes its data for good.

---

## Part A: for the shop owner (what to do, in order)

**You need:** the phone with the old app, a computer with this project, a USB cable, and about 30 minutes
on a quiet day (no sales during the move).

### Step 1: Update the OLD app (keep its data)
Install the newest build of the old app **over** the existing one (see Part B, step 1, for the exact
commands). Do **not** uninstall it first.
- Afterwards the old app's name under its icon is **"দোকান হিসাব (পুরনো)"** ("old").
- Open it once. It updates its database by itself; this may take a few seconds.
- Check that your shop, your customers and today's numbers are still there.

### Step 2: Make a backup in the old app and save it to Google Drive
1. In the old app: **আরও → সেটিংস → ব্যাকআপ ও রিস্টোর → ব্যাকআপ ফাইল সংরক্ষণ করুন**.
2. Read the warning and tap **বুঝেছি, এগিয়ে যান**, then save the file (`DokanHisab-backup-….zip`).
3. Then tap **শেয়ার করুন** and choose **Drive**, so a copy is in your Google Drive too.
   (The file contains every customer's name, mobile number and money: keep it only in your own Drive.)

### Step 3: Write down the numbers of the old app
On paper or with screenshots, note from the old app:
- **হোম:** মোট পাবো.
- **খাতা:** পাবো, অগ্রিম, নিট, and each customer's amount.
- **রিপোর্ট → এই বছর**, each of the four tabs: বিক্রি (count and মোট বিক্রি), বাকি (পাবো, নিট),
  খরচ (মোট), লাভ-ক্ষতি (the last line).
- **বিক্রির তালিকা → এই বছর:** the line "… টি বিক্রি · মোট ৳ …" and the newest receipt number.

### Step 4: Install the NEW app
Install the new app (see Part B, step 4). Its name is **"দোকান হিসাব"**. Both apps are now on the
phone side by side; the new one starts empty.

### Step 5: Restore the backup in the new app and compare
1. In the new app: **আরও → সেটিংস → ব্যাকআপ ও রিস্টোর → ব্যাকআপ থেকে ফিরিয়ে আনুন**.
2. Choose the backup file from step 2 (from the phone, or from Drive).
3. The app shows "ব্যাকআপে / এখন" counts (বিক্রি, কাস্টমার, পণ্য, খরচ, লেনদেন). Tap
   **হ্যাঁ, এই ব্যাকআপ দিয়ে বদলে দিন**. The app restarts and says **"ডেটা ফিরিয়ে আনা হয়েছে"**.
4. Open the same screens as in step 3 and compare every number. **They must all be the same.**
5. The new app will ask you to make a backup ("আপনি ৭ দিনের বেশি ব্যাকআপ নেননি"), because it has
   never made one itself. Make one now and share it to Drive.
6. Enter your shop details if they are missing (সেটিংস).

If any number differs: **stop**, keep both apps, and do not sell in the new app yet.

### Step 6: Only now remove the old app
When every number matched and the new app has made its own backup, uninstall
**"দোকান হিসাব (পুরনো)"** (long-press the icon → Uninstall). Keep the backup files in Drive.

From now on, use only **"দোকান হিসাব"**.

---

## Part B: technical details (for the developer)

Commands are for Windows PowerShell in the project folder. Find the phone's id with `adb devices`
and use it as `<phone-id>` (never install to "all devices").

**Step 1: the old app, updated in place.**
```powershell
.\gradlew.bat :app:assembleDebug -PoldAppId=true
adb -s <phone-id> install -r app\build\outputs\apk\debug\app-debug.apk
```
- `-PoldAppId=true` builds the latest code with `applicationId = com.example.dukanhisabnext`, no
  `.debug` suffix and the label "দোকান হিসাব (পুরনো)" (`app/build.gradle.kts`). It exists only for this move.
- `install -r` replaces the app and **keeps its data**. On first start Room runs every migration
  from the phone's database version (version 1 on the real phone) up to version 5 (`ALL_MIGRATIONS` in `data/`,
  checked by `MigrationTest`). Never `fallbackToDestructiveMigration`, never uninstall.
- The old app was installed from Android Studio, so it is signed with this PC's debug key
  (`%USERPROFILE%\.android\debug.keystore`). The update must be built **on the same PC**. If adb says
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the signatures differ: **stop, do not uninstall**, and ask.

**Step 2: the backup.** The zip holds `dokan_hisab.db`, a manifest (format version, database version,
counts, app version) and a checksum (`docs/DATA_FORMAT.md`). It has no package-name check, so a backup
from the old id restores in the new id.

**Step 4: the new app.** Either the Play Store build (preferred, see the note below) or a
release APK signed with the upload key (`docs/RELEASE.md`):
```powershell
.\gradlew.bat :app:assembleRelease
adb -s <phone-id> install app\build\outputs\apk\release\app-release.apk
```
(Without `keystore.properties` the file is `app-release-unsigned.apk`, which a phone will not install.)

**Step 5: the restore** uses the normal restore path (checks, safety copy, atomic swap, restart).
`backup_prefs.xml` is not part of the backup file, so the new app has no "last backup" date: that is
why it shows the backup reminder until it makes its own backup.

### Proof on the emulator (Group D1)
On `emulator-5554` the old app (`com.example.dukanhisabnext`, with test data) was updated in place
with the `-PoldAppId=true` build. A backup was then made in it, and the new release-id build
(`com.taqwatech.dokanhisab`, R8 on) was installed next to it and restored from that backup. Home,
Khata, the four reports for "এই বছর" and the sales list (all 15 receipt numbers) were recorded line
by line in both apps: 288 lines each, all equal. The only difference was the expected backup reminder
in the new app. This is also the **"lost phone" test**: a backup file alone was enough to rebuild the
whole shop in a fresh app.

---

## Note: a sideloaded APK and the Play Store app cannot replace each other

Android only lets an app be updated by an APK signed with **the same key**.
- An APK you build and install yourself (`app-release.apk`) is signed with **your upload key**.
- An app installed from **Google Play** is signed by Google with the **app signing key** (Play App Signing).

These two keys are different, so a Play update cannot install over a sideloaded app, and a sideloaded APK
cannot install over the Play app (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Switching between them always
means: **make a backup → uninstall → install the other one → restore the backup.**

So: if the Play Store release is near, it is simpler to wait and install the new app from Play (for
example from an internal testing track) in step 4, and do the move only once.
