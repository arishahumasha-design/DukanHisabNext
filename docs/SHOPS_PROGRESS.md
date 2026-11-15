# Several shops: progress

Work log of the "several fully separate shops" feature, so the work can be resumed after an interruption.
The plan and the decisions are in the Phase 1 report (approved): one database file per shop, a small shop
registry outside the shop databases, switching by restarting the app, per-shop backup.

Decisions: owner name/mobile per shop (a new shop is pre-filled from the open one); restart on switch; backup
names keep the time; real delete (not the open shop, not the last, typed name, "Back up this shop first" is the
primary action, deleting without a backup needs the tick "I do not want a backup"); names unique ignoring case;
at most 20 shops; a shop that cannot be opened is shown as such (restore/remove), never auto-deleted or
auto-created. Extra: the open shop's name is always shown on Home and in Settings; after a switch the app lands
on Home.

## Done

1. Registry model, rules and file format, file layout per shop (shop 1 keeps the old names), ASCII backup
   names (`shop/ShopRegistry.kt`, `shop/ShopPaths.kt`, PC tests `ShopRegistryTest`).
2. Registry store on disk (`shop/ShopStore.kt`: atomic write, .bak, rebuild from files, crash-safe delete).
   PC tests `ShopStoreTest`; phone test `LegacyShopPhoneTest` (the old database registered as shop 1 stays
   byte-identical and shows the same data when opened by path; v4 still converts). IsolatedContext accepts
   full database paths inside its folder.
3. The app runs on the open shop: database (by full path, `AppDatabase.getInstance(context, file)`), cart,
   journal, backup engine and backup settings come from `ShopPaths`; start-up recovery runs for every shop;
   start-up health check (missing / cannot open / quick_check), registry name kept in step with the profile.
   A damaged database file is never deleted any more (`KeepDamagedFileFactory`). Bug fixed on the way: the
   restore opened the prepared copy by name, which only worked for the top databases folder.
   Phone test `ShopIsolationPhoneTest` (sale in A not in B, carts per shop, backup A restored into B, crash
   recovery per shop).
4. `shop/ShopManager.kt`: make a shop (own database + profile), edit any shop (a closed one is opened briefly),
   switch (refused while a backup/restore runs or something is being saved; otherwise the choice is saved and
   the app restarts), delete (not open, not last). PC test `SwitchRulesTest`, phone test `ShopManagerPhoneTest`.
5. Per-shop backups: file name `<Shop-name>-backup-YYYY-MM-DD-HHmm.zip` (Shop-<n> without ASCII letters), the
   optional manifest field `shopName`, the restore dialog names the shop it replaces and warns when the backup is
   from a differently named shop (neutral note for older backups). PC test `BackupShopNameTest`; phone test
   `ShopIsolationPhoneTest` checks the name and the manifest.
6. UI: shop list (`ui/shop/ShopListScreen.kt`: cards, Open with confirm + cover + restart, Edit, Delete with
   "Back up this shop first" as the focused main action, the tick "I do not want a backup" and the typed name),
   new/edit shop form (`ShopScreen` with `ShopFormTarget`; a new shop is pre-filled with the open shop's owner),
   "open now?" after making a shop, open-shop pill on Home (tap opens the list), Settings "Change shop" opens
   the list, `ShopProblemFrame` instead of the app when the open shop's file is gone or unreadable (restore into
   it, or other shops). Strings in Bengali and English. Checked on the emulator: edit, new, switch, Home name.
7. Shops 2+ keep their database in `files/shop-databases/` (lint: Android's automatic backup allows a sub-folder
   only in the files domain); automatic-backup rules cover it and the registry. Settings label "এখন খোলা দোকান".
   "Shop created, open it now?" dialog. Docs: SPEC (Several shops), DATA_FORMAT (`shopName`, file names, section 4),
   MOVE_TO_NEW_APP (restore goes into the open shop), LANGUAGE (new strings). Emulator: backup names per shop,
   restore warning, delete dialog with backup-first (backed up the closed shop), a missing shop file shown as
   "can't be opened" (nothing created) and repaired by restoring its backup into it.
8. Shop cards: "Open" on its own line, Edit/Delete share the next (long Bengali labels no longer squeeze).
   Whole phone suite: 300 tests OK from a Bengali and from an English start; PC tests 1035; lint 0 errors.

## Next

Nothing left of the approved plan. Possible later work: see Open questions.

## Open questions

- Backup settings of shops 2+ (`backup_prefs_shop_<n>.xml`) are not in Android's automatic backup (a file name
  cannot be a pattern); only the last-backup time and reminder state are in it.
- The switch shows the app's green start screen during the restart (about 1 s); the in-app cover is brief.
