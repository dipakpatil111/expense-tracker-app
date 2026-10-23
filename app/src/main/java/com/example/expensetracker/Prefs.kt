package com.example.expensetracker

import android.content.Context

const val RAPIDO_RECHARGE = "Rapido Recharge"
const val RAPIDO_SUB = "Rapido Subscription"

val DEFAULT_CATEGORIES = listOf("Petrol", "Food", "Bills", "Rides", RAPIDO_RECHARGE, RAPIDO_SUB, "Maintenance", "Shopping", "Health", "Loan", "Other")

/** Small wrapper around SharedPreferences (budgets, categories, theme, lock, SMS switch). */
class AppPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE)

    var totalBudget: Float
        get() = sp.getFloat("budget_limit", 15000f)
        set(v) { sp.edit().putFloat("budget_limit", v).apply() }

    // opening balance (cash in hand + bank) and the moment it is valid from. openingDate == 0 means "not set".
    var openingCash: Double
        get() = sp.getString("opening_cash", "0")?.toDoubleOrNull() ?: 0.0
        set(v) { sp.edit().putString("opening_cash", v.toString()).apply() }
    var openingBank: Double
        get() = sp.getString("opening_bank", "0")?.toDoubleOrNull() ?: 0.0
        set(v) { sp.edit().putString("opening_bank", v.toString()).apply() }
    var openingDate: Long
        get() = sp.getLong("opening_date", 0L)
        set(v) { sp.edit().putLong("opening_date", v).apply() }

    fun categoryBudget(cat: String): Float = sp.getFloat("cat_budget_$cat", 0f)
    fun setCategoryBudget(cat: String, v: Float) {
        if (v <= 0f) sp.edit().remove("cat_budget_$cat").apply() else sp.edit().putFloat("cat_budget_$cat", v).apply()
    }

    var themeMode: String
        get() = sp.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
        set(v) { sp.edit().putString("theme_mode", v).apply() }

    var appLock: Boolean
        get() = sp.getBoolean("app_lock", false)
        set(v) { sp.edit().putBoolean("app_lock", v).apply() }

    var smsTracking: Boolean
        get() = sp.getBoolean("sms_tracking", true)
        set(v) { sp.edit().putBoolean("sms_tracking", v).apply() }

    var customCategories: List<String>
        get() = (sp.getString("custom_categories", "") ?: "").split("|").filter { it.isNotBlank() }
        set(v) { sp.edit().putString("custom_categories", v.joinToString("|")).apply() }

    /** true only the first time a key is seen -> used so every alert/notification fires once. */
    fun shouldAlert(key: String): Boolean {
        if (sp.getBoolean(key, false)) return false
        sp.edit().putBoolean(key, true).apply()
        return true
    }
}
