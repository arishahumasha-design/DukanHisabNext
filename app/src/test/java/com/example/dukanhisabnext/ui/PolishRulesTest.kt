package com.example.dukanhisabnext.ui

import androidx.compose.ui.graphics.toArgb
import com.example.dukanhisabnext.ui.theme.DarkColorScheme
import com.example.dukanhisabnext.ui.theme.LightColorScheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards for the M9 group B polish that live in source files and resources rather than in pure
 * functions: the pinned Save bars, the capital-letter keyboard hint, the loading rings, the Khata
 * names, the case-insensitive SQL order, the window theme and the portrait lock. They read the
 * project files, so a later change that undoes one of them fails here.
 */
class PolishRulesTest {

    private val main = File("src/main")
    private val ui = File(main, "java/com/example/dukanhisabnext/ui")
    private fun uiFile(path: String) = File(ui, path).readText()
    private fun res(path: String) = File(main, "res/$path").readText()

    // ---- forms ----

    @Test
    fun everyFormKeepsItsMainButtonInABarAboveTheKeyboard() {
        // Forms built on the shared FormWithSaveBar + SaveBar (fields scroll, the bar is pinned):
        for (file in listOf(
            "products/ProductEditScreen.kt", "customers/CustomerEditScreen.kt",
            "settings/OwnerScreen.kt", "shop/ShopScreen.kt"
        )) {
            val text = uiFile(file)
            assertTrue("$file uses FormWithSaveBar", text.contains("FormWithSaveBar("))
            assertTrue("$file uses SaveBar", text.contains("SaveBar("))
            assertFalse("$file has no loose save button in the scrolling part", Regex("""Button\(\s*onClick = viewModel::save""").containsMatchIn(text))
        }
        // The stock screen keeps its history list scrolling above the pinned bar:
        val stock = uiFile("products/StockAdjustScreen.kt")
        assertTrue(stock.contains("SaveBar(") && stock.contains("Modifier.weight(1f)") && stock.contains("imePadding()"))
        // The ledger and expense forms already had the bar (a Surface above the keyboard):
        for (file in listOf("customers/LedgerFormScreen.kt", "expense/ExpenseFormScreen.kt")) {
            val text = uiFile(file)
            assertTrue("$file pinned bar", text.contains("Surface(tonalElevation = 3.dp") && text.contains("imePadding()"))
        }
    }

    @Test
    fun theSharedFormLayoutPutsTheBarOutsideTheScroll() {
        val text = uiFile("components/FormLayout.kt")
        val scrollAt = text.indexOf("verticalScroll(")
        val barAt = text.indexOf("saveBar()")
        assertTrue(scrollAt in 0 until barAt) // the bar is called AFTER (below) the scrolling column
        assertTrue(text.contains("imePadding()"))
    }

    @Test
    fun bigAmountsAskBeforeSaving() {
        assertTrue(uiFile("customers/LedgerFormScreen.kt").contains("needsMoneyConfirm(amountPaisa, LEDGER_CONFIRM_PAISA)"))
        assertTrue(uiFile("expense/ExpenseFormScreen.kt").contains("needsMoneyConfirm(amountPaisa, EXPENSE_CONFIRM_PAISA)"))
        assertTrue(uiFile("products/StockAdjustScreen.kt").contains("needsStockConfirm(change, stock)"))
        assertTrue(uiFile("products/ProductEditScreen.kt").contains("needsStockConfirm(parseQuantity(state.openingStock), null)"))
        val strings = res("values/strings.xml")
        assertTrue(strings.contains(">সংশোধন করুন<") && strings.contains(">হ্যাঁ, সংরক্ষণ করুন<") && strings.contains(">ঠিক আছে?<"))
    }

    @Test
    fun nameFieldsAskForCapitalLetters() {
        for (file in listOf(
            "products/ProductEditScreen.kt", "customers/CustomerEditScreen.kt", "settings/OwnerScreen.kt",
            "shop/ShopScreen.kt", "expense/CategoriesScreen.kt"
        )) {
            assertTrue("$file", uiFile(file).contains("KeyboardCapitalization.Words"))
        }
    }

