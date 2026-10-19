package com.example.expensetracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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

@Composable
fun KhataScreen(vm: AppViewModel, showAddLoan: Boolean, onAddLoanDismiss: () -> Unit) {
    val context = LocalContext.current
    val loans by vm.loans.collectAsState()
    val allPayments by vm.payments.collectAsState()
    val payByLoan = remember(allPayments) { allPayments.groupBy { it.loanId } }

    var filter by remember { mutableStateOf("ACTIVE") }
    var payFor by remember { mutableStateOf<Pair<LoanRecord, String>?>(null) }
    var passbook by remember { mutableStateOf<LoanRecord?>(null) }
    var deleteLoan by remember { mutableStateOf<LoanRecord?>(null) }
    var settleLoan by remember { mutableStateOf<LoanRecord?>(null) }

    val active = loans.filter { !it.isSettled }
    val owe = active.filter { it.type == "TAKEN" }.sumOf { it.amount }
    val get = active.filter { it.type == "GIVEN" }.sumOf { it.amount }
    val pendingTaken = active.filter { it.type == "TAKEN" }.sumOf { calcInterest(it, payByLoan[it.id] ?: emptyList()).pending }
    val pendingGiven = active.filter { it.type == "GIVEN" }.sumOf { calcInterest(it, payByLoan[it.id] ?: emptyList()).pending }
    val shown = when (filter) { "ACTIVE" -> active; "SETTLED" -> loans.filter { it.isSettled }; else -> loans }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenTitle("Khata", "Udhaar & interest book") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppCard(Modifier.weight(1f), padding = 14.dp) {
                    Text("YOU OWE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Th.textMuted)
                    Text(money(owe), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Th.expense)
                    if (pendingTaken > 0) Text("+ ${money(pendingTaken)} interest due", fontSize = 10.sp, color = Th.textMuted)
                }
                AppCard(Modifier.weight(1f), padding = 14.dp) {
                    Text("YOU WILL GET", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Th.textMuted)
                    Text(money(get), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Th.income)
                    if (pendingGiven > 0) Text("+ ${money(pendingGiven)} interest due", fontSize = 10.sp, color = Th.textMuted)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ACTIVE" to "Active", "SETTLED" to "Settled", "ALL" to "All").forEach { (k, l) -> Chip(l, filter == k, { filter = k }) }
            }
        }
        if (shown.isEmpty()) {
            item { AppCard { Text("No records here. Tap  + Add record  to start.", color = Th.textMuted, fontSize = 13.sp) } }
        }
        items(shown, key = { it.id }) { loan ->
            val pays = payByLoan[loan.id] ?: emptyList()
            val s = remember(loan, pays) { calcInterest(loan, pays) }
            val taken = loan.type == "TAKEN"
            val accent = if (taken) Th.expense else Th.income
            AppCard(onClick = { passbook = loan }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Text(loan.personName.take(1).uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = accent)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(loan.personName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Th.text)
                        Text(
                            (if (taken) "Udhaar liya" else "Udhaar diya") + " • " + fmtDate(loan.startDate) + if (loan.isSettled) " • Settled" else "",
                            fontSize = 11.sp, color = Th.textMuted
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(loan.amount), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = accent)
                        Text("of ${money(loan.originalAmount)}", fontSize = 10.sp, color = Th.textMuted)
                    }
                }
                if (loan.hasInterest) {
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Th.surfaceAlt).padding(12.dp)) {
                        Text("${loan.monthlyRate}% / month  •  ${money(loan.amount * loan.monthlyRate / 100)} per month on balance", fontSize = 11.sp, color = Th.textMuted)
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Accrued ${money(s.accrued)}  •  Paid ${money(s.paid)}", fontSize = 11.sp, color = Th.textMuted)
                            if (!loan.isSettled) Text(
                                if (s.pending >= 1) "Pending ${money(s.pending)}" else "Up to date ✓",
                                fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (s.pending >= 1) Th.warning else Th.income
                            )
                        }
                    }
                }
                if (!loan.isSettled) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (loan.hasInterest) Chip(if (taken) "Pay interest" else "Got interest", false, { payFor = loan to "INTEREST" })
                        Chip(if (taken) "Repay" else "Got back", false, { payFor = loan to "PRINCIPAL" })
                        Chip("Settle", false, { settleLoan = loan })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { shareText(context, loanStatement(loan, pays, s)) }) { Text("Share statement", fontSize = 12.sp) }
                    TextButton(onClick = { deleteLoan = loan }) { Text("Delete", fontSize = 12.sp, color = Th.expense) }
                }
            }
        }
    }

    if (showAddLoan) AddLoanDialog(vm, onAddLoanDismiss)
    payFor?.let { (loan, type) -> PaymentDialog(vm, loan, type, payByLoan[loan.id] ?: emptyList()) { payFor = null } }
    passbook?.let { loan -> PassbookDialog(loan, payByLoan[loan.id] ?: emptyList()) { passbook = null } }
    deleteLoan?.let { l ->
        ConfirmDialog("Delete ${l.personName}?", "This record is removed from all synced devices. Its past transactions stay in your entries.", "Delete",
            { vm.deleteLoan(l); deleteLoan = null }, { deleteLoan = null }, danger = true)
    }
    settleLoan?.let { l ->
        ConfirmDialog("Settle ${l.personName}?", "Marks the principal as fully paid. Pending interest (if any) is not recorded as paid.", "Settle",
            { vm.settleLoan(l); settleLoan = null }, { settleLoan = null })
    }
}

