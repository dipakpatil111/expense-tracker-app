package com.example.expensetracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Month statement for cash and bank:
 *   Opening  +  Income  -  Expense  +/-  Transfer  =  Closing
 * Closing of one month is exactly the opening of the next month, so it carries forward automatically.
 */
@Composable
fun MonthSummaryCard(period: Period, opening: Opening, balTxs: List<Transaction>) {
    val now = System.currentTimeMillis()
    val inPeriod = balTxs.filter { it.timestamp >= period.start && it.timestamp <= period.end }

    fun sum(cash: Boolean, pick: (Transaction) -> Boolean): Double =
        inPeriod.filter { pick(it) && ((it.mode == "CASH") == cash) }.sumOf { it.amount }

    val open = balanceAt(opening, balTxs, period.start - 1)
    val close = balanceAt(opening, balTxs, period.end)

    val incCash = sum(true) { it.type == "CREDIT" && !isTransfer(it) }
    val incBank = sum(false) { it.type == "CREDIT" && !isTransfer(it) }
    val expCash = sum(true) { it.type == "DEBIT" && !isTransfer(it) }
    val expBank = sum(false) { it.type == "DEBIT" && !isTransfer(it) }
    val trCash = sum(true) { it.type == "CREDIT" && isTransfer(it) } - sum(true) { it.type == "DEBIT" && isTransfer(it) }
    val trBank = sum(false) { it.type == "CREDIT" && isTransfer(it) } - sum(false) { it.type == "DEBIT" && isTransfer(it) }
    val hasTransfer = inPeriod.any { isTransfer(it) }

    AppCard {
        SectionTitle("Balance statement")
        Text(period.label, fontSize = 12.sp, color = Th.textMuted)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1.2f))
            listOf("Cash", "Bank", "Total").forEach {
                Text(it, Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Th.textMuted, textAlign = TextAlign.End)
            }
        }
        Hairline()
        SumRow("Opening", open.cash, open.bank, signed = false, tint = null, bold = false)
        SumRow("+ Income", incCash, incBank, signed = true, tint = Th.income, bold = false)
        SumRow("− Expense", -expCash, -expBank, signed = true, tint = Th.expense, bold = false)
        if (hasTransfer) SumRow("⇄ Transfer", trCash, trBank, signed = true, tint = Th.info, bold = false)
        Hairline()
        SumRow(if (period.end > now) "Balance now" else "Closing", close.cash, close.bank, signed = false, tint = Th.primary, bold = true)
        Spacer(Modifier.height(8.dp))
        Text(
            if (opening.isSet && period.end < opening.date)
                "This period is before your opening balance date (${fmtDate(opening.date)}), so the balance is not tracked here."
            else "This month's closing balance is automatically next month's opening balance.",
            fontSize = 11.sp, color = Th.textMuted
        )
    }
}

@Composable
private fun SumRow(label: String, cash: Double, bank: Double, signed: Boolean, tint: Color?, bold: Boolean) {
    val f: (Double) -> String = { if (signed) signedMoney(it) else money(it) }
    val color = tint ?: Th.text
    val weight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1.2f), fontSize = 12.sp, color = if (bold) Th.text else Th.textMuted, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(f(cash), Modifier.weight(1f), fontSize = 12.sp, color = color, fontWeight = weight, textAlign = TextAlign.End)
        Text(f(bank), Modifier.weight(1f), fontSize = 12.sp, color = color, fontWeight = weight, textAlign = TextAlign.End)
        Text(f(cash + bank), Modifier.weight(1f), fontSize = 12.sp, color = color, fontWeight = weight, textAlign = TextAlign.End)
    }
}

/** Move money between cash in hand and bank (ATM withdrawal / cash deposit). Not counted as income or expense. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var cashToBank by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var timestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    AppDialog(onDismiss, "Transfer", "Move money between cash and bank") {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Th.surfaceAlt).padding(4.dp)) {
            listOf(false to "Bank → Cash\n(ATM withdraw)", true to "Cash → Bank\n(deposit)").forEach { (c2b, label) ->
                val sel = cashToBank == c2b
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (sel) Th.primary else Color.Transparent)
                        .clickable { cashToBank = c2b }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, color = if (sel) Th.onPrimary else Th.textMuted) }
            }
        }
        Spacer(Modifier.height(12.dp))
        AppTextField(amount, { amount = it; error = false }, "Amount (₹)", number = true)
        if (error) Text("Enter a valid amount", color = Th.expense, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
        Spacer(Modifier.height(8.dp))
        AppTextField(note, { note = it }, "Note (optional)")
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Th.surfaceAlt).clickable { showDate = true }.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("📅  Date & time", color = Th.textMuted, fontSize = 13.sp)
            Text(fmtDateTime(timestamp), color = Th.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Save transfer", {
            val amt = amount.replace(",", "").toDoubleOrNull()
            if (amt == null || amt <= 0.0) { error = true; return@PrimaryButton }
            vm.addTransfer(amt, cashToBank, timestamp, note.trim())
            onDismiss()
        })
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = Th.textMuted) }
    }

    if (showDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = localToUtcDay(timestamp))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { st.selectedDateMillis?.let { timestamp = mergeDate(timestamp, it) }; showDate = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = st) }
    }
}
