package com.example.dukanhisabnext.expense

import com.example.dukanhisabnext.util.AppLanguage
import com.example.dukanhisabnext.util.CurrentLanguage

// The seven default expense categories (salary, rent...) are created once, with the name of the language that
// was on at that moment, and they carry a stable key (expense_categories.systemKey = the DefaultCategory name).
// So, without any database change, a default the user has NOT renamed can be shown in the current language:
// its stored name is still one of the default names of its key. A category the user created, or a default
// the user renamed, is shown exactly as typed.

/** The default names of every language, in DefaultCategory order (strings.xml "expense_default_categories"). */
object DefaultCategoryNames {
    @Volatile
    var byLanguage: Map<AppLanguage, List<String>> = emptyMap()
}

/**
 * The name to show for a category: [name] as stored, unless it is a default ([systemKey] set) whose stored name
 * is still one of that default's names in any language; then the default's name in the current language.
 */
fun shownCategoryName(name: String, systemKey: String?): String {
    if (systemKey == null) return name
    val index = DefaultCategory.entries.indexOfFirst { it.name == systemKey }
    if (index < 0) return name
    val names = DefaultCategoryNames.byLanguage
    val isUntouchedDefault = names.values.any { list -> list.getOrNull(index)?.let { normalizeCategoryName(it) == normalizeCategoryName(name) } == true }
    if (!isUntouchedDefault) return name // renamed by the user: exactly as typed
    return names[CurrentLanguage.value]?.getOrNull(index) ?: name
}