fun loanStatement(loan: LoanRecord, pays: List<InterestPayment>, s: InterestSummary): String {
    val principalPaid = pays.filter { it.paymentType == "PRINCIPAL" }.sumOf { it.amount }
    return buildString {
        appendLine("Udhaar statement - ${loan.personName}")
        appendLine(if (loan.type == "TAKEN") "(Maine liya)" else "(Maine diya)")
        appendLine("Date: ${fmtDate(loan.startDate)}")
        appendLine("Original amount: ${money(loan.originalAmount)}")
        appendLine("Principal paid: ${money(principalPaid)}")
        appendLine("Balance principal: ${money(loan.amount)}")
        if (loan.hasInterest) {
            appendLine("Interest @ ${loan.monthlyRate}% per month")
            appendLine("Interest accrued: ${money(s.accrued)}  |  paid: ${money(s.paid)}")
            appendLine("Interest pending: ${money(s.pending)}")
            appendLine("TOTAL DUE: ${money(loan.amount + s.pending)}")
        }
        append("As of ${fmtDate(System.currentTimeMillis())}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLoanDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("TAKEN") }
    var hasInterest by remember { mutableStateOf(false) }
    var rate by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var start by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AppDialog(onDismiss, "New khata record") {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Th.surfaceAlt).padding(4.dp)) {
            listOf("TAKEN" to "Udhaar liya", "GIVEN" to "Udhaar diya").forEach { (t, l) ->
                val sel = type == t
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                        .background(if (sel) (if (t == "TAKEN") Th.expense else Th.income) else Color.Transparent)
                        .clickable { type = t }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text(l, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (sel) Color.White else Th.textMuted) }
            }
        }
        Spacer(Modifier.height(12.dp))
        AppTextField(name, { name = it }, "Person name")
        Spacer(Modifier.height(8.dp))
        AppTextField(amount, { amount = it }, "Amount (₹)", number = true)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Charges interest?", color = Th.text, modifier = Modifier.weight(1f))
            Switch(hasInterest, { hasInterest = it }, colors = SwitchDefaults.colors(checkedTrackColor = Th.primary))
        }
        if (hasInterest) AppTextField(rate, { rate = it }, "Interest % per month", number = true)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Th.surfaceAlt).clickable { showDate = true }.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("📅  Start date", color = Th.textMuted, fontSize = 13.sp)
            Text(fmtDate(start), color = Th.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        AppTextField(note, { note = it }, "Note (optional)")
        error?.let { Text(it, color = Th.expense, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Save record", {
            val amt = amount.replace(",", "").toDoubleOrNull()
            val r = if (hasInterest) rate.toDoubleOrNull() else 0.0
            when {
                name.isBlank() -> error = "Enter a name"
                amt == null || amt <= 0.0 -> error = "Enter a valid amount"
                hasInterest && (r == null || r <= 0.0) -> error = "Enter the monthly interest %"
                else -> { vm.addLoan(name.trim(), amt, type, hasInterest, r ?: 0.0, start, note.trim()); onDismiss() }
            }
        })
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = Th.textMuted) }
    }

    if (showDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = localToUtcDay(start))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { st.selectedDateMillis?.let { start = mergeDate(start, it) }; showDate = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = st) }
    }
}

