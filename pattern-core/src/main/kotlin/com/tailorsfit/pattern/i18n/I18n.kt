package com.tailorsfit.pattern.i18n

import java.util.Locale

/** Languages the app is translated into. Add a table in [I18n.tables] to add one. */
enum class Language(val code: String, val nativeName: String) {
    EN("en", "English"),
    HI("hi", "हिन्दी"),
    TE("te", "తెలుగు"),
    ;

    companion object {
        fun fromCode(code: String?): Language? = entries.firstOrNull { it.code == code }
    }
}

/**
 * All user-visible text of the app and the pattern engine, looked up by key in the current
 * [language]. Missing translations fall back to English, then to the key itself. Templates use
 * [String.format] placeholders (`%s`, `%d`, `%1$s`).
 */
object I18n {
    @Volatile
    var language: Language = Language.EN

    val tables: Map<Language, Map<String, String>> = mapOf(
        Language.EN to StringsEn.strings,
        Language.HI to StringsHi.strings,
        Language.TE to StringsTe.strings,
    )

    fun has(key: String) = StringsEn.strings.containsKey(key)

    fun t(key: String, vararg args: Any?): String {
        val template = tables[language]?.get(key) ?: StringsEn.strings[key] ?: return key
        return if (args.isEmpty()) template.replace("%%", "%") else String.format(Locale.US, template, *args)
    }

    /** "1 customer" / "5 customers": uses `<base>.one` when [n] is 1, else `<base>.other`. */
    fun plural(base: String, n: Int): String = t(if (n == 1) "$base.one" else "$base.other", n)
}

/** Short alias used throughout the code base. */
fun tr(key: String, vararg args: Any?): String = I18n.t(key, *args)
