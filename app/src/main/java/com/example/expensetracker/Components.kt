package com.example.expensetracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Month switcher: ‹ October 2026 ›  (tap the label for a custom date range). Used on Home / Entries / Analytics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodBar(vm: AppViewModel) {
    val period by vm.period.collectAsState()
    val offset by vm.monthOffset.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Row(
        Modifier.fillMaxWidth().clip(shape).background(Th.surface).border(1.dp, Th.border, shape).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!period.isCustom) TextButton(onClick = { vm.previousMonth() }) { Text("‹", fontSize = 24.sp, color = Th.primary) }
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { showPicker = true }.padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(period.label, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Th.text)
            Text(if (period.isCustom) "Custom range • tap to change" else "tap for custom range", fontSize = 10.sp, color = Th.textMuted)
        }
        if (period.isCustom) TextButton(onClick = { vm.clearCustomRange() }) { Text("Reset", color = Th.primary, fontWeight = FontWeight.Bold) }
        else TextButton(onClick = { vm.nextMonth() }, enabled = offset > 0) { Text("›", fontSize = 24.sp, color = if (offset > 0) Th.primary else Th.border) }
    }

    if (showPicker) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = state.selectedStartDateMillis
                    val e = state.selectedEndDateMillis
                    if (s != null && e != null) vm.setCustomRange(utcPickedToLocalStart(s), endOfDay(utcPickedToLocalStart(e)))
                    showPicker = false
                }) { Text("Apply", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) {
            DateRangePicker(state = state, modifier = Modifier.height(480.dp), showModeToggle = false)
        }
    }
}

@Composable
fun TxRow(tx: Transaction, onClick: () -> Unit) {
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryAvatar(tx.txCategory)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.description, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Th.text)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${tx.txCategory} • ${tx.mode} • ${timeFmt.format(Date(tx.timestamp))}", fontSize = 11.sp, color = Th.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (tx.source == "SMS") { Spacer(Modifier.width(6.dp)); SmallTag("SMS", Th.info) }
                if (tx.source == "RECURRING") { Spacer(Modifier.width(6.dp)); SmallTag("AUTO", Th.warning) }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            (if (tx.type == "CREDIT") "+" else "-") + money(tx.amount),
            fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
            color = if (tx.type == "CREDIT") Th.income else Th.expense
        )
    }
}

/** Adds one card per day (with a "Today / Yesterday / date" header and the day's net) to a LazyColumn. */
fun LazyListScope.txDayGroups(txs: List<Transaction>, onClick: (Transaction) -> Unit) {
    txs.groupBy { dayKey(it.timestamp) }.forEach { (key, list) ->
        item(key = "day_$key") {
            Column {
                val net = list.sumOf { if (it.type == "CREDIT") it.amount else -it.amount }
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(dayHeader(list.first().timestamp), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Th.textMuted)
                    Text(signedMoney(net), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Th.textMuted)
                }
                AppCard(padding = 8.dp) {
                    list.forEachIndexed { i, t ->
                        if (i > 0) Hairline()
                        TxRow(t) { onClick(t) }
                    }
                }
            }
        }
    }
}

/** Rapido net = Rapido earnings - recharge - subscription (- any other debit entered with mode RAPIDO). No petrol. */
@Composable
fun RapidoCard(txs: List<Transaction>) {
    val trips = txs.filter { it.type == "CREDIT" && it.mode == "RAPIDO" }
    val costs = txs.filter { isRapidoCost(it) }
    val earned = trips.sumOf { it.amount }
    val recharge = costs.filter { it.txCategory == RAPIDO_RECHARGE }.sumOf { it.amount }
    val subscription = costs.filter { it.txCategory == RAPIDO_SUB }.sumOf { it.amount }
    val other = costs.sumOf { it.amount } - recharge - subscription
    val net = earned - recharge - subscription - other
    AppCard {
        SectionTitle("Rapido net")
        Spacer(Modifier.height(8.dp))
        Text(signedMoney(net), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = if (net >= 0) Th.income else Th.expense)
        Text(
            "${trips.size} trips" + if (trips.isNotEmpty()) " • ${money(net / trips.size)} net per trip" else "",
            fontSize = 12.sp, color = Th.textMuted
        )
        Spacer(Modifier.height(12.dp))
        RapidoLine("Earnings", earned, Th.income, "+")
        RapidoLine("Recharge", recharge, Th.expense, "-")
        RapidoLine("Subscription", subscription, Th.expense, "-")
        if (other > 0.5) RapidoLine("Other Rapido cost", other, Th.expense, "-")
        Spacer(Modifier.height(6.dp))
        Hairline()
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Net", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Th.text)
            Text(signedMoney(net), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = if (net >= 0) Th.income else Th.expense)
        }
    }
}

@Composable
private fun RapidoLine(label: String, value: Double, color: androidx.compose.ui.graphics.Color, sign: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = Th.textMuted)
        Text(sign + money(value), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