@Composable
fun PaymentDialog(vm: AppViewModel, loan: LoanRecord, type: String, pays: List<InterestPayment>, onDismiss: () -> Unit) {
    val s = remember(loan, pays) { calcInterest(loan, pays) }
    val isInterest = type == "INTEREST"
    val suggested = if (isInterest) (if (s.pending >= 1) s.pending else loan.amount * loan.monthlyRate / 100) else loan.amount
    var amount by remember { mutableStateOf(if (suggested > 0) suggested.toLong().toString() else "") }
    var mode by remember { mutableStateOf("ONLINE") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val taken = loan.type == "TAKEN"

    AppDialog(
        onDismiss,
        (if (isInterest) (if (taken) "Pay interest" else "Receive interest") else (if (taken) "Repay principal" else "Receive principal")) + " • ${loan.personName}",
        if (isInterest) "Pending now ${money(s.pending)}" else "Balance ${money(loan.amount)}"
    ) {
        AppTextField(amount, { amount = it }, "Amount (₹)", number = true)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ONLINE", "CASH").forEach { m -> Chip(m, mode == m, { mode = m }) }
        }
        Spacer(Modifier.height(10.dp))
        AppTextField(note, { note = it }, "Note (optional)")
        error?.let { Text(it, color = Th.expense, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Record payment", {
            val amt = amount.replace(",", "").toDoubleOrNull()
            when {
                amt == null || amt <= 0.0 -> error = "Enter a valid amount"
                !isInterest && amt > loan.amount + 0.5 -> error = "More than the balance (${money(loan.amount)})"
                else -> { vm.recordPayment(loan, type, amt, mode, System.currentTimeMillis(), note.trim()); onDismiss() }
            }
        })
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = Th.textMuted) }
    }
}

@Composable
fun PassbookDialog(loan: LoanRecord, pays: List<InterestPayment>, onDismiss: () -> Unit) {
    val s = remember(loan, pays) { calcInterest(loan, pays) }
    AppDialog(onDismiss, "${loan.personName} • passbook", "Started ${fmtDate(loan.startDate)}") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("BALANCE", money(loan.amount), Th.text, Modifier.weight(1f))
            StatTile("INTEREST PENDING", money(s.pending), if (s.pending >= 1) Th.warning else Th.income, Modifier.weight(1f))
        }
        if (loan.note.isNotBlank()) Text("Note: ${loan.note}", fontSize = 12.sp, color = Th.textMuted, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(14.dp))
        Text("PAYMENTS", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Th.textMuted)
        Spacer(Modifier.height(6.dp))
        if (pays.isEmpty()) Text("No payments yet", fontSize = 13.sp, color = Th.textMuted)
        pays.sortedByDescending { it.paymentDate }.forEachIndexed { i, p ->
            if (i > 0) Hairline()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(if (p.paymentType == "INTEREST") "Interest" else "Principal", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Th.text)
                    Text("${fmtDate(p.paymentDate)} • ${p.paymentMode}" + if (p.note.isNotBlank()) " • ${p.note}" else "", fontSize = 11.sp, color = Th.textMuted)
                }
                Text(money(p.amount), fontWeight = FontWeight.ExtraBold, color = Th.text)
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Close", onDismiss)
    }
}