    // ---- loading ----

    @Test
    fun noScreenShowsNothingWhileLoading() {
        ui.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            assertFalse("${file.name}: 'null -> Unit' shows nothing while loading", Regex("""== null -> Unit""").containsMatchIn(text))
            assertFalse("${file.name}: an empty Box marked still loading", text.contains(") // still loading") && Regex("""Box\([^)]*\)\) // still loading""").containsMatchIn(text))
        }
        assertTrue(uiFile("components/LoadingIndicator.kt").contains("delay(LOADING_DELAY_MS)"))
    }

    // ---- wording ----

    @Test
    fun theKhataHasOneNamePerPlace() {
        val strings = res("values/strings.xml")
        assertTrue(strings.contains("<string name=\"tab_khata\">খাতা</string>"))
        assertTrue(strings.contains("<string name=\"khata_title\">বাকির খাতা</string>"))
        assertFalse("the string 'বাকি খাতা' is gone", strings.contains(">বাকি খাতা<"))
        assertFalse(strings.contains("title_khata"))
        main.walkTopDown().filter { it.extension == "kt" }.forEach {
            assertFalse("${it.name} still uses title_khata", it.readText().contains("title_khata"))
        }
    }

    @Test
    fun oneNamePerThing() {
        val strings = res("values/strings.xml")
        // M9 group C: the shopkeeper chose one word for each of these.
        assertFalse("ক্যাটাগরি everywhere", strings.contains("ক্যাটেগরি"))
        assertFalse("ক্রয়মূল্য everywhere", strings.contains("কেনা দাম"))
        assertFalse("রসিদ নম্বর everywhere", strings.contains("রসিদ নং"))
        assertFalse("লেনদেন in the restore summary", strings.contains("খাতার এন্ট্রি"))
        assertTrue(strings.contains("<string name=\"count_ledger\">লেনদেন</string>"))
        assertTrue(strings.contains("<string name=\"receipt_no\">রসিদ নম্বর</string>"))
        // These stay as they are (the shopkeeper's decision): both total words and the two "নগদ" labels.
        assertTrue(strings.contains("সর্বমোট") && strings.contains("মোট প্রদেয়") && strings.contains("সাব-টোটাল"))
    }

    // ---- sorting ----

    @Test
    fun everyNameOrderInTheDaosIgnoresCase() {
        val daoDir = File(main, "java/com/example/dukanhisabnext/data/dao")
        var found = 0
        daoDir.listFiles { f -> f.extension == "kt" }!!.forEach { file ->
            for (match in Regex("""ORDER BY ([^\n"]*)""").findAll(file.readText())) {
                val order = match.groupValues[1]
                // An ORDER BY on a name column (not a date or an amount) must use NOCASE.
                if (Regex("""\bname\b""").containsMatchIn(order)) {
                    found++
                    assertTrue("${file.name}: ORDER BY $order", order.contains("COLLATE NOCASE"))
                }
            }
        }
        assertTrue("the product, category, customer and expense-category orders were found", found >= 6)
    }

    // ---- window theme and orientation ----

    @Test
    fun theAppIsLockedToPortrait() {
        assertTrue(File(main, "AndroidManifest.xml").readText().contains("android:screenOrientation=\"portrait\""))
    }

    @Test
    fun theDarkWindowThemeExistsAndMatchesTheComposeBackground() {
        val day = res("values/themes.xml")
        val night = res("values-night/themes.xml")
        assertTrue(day.contains("Theme.Material.Light") && day.contains("windowBackground"))
        assertFalse("the night theme must not be a Light one", night.contains("Theme.Material.Light"))
        assertTrue(night.contains("windowBackground"))

        fun colorOf(xml: String) = Regex("""name="window_background">#([0-9A-Fa-f]{8})<""").find(xml)!!.groupValues[1].toLong(16).toInt()
        // The window colour is the colour the Compose theme draws as "background" (Theme.kt).
        assertEquals(LightColorScheme.background.toArgb(), colorOf(res("values/colors.xml")))
        assertEquals(DarkColorScheme.background.toArgb(), colorOf(res("values-night/colors.xml")))
    }
}
