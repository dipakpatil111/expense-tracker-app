package com.example.expensetracker

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(vm: AppViewModel, initialSub: String?, onConsumed: () -> Unit) {
    var sub by remember { mutableStateOf(initialSub) }
    LaunchedEffect(Unit) { onConsumed() }
    BackHandler(enabled = sub != null) { sub = null }
    when (sub) {
        "BUDGETS" -> BudgetsScreen(vm) { sub = null }
        "RECURRING" -> RecurringScreen(vm) { sub = null }
        "CATEGORIES" -> CategoriesScreen(vm) { sub = null }
        else -> SettingsHome(vm) { sub = it }
    }
}

@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("‹ Back", color = Th.primary, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(4.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Th.text)
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Th.text)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Th.textMuted)
        }
        trailing()
    }
}

@Composable
private fun SettingsHome(vm: AppViewModel, open: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme by vm.themeMode.collectAsState()
    val appLock by vm.appLock.collectAsState()
    val sms by vm.smsTracking.collectAsState()
    val email by vm.userEmail.collectAsState()
    val status by vm.syncStatus.collectAsState()
    var showAuth by remember { mutableStateOf(false) }
    var confirmLegacy by remember { mutableStateOf(false) }

    val smsPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) vm.setSmsTracking(true) else toast(context, "SMS permission denied")
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val json = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "" }
            val r = vm.restoreBackup(json)
            toast(context, r.fold({ "Restored $it records" }, { "Restore failed: ${it.message}" }))
        }
    }
    val deviceSecure = remember { (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenTitle("More", "Budgets, backup & preferences") }

        item {
            AppCard {
                SectionTitle("Cloud backup & sync")
                Spacer(Modifier.height(6.dp))
                if (email == null) {
                    Text("Sign in to back up your data and use it on more than one phone. Works offline too.", fontSize = 12.sp, color = Th.textMuted)
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Sign in / Create account", { showAuth = true })
                } else {
                    Text("Signed in as $email", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Th.text)
                    if (status.isNotBlank()) Text(status, fontSize = 12.sp, color = Th.textMuted)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Sync now", true, { vm.syncNow() })
                        Chip("Import old cloud data", false, { confirmLegacy = true })
                        Chip("Sign out", false, { vm.signOut() })
                    }
                }
            }
        }

        item {
            AppCard {
                SectionTitle("Plan & organise")
                SettingRow("Budgets", "Monthly + per-category limits with alerts", { open("BUDGETS") }) { Text("›", fontSize = 22.sp, color = Th.textMuted) }
                Hairline()
                SettingRow("Recurring entries", "Rent, EMI, subscriptions – added automatically", { open("RECURRING") }) { Text("›", fontSize = 22.sp, color = Th.textMuted) }
                Hairline()
                SettingRow("Categories", "Add your own categories", { open("CATEGORIES") }) { Text("›", fontSize = 22.sp, color = Th.textMuted) }
            }
        }

        item {
            AppCard {
                SectionTitle("Appearance & security")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEach { (k, l) -> Chip(l, theme == k, { vm.setTheme(k) }) }
                }
                Spacer(Modifier.height(6.dp))
                SettingRow("App lock", if (deviceSecure) "Ask phone PIN / pattern / fingerprint on open" else "Set a screen lock on your phone first") {
                    Switch(appLock, { vm.setAppLock(it) }, enabled = deviceSecure, colors = SwitchDefaults.colors(checkedTrackColor = Th.primary))
                }
                Hairline()
                SettingRow("Auto-track bank SMS", "Detects debit / credit messages") {
                    Switch(sms, { on ->
                        if (on) smsPermLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)) else vm.setSmsTracking(false)
                    }, colors = SwitchDefaults.colors(checkedTrackColor = Th.primary))
                }
            }
        }

        item {
            AppCard {
                SectionTitle("Your data")
                SettingRow("Export full backup (JSON)", "Transactions, khata, recurring rules", {
                    scope.launch { shareTextFile(context, "expense_backup_${dayKey(System.currentTimeMillis())}.json", vm.buildBackup(), "application/json") }
                })
                Hairline()
                SettingRow("Restore from backup file", "Merges with what is already on this phone", { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) })
                Hairline()
                SettingRow("Export all transactions (CSV)", "Open in Excel / Google Sheets", {
                    scope.launch { exportCsv(context, "all", vm.allTransactionsForExport()) }
                })
            }
        }
    }

    if (showAuth) AuthDialog(vm) { showAuth = false }
    if (confirmLegacy) {
        ConfirmDialog(
            "Import old cloud data?",
            "Copies data saved by the previous app version (open 'transactions', 'loans', 'interest_payments' collections) into your account. Do this BEFORE you publish the new Firestore rules. Duplicates are skipped.",
            "Import", { vm.importLegacy(); confirmLegacy = false }, { confirmLegacy = false }
        )
    }
}

private fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

@Composable
private fun AuthDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    AppDialog(onDismiss, if (register) "Create account" else "Sign in", "Your data is stored under your own account") {
        AppTextField(email, { email = it }, "Email")
        Spacer(Modifier.height(8.dp))
        AppTextField(pw, { pw = it }, "Password (min 6 characters)", password = true)
        error?.let { Text(it, color = Th.expense, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(if (loading) "Please wait…" else if (register) "Create account" else "Sign in", {
            if (email.isBlank() || pw.length < 6) { error = "Enter email and a password of 6+ characters"; return@PrimaryButton }
            loading = true; error = null
            vm.signIn(email, pw, register) { err -> loading = false; if (err == null) onDismiss() else error = err }
        }, enabled = !loading)
        TextButton(onClick = { register = !register; error = null }, modifier = Modifier.fillMaxWidth()) {
            Text(if (register) "Already have an account? Sign in" else "New here? Create an account", color = Th.primary)
        }
    }
}

/* ------------------------------ budgets ------------------------------ */

@Composable
private fun BudgetsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val categories by vm.categories.collectAsState()
    val total by vm.totalBudget.collectAsState()
    val budgets by vm.categoryBudgets.collectAsState()
    var totalText by remember { mutableStateOf(if (total > 0f) total.toLong().toString() else "") }
    val catText = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(categories) {
        categories.forEach { c -> if (catText[c] == null) catText[c] = budgets[c]?.toLong()?.toString() ?: "" }
    }
    val context = LocalContext.current

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SubHeader("Budgets", onBack) }
        item {
            AppCard {
                Text("Monthly budget", fontWeight = FontWeight.Bold, color = Th.text)
                Text("You get an alert at 80% and again at 100%. Leave empty for no limit.", fontSize = 12.sp, color = Th.textMuted)
                Spacer(Modifier.height(10.dp))
                AppTextField(totalText, { totalText = it }, "Total monthly limit (₹)", number = true)
            }
        }
        item {
            AppCard {
                Text("Category limits", fontWeight = FontWeight.Bold, color = Th.text)
                Spacer(Modifier.height(10.dp))
                categories.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryAvatar(c, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        AppTextField(catText[c] ?: "", { catText[c] = it }, c, number = true)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        item {
            PrimaryButton("Save budgets", {
                val perCat = categories.associateWith { (catText[it] ?: "").replace(",", "").toFloatOrNull() ?: 0f }
                vm.saveBudgets(totalText.replace(",", "").toFloatOrNull() ?: 0f, perCat)
                toast(context, "Budgets saved")
                onBack()
            })
        }
    }
}

/* ------------------------------ recurring ------------------------------ */

@Composable
private fun RecurringScreen(vm: AppViewModel, onBack: () -> Unit) {
    val rules by vm.rules.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SubHeader("Recurring entries", onBack) }
        item { PrimaryButton("+ Add recurring entry", { showAdd = true }) }
        if (rules.isEmpty()) item { AppCard { Text("Nothing yet. Add rent, EMI, recharge or a salary – it is recorded automatically every month.", fontSize = 13.sp, color = Th.textMuted) } }
        items(rules, key = { it.id }) { r ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryAvatar(r.txCategory)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.title, fontWeight = FontWeight.Bold, color = Th.text)
                        Text("Day ${r.dayOfMonth} of every month • ${r.mode}", fontSize = 11.sp, color = Th.textMuted)
                    }
                    Text((if (r.type == "CREDIT") "+" else "-") + money(r.amount), fontWeight = FontWeight.ExtraBold, color = if (r.type == "CREDIT") Th.income else Th.expense)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (r.isActive) "Active" else "Paused", fontSize = 12.sp, color = Th.textMuted, modifier = Modifier.weight(1f))
                    Switch(r.isActive, { vm.setRuleActive(r, it) }, colors = SwitchDefaults.colors(checkedTrackColor = Th.primary))
                    TextButton(onClick = { vm.deleteRule(r) }) { Text("Delete", color = Th.expense, fontSize = 12.sp) }
                }
            }
        }
    }
    if (showAdd) AddRuleDialog(vm) { showAdd = false }
}

