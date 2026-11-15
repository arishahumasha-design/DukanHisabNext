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

## Next

3. The app runs on the open shop's files; start-up recovery for every shop; health check; isolation tests.
4. Switch (guarded, restart) and delete (crash-safe) with tests.
5. Per-shop backup names, `shopName` in the manifest, restore warning.
6. UI: shop list, new/edit, delete dialog, shop name on Home and in Settings.
7. Docs, full test runs, screenshots.

## Open questions

None yet.
