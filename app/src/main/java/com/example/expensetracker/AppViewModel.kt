package com.example.expensetracker

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Period(val label: String, val start: Long, val end: Long, val isCustom: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getDatabase(app)
    private val txDao = db.transactionDao()
    private val loanDao = db.loanDao()
    private val recurringDao = db.recurringDao()
    val prefs = AppPrefs(app)

    /** app-lock state lives here so it survives rotation */
    var unlocked by mutableStateOf(false)

    // ---------------- period selection ----------------
    val monthOffset = MutableStateFlow(0)
    val customRange = MutableStateFlow<Pair<Long, Long>?>(null)

    private fun makePeriod(offset: Int, custom: Pair<Long, Long>?): Period {
        if (custom != null) {
            val f = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            return Period("${f.format(Date(custom.first))} – ${f.format(Date(custom.second))}", custom.first, custom.second, true)
        }
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -offset) }
        val (s, e) = monthBoundsOf(cal.timeInMillis)
        return Period(monthLabel(s), s, e, false)
    }

    private fun previousOf(p: Period): Pair<Long, Long> {
        if (p.isCustom) {
            val len = p.end - p.start + 1
            return (p.start - len) to (p.start - 1)
        }
        val cal = Calendar.getInstance().apply { timeInMillis = p.start; add(Calendar.MONTH, -1) }
        return monthBoundsOf(cal.timeInMillis)
    }

    val period: StateFlow<Period> = combine(monthOffset, customRange) { off, custom -> makePeriod(off, custom) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, makePeriod(0, null))

    val transactions: StateFlow<List<Transaction>> = period
        .flatMapLatest { txDao.observeRange(it.start, it.end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val previousTransactions: StateFlow<List<Transaction>> = period
        .flatMapLatest { val (s, e) = previousOf(it); txDao.observeRange(s, e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun previousMonth() { monthOffset.value = monthOffset.value + 1 }
    fun nextMonth() { if (monthOffset.value > 0) monthOffset.value = monthOffset.value - 1 }
    fun setCustomRange(start: Long, end: Long) { customRange.value = start to end }
    fun clearCustomRange() { customRange.value = null }

    // ---------------- loans / recurring ----------------
    val loans: StateFlow<List<LoanRecord>> = loanDao.observeLoans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val payments: StateFlow<List<InterestPayment>> = loanDao.observeAllPayments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rules: StateFlow<List<RecurringRule>> = recurringDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---------------- settings ----------------
    val themeMode = MutableStateFlow(prefs.themeMode)
    val appLock = MutableStateFlow(prefs.appLock)
    val smsTracking = MutableStateFlow(prefs.smsTracking)
    val totalBudget = MutableStateFlow(prefs.totalBudget)
    val customCategories = MutableStateFlow(prefs.customCategories)
    val categories: StateFlow<List<String>> = customCategories
        .map { DEFAULT_CATEGORIES + it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_CATEGORIES + prefs.customCategories)
    val categoryBudgets = MutableStateFlow(
        (DEFAULT_CATEGORIES + prefs.customCategories).associateWith { prefs.categoryBudget(it) }.filterValues { it > 0f }
    )
    val userEmail = MutableStateFlow(FirebaseSync.currentEmail)
    val syncStatus = MutableStateFlow("")

    // ---------------- opening balance & running balance ----------------
    val opening = MutableStateFlow(Opening(prefs.openingCash, prefs.openingBank, prefs.openingDate))

    /** every entry (incl. transfers) from the opening date onwards, oldest first - used to build running balances */
    val balanceTxs: StateFlow<List<Transaction>> = opening
        .flatMapLatest { txDao.observeFrom(it.date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveOpening(cash: Double, bank: Double, date: Long) {
        prefs.openingCash = cash; prefs.openingBank = bank; prefs.openingDate = date
        opening.value = Opening(cash, bank, date)
    }

    fun setTheme(mode: String) { prefs.themeMode = mode; themeMode.value = mode }
    fun setAppLock(on: Boolean) { prefs.appLock = on; appLock.value = on; if (on) unlocked = true }
    fun setSmsTracking(on: Boolean) { prefs.smsTracking = on; smsTracking.value = on }

    fun saveBudgets(total: Float, perCat: Map<String, Float>) {
        prefs.totalBudget = total; totalBudget.value = total
        categories.value.forEach { prefs.setCategoryBudget(it, perCat[it] ?: 0f) }
        categoryBudgets.value = perCat.filterValues { it > 0f }
    }

    fun addCategory(name: String) {
        val n = name.trim()
        if (n.isEmpty() || categories.value.any { it.equals(n, true) }) return
        val list = customCategories.value + n
        prefs.customCategories = list; customCategories.value = list
    }

    fun removeCategory(name: String) {
        val list = customCategories.value - name
        prefs.customCategories = list; customCategories.value = list
    }

    // ---------------- transactions ----------------
    fun addTransaction(amount: Double, desc: String, type: String, mode: String, category: String, timestamp: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val tx = Transaction(amount = amount, type = type, mode = mode, txCategory = category, description = desc, timestamp = timestamp)
            val saved = tx.copy(id = txDao.insertIgnore(tx))
            FirebaseSync.pushTransaction(saved)
            BudgetChecker.check(app, db, prefs, saved)
        }
    }

    fun updateTransaction(old: Transaction, amount: Double, desc: String, type: String, mode: String, category: String, timestamp: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = old.copy(amount = amount, description = desc, type = type, mode = mode, txCategory = category,
                timestamp = timestamp, updatedAt = System.currentTimeMillis())
            txDao.update(t)
            FirebaseSync.pushTransaction(t)
            BudgetChecker.check(app, db, prefs, t)
        }
    }

    fun deleteTransaction(tx: Transaction) = setDeleted(tx, true)
    fun restoreTransaction(tx: Transaction) = setDeleted(tx, false)

    private fun setDeleted(tx: Transaction, deleted: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = tx.copy(isDeleted = deleted, updatedAt = System.currentTimeMillis())
            txDao.update(t)
            FirebaseSync.pushTransaction(t)
        }
    }

    /** cash <-> bank transfer: two linked rows (one debit, one credit) in category Transfer. */
    fun addTransfer(amount: Double, cashToBank: Boolean, timestamp: Long, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val label = if (cashToBank) "Transfer: Cash → Bank" else "Transfer: Bank → Cash"
            val desc = if (note.isBlank()) label else "$label ($note)"
            val out = Transaction(amount = amount, type = "DEBIT", mode = if (cashToBank) "CASH" else "ONLINE",
                txCategory = TRANSFER, description = desc, timestamp = timestamp)
            val inn = Transaction(amount = amount, type = "CREDIT", mode = if (cashToBank) "ONLINE" else "CASH",
                txCategory = TRANSFER, description = desc, timestamp = timestamp)
            FirebaseSync.pushTransaction(out.copy(id = txDao.insertIgnore(out)))
            FirebaseSync.pushTransaction(inn.copy(id = txDao.insertIgnore(inn)))
        }
    }

    /** deletes both halves of a transfer */
    fun deleteTransfer(tx: Transaction) {
        val pair = transactions.value.firstOrNull {
            it.txCategory == TRANSFER && it.id != tx.id && it.timestamp == tx.timestamp && it.amount == tx.amount && it.type != tx.type
        }
        setDeleted(tx, true)
        if (pair != null) setDeleted(pair, true)
    }

    suspend fun allTransactionsForExport(): List<Transaction> = withContext(Dispatchers.IO) { txDao.getAllActive() }

    // ---------------- loans ----------------
    fun addLoan(name: String, amount: Double, type: String, hasInterest: Boolean, rate: Double, startDate: Long, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val l = LoanRecord(personName = name, amount = amount, originalAmount = amount, type = type,
                hasInterest = hasInterest, monthlyRate = rate, startDate = startDate, note = note)
            FirebaseSync.pushLoan(l.copy(id = loanDao.insertLoan(l)))
        }
    }

    fun recordPayment(loan: LoanRecord, paymentType: String, amount: Double, mode: String, date: Long, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val p = InterestPayment(loanId = loan.id, loanUid = loan.uid, amount = amount, paymentType = paymentType,
                paymentMode = mode, paymentDate = date, note = note)
            FirebaseSync.pushPayment(p.copy(id = loanDao.insertPayment(p)))

            if (paymentType == "PRINCIPAL") {
                val nb = (loan.amount - amount).coerceAtLeast(0.0)
                val upd = loan.copy(amount = nb, isSettled = nb <= 0.0, updatedAt = now)
                loanDao.updateLoan(upd)
                FirebaseSync.pushLoan(upd)
            }
            val tx = Transaction(
                amount = amount, type = if (loan.type == "TAKEN") "DEBIT" else "CREDIT", mode = mode, txCategory = "Loan",
                description = (if (paymentType == "INTEREST") "Interest - " else "Principal - ") + loan.personName,
                timestamp = date
            )
            FirebaseSync.pushTransaction(tx.copy(id = txDao.insertIgnore(tx)))
        }
    }

    fun settleLoan(loan: LoanRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            val l = loan.copy(amount = 0.0, isSettled = true, updatedAt = System.currentTimeMillis())
            loanDao.updateLoan(l); FirebaseSync.pushLoan(l)
        }
    }

    fun deleteLoan(loan: LoanRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            val l = loan.copy(isDeleted = true, updatedAt = System.currentTimeMillis())
            loanDao.updateLoan(l); FirebaseSync.pushLoan(l)
        }
    }

    // ---------------- recurring ----------------
    fun addRule(title: String, amount: Double, type: String, mode: String, category: String, day: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            // if this month's day has already passed, start from next month (no surprise back-dated entry)
            val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
            val last = if (today >= day) monthKey(System.currentTimeMillis()) else ""
            recurringDao.insert(RecurringRule(title = title, amount = amount, type = type, mode = mode,
                txCategory = category, dayOfMonth = day, lastRunMonth = last))
            RecurringRunner.run(app, db)
        }
    }

    fun setRuleActive(r: RecurringRule, active: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { recurringDao.update(r.copy(isActive = active)) }
    }

    fun deleteRule(r: RecurringRule) { viewModelScope.launch(Dispatchers.IO) { recurringDao.delete(r) } }

    // ---------------- cloud ----------------
    fun signIn(email: String, pw: String, register: Boolean, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) { if (register) FirebaseSync.register(email, pw) else FirebaseSync.signIn(email, pw) }
            if (err == null) { userEmail.value = FirebaseSync.currentEmail; syncNow() }
            onResult(err)
        }
    }

    fun signOut() { FirebaseSync.signOut(); userEmail.value = null; syncStatus.value = "" }

    fun syncNow() {
        viewModelScope.launch(Dispatchers.IO) {
            syncStatus.value = "Syncing…"
            syncStatus.value = FirebaseSync.syncAll(txDao, loanDao)
        }
    }

    fun importLegacy() {
        viewModelScope.launch(Dispatchers.IO) {
            syncStatus.value = "Importing old cloud data…"
            val r = FirebaseSync.importLegacy(txDao, loanDao)
            syncStatus.value = r
            if (FirebaseSync.isLoggedIn) syncStatus.value = r + " • " + FirebaseSync.syncAll(txDao, loanDao)
        }
    }

    // ---------------- backup ----------------
    suspend fun buildBackup(): String = withContext(Dispatchers.IO) { Backup.toJson(db) }

    suspend fun restoreBackup(json: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching { Backup.restore(db, json) }.also { if (it.isSuccess && FirebaseSync.isLoggedIn) syncNow() }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) { RecurringRunner.run(app, db) }
        if (FirebaseSync.isLoggedIn) syncNow()
    }
}
