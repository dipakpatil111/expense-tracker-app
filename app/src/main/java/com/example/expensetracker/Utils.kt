package com.example.expensetracker

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

private val inrFormat = ThreadLocal.withInitial {
    NumberFormat.getNumberInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0; minimumFractionDigits = 0 }
}

fun money(v: Double): String {
    val s = inrFormat.get()!!.format(abs(v))
    return (if (v <= -0.5) "-" else "") + "₹" + s
}

fun signedMoney(v: Double): String = (if (v >= 0.5) "+" else "") + money(v)

const val DAY_MS = 86_400_000L

fun monthBoundsOf(ts: Long): Pair<Long, Long> {
    val cal = Calendar.getInstance().apply {
        timeInMillis = ts
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val start = cal.timeInMillis
    cal.add(Calendar.MONTH, 1)
    return start to (cal.timeInMillis - 1)
}

fun monthLabel(ts: Long): String = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(ts))
fun fmtDate(ts: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ts))
fun fmtDateTime(ts: Long): String = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(ts))
fun dayKey(ts: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ts))
fun monthKey(ts: Long): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(ts))

fun startOfDay(ts: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ts
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun endOfDay(ts: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(ts)
    add(Calendar.DAY_OF_YEAR, 1)
}.timeInMillis - 1

fun dayHeader(ts: Long): String {
    val today = startOfDay(System.currentTimeMillis())
    val d = startOfDay(ts)
    return when (d) {
        today -> "Today"
        startOfDay(today - 1) -> "Yesterday"
        else -> SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(Date(ts))
    }
}

fun isWeekend(timestamp: Long): Boolean {
    val day = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.DAY_OF_WEEK)
    return day == Calendar.SATURDAY || day == Calendar.SUNDAY
}

/**
 * FIX: Material DatePicker returns UTC-midnight millis. Using it directly (like the old code did) made
 * the range start at 05:30 AM IST and silently dropped early-morning entries of the first day.
 */
fun utcPickedToLocalStart(utc: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    return Calendar.getInstance().apply {
        clear()
        set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

fun localToUtcDay(ts: Long): Long {
    val l = Calendar.getInstance().apply { timeInMillis = ts }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

/** Keep the time-of-day of [existingTs] but move it to the day picked in a DatePicker. */
fun mergeDate(existingTs: Long, utcPicked: Long): Long {
    val t = Calendar.getInstance().apply { timeInMillis = existingTs }
    return Calendar.getInstance().apply {
        timeInMillis = utcPickedToLocalStart(utcPicked)
        set(Calendar.HOUR_OF_DAY, t.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, t.get(Calendar.MINUTE))
        set(Calendar.SECOND, t.get(Calendar.SECOND))
    }.timeInMillis
}

/* ---------- Interest (simple interest, per-month rate, pro-rated by days/30) ---------- */

data class InterestSummary(val accrued: Double, val paid: Double, val pending: Double, val months: Double)

/**
 * NEW: accrued interest is calculated piece-by-piece: every principal repayment changes the balance
 * that later interest is charged on. pending = accrued - interest already paid.
 */
fun calcInterest(loan: LoanRecord, payments: List<InterestPayment>, now: Long = System.currentTimeMillis()): InterestSummary {
    if (!loan.hasInterest || loan.monthlyRate <= 0.0) return InterestSummary(0.0, 0.0, 0.0, 0.0)
    var balance = loan.originalAmount
    var cursor = loan.startDate
    var accrued = 0.0
    payments.filter { it.paymentType == "PRINCIPAL" }.sortedBy { it.paymentDate }.forEach { p ->
        val t = maxOf(p.paymentDate, cursor)
        accrued += balance * loan.monthlyRate / 100.0 * ((t - cursor) / DAY_MS.toDouble() / 30.0)
        balance = (balance - p.amount).coerceAtLeast(0.0)
        cursor = t
    }
    val end = if (loan.isSettled) cursor else maxOf(now, cursor)
    accrued += balance * loan.monthlyRate / 100.0 * ((end - cursor) / DAY_MS.toDouble() / 30.0)
    val paid = payments.filter { it.paymentType == "INTEREST" }.sumOf { it.amount }
    val months = (end - loan.startDate) / DAY_MS.toDouble() / 30.0
    return InterestSummary(accrued, paid, (accrued - paid).coerceAtLeast(0.0), months)
}

/** A debit that belongs to Rapido: recharge, subscription, or anything entered with mode RAPIDO. */
fun isRapidoCost(t: Transaction): Boolean =
    t.type == "DEBIT" && (t.mode == "RAPIDO" || t.txCategory == RAPIDO_RECHARGE || t.txCategory == RAPIDO_SUB)

/* ---------- cash / bank running balance ---------- */

/** Category used for cash <-> bank transfers. Transfers move money between cash and bank, they are NOT income or expense. */
const val TRANSFER = "Transfer"
fun isTransfer(t: Transaction): Boolean = t.txCategory == TRANSFER

data class Opening(val cash: Double, val bank: Double, val date: Long) {
    val isSet: Boolean get() = date > 0L
}

data class Bal(val cash: Double, val bank: Double) {
    val total: Double get() = cash + bank
}

/**
 * Balance after all entries with timestamp <= [until]. [txs] must only contain entries from the opening date onwards.
 * CASH mode entries move the cash balance, every other mode (ONLINE / UBER / RAPIDO) moves the bank balance.
 */
fun balanceAt(o: Opening, txs: List<Transaction>, until: Long): Bal {
    var cash = o.cash
    var bank = o.bank
    for (t in txs) {
        if (t.timestamp > until) continue
        val d = if (t.type == "CREDIT") t.amount else -t.amount
        if (t.mode == "CASH") cash += d else bank += d
    }
    return Bal(cash, bank)
}
