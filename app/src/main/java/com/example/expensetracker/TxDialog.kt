package com.example.expensetracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Add / edit a transaction. Pass [editing] to edit (a Delete button appears). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxDialog(vm: AppViewModel, editing: Transaction?, onDismiss: () -> Unit, onDelete: ((Transaction) -> Unit)? = null) {
    val categories by vm.categories.collectAsState()
    var type by remember { mutableStateOf(editing?.type ?: "CREDIT") }
    var amount by remember {
        mutableStateOf(editing?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "")
    }
    var mode by remember { mutableStateOf(editing?.mode ?: "UBER") }
    var category by remember { mutableStateOf(editing?.txCategory ?: "Rides") }
    var desc by remember { mutableStateOf(editing?.description ?: "") }
    var timestamp by remember { mutableLongStateOf(editing?.timestamp ?: System.currentTimeMillis()) }
    var showDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    AppDialog(onDismiss = onDismiss, title = if (editing == null) "New transaction" else "Edit transaction", subtitle = "Fast entry for income & expenses") {
        // type toggle
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Th.surfaceAlt).padding(4.dp)) {
            listOf("CREDIT" to "Income  +", "DEBIT" to "Expense  −").forEach { (t, label) ->
                val sel = type == t
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                        .background(if (sel) (if (t == "CREDIT") Th.income else Th.expense) else Color.Transparent)
                        .clickable { type = t; if (editing == null) category = if (t == "CREDIT") "Other" else "Petrol" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (sel) Color.White else Th.textMuted) }
            }
        }
        Spacer(Modifier.height(14.dp))

        AppTextField(amount, { amount = it; error = false }, "Amount (₹)", number = true)
        if (error) Text("Enter a valid amount", color = Th.expense, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))

        Spacer(Modifier.height(10.dp))
        Text("QUICK", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Th.textMuted)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val shortcuts = if (type == "CREDIT") listOf(48 to "Cashback", 100 to "Received", 200 to "Trip", 500 to "Salary")
            else listOf(100 to "Fuel", 200 to "Fuel", 20 to "Tea", 500 to "Service")
            items(shortcuts) { (v, label) ->
                Chip("₹$v $label", false, {
                    amount = v.toString()
                    if (label == "Cashback") { category = "Other"; mode = "ONLINE" }
                    if (label == "Fuel") category = "Petrol"
                    if (label == "Tea") category = "Food"
                    desc = "$label ₹$v"
                })
            }
        }

        if (type == "DEBIT") {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf(RAPIDO_RECHARGE, RAPIDO_SUB)) { c ->
                    Chip("${categoryEmoji(c)} $c", category == c, { category = c; mode = "ONLINE"; if (desc.isBlank()) desc = c })
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("PAYMENT MODE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Th.textMuted)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("CASH", "ONLINE", "UBER", "RAPIDO")) { m ->
                Chip(m, mode == m, {
                    mode = m
                    if (desc.isBlank()) desc = if (m == "UBER") "Uber Ride" else if (m == "RAPIDO") "Rapido Ride" else ""
                })
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("CATEGORY", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Th.textMuted)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(categories) { c -> Chip("${categoryEmoji(c)} $c", category == c, { category = c }) }
        }

        Spacer(Modifier.height(12.dp))
        AppTextField(desc, { desc = it }, "Note (kisne diya / kahan kharcha hua)")

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Th.surfaceAlt).clickable { showDate = true }.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("📅  Date & time", color = Th.textMuted, fontSize = 13.sp)
            Text(fmtDateTime(timestamp), color = Th.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }

        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = if (type == "CREDIT") "Save income" else "Save expense",
            color = if (type == "CREDIT") Th.income else Th.expense, contentColor = Color.White,
            onClick = {
                val amt = amount.replace(",", "").toDoubleOrNull()
                if (amt == null || amt <= 0.0) { error = true; return@PrimaryButton }
                val finalDesc = desc.ifBlank { if (type == "CREDIT") "$mode income" else category }
                if (editing == null) vm.addTransaction(amt, finalDesc, type, mode, category, timestamp)
                else vm.updateTransaction(editing, amt, finalDesc, type, mode, category, timestamp)
                onDismiss()
            }
        )
        if (editing != null && onDelete != null) {
            TextButton(onClick = { onDelete(editing); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete entry", color = Th.expense, fontWeight = FontWeight.Bold)
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = Th.textMuted) }
    }

    if (showDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = localToUtcDay(timestamp))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = { st.selectedDateMillis?.let { timestamp = mergeDate(timestamp, it) }; showDate = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = st) }
    }
}