@Composable
private fun AddRuleDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val categories by vm.categories.collectAsState()
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("1") }
    var type by remember { mutableStateOf("DEBIT") }
    var mode by remember { mutableStateOf("ONLINE") }
    var cat by remember { mutableStateOf("Bills") }
    var error by remember { mutableStateOf<String?>(null) }
    AppDialog(onDismiss, "New recurring entry") {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Th.surfaceAlt).padding(4.dp)) {
            listOf("DEBIT" to "Expense", "CREDIT" to "Income").forEach { (t, l) ->
                val sel = type == t
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (sel) (if (t == "CREDIT") Th.income else Th.expense) else Color.Transparent)
                        .clickable { type = t }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text(l, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (sel) Color.White else Th.textMuted) }
            }
        }
        Spacer(Modifier.height(10.dp))
        AppTextField(title, { title = it }, "Title (Rent, EMI, Netflix…)")
        Spacer(Modifier.height(8.dp))
        AppTextField(amount, { amount = it }, "Amount (₹)", number = true)
        Spacer(Modifier.height(8.dp))
        AppTextField(day, { day = it.filter { c -> c.isDigit() }.take(2) }, "Day of month (1-31)")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("CASH", "ONLINE").forEach { m -> Chip(m, mode == m, { mode = m }) } }
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(categories) { c -> Chip("${categoryEmoji(c)} $c", cat == c, { cat = c }) }
        }
        error?.let { Text(it, color = Th.expense, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Save", {
            val amt = amount.replace(",", "").toDoubleOrNull()
            val d = day.toIntOrNull()
            when {
                title.isBlank() -> error = "Enter a title"
                amt == null || amt <= 0.0 -> error = "Enter a valid amount"
                d == null || d !in 1..31 -> error = "Day must be 1 to 31"
                else -> { vm.addRule(title.trim(), amt, type, mode, cat, d); onDismiss() }
            }
        })
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = Th.textMuted) }
    }
}

/* ------------------------------ categories ------------------------------ */

@Composable
private fun CategoriesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val custom by vm.customCategories.collectAsState()
    var name by remember { mutableStateOf("") }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SubHeader("Categories", onBack) }
        item {
            AppCard {
                Text("Add a category", fontWeight = FontWeight.Bold, color = Th.text)
                Spacer(Modifier.height(8.dp))
                AppTextField(name, { name = it }, "e.g. Parking, Toll, Mobile EMI")
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Add", { vm.addCategory(name); name = "" })
            }
        }
        item {
            AppCard {
                Text("Built-in", fontWeight = FontWeight.Bold, color = Th.text)
                Spacer(Modifier.height(8.dp))
                DEFAULT_CATEGORIES.forEach { c ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryAvatar(c, 32.dp); Spacer(Modifier.width(10.dp)); Text(c, color = Th.text)
                    }
                }
            }
        }
        if (custom.isNotEmpty()) item {
            AppCard {
                Text("Yours", fontWeight = FontWeight.Bold, color = Th.text)
                Spacer(Modifier.height(8.dp))
                custom.forEach { c ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryAvatar(c, 32.dp); Spacer(Modifier.width(10.dp))
                        Text(c, color = Th.text, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.removeCategory(c) }) { Text("Remove", color = Th.expense, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}
