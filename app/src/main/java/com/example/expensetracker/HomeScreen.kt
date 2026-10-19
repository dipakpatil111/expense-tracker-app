package com.example.expensetracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(vm: AppViewModel, onSeeAll: () -> Unit, onEdit: (Transaction) -> Unit, onOpenBudgets: () -> Unit) {
    val txs by vm.transactions.collectAsState()
    val period by vm.period.collectAsState()
    val totalBudget by vm.totalBudget.collectAsState()
    val catBudgets by vm.categoryBudgets.collectAsState()
    var driverFilter by remember { mutableStateOf("COMMUTE") }

    val credits = txs.filter { it.type == "CREDIT" }
    val debits = txs.filter { it.type == "DEBIT" }
    val totalIn = credits.sumOf { it.amount }
    val totalOut = debits.sumOf { it.amount }
    val net = totalIn - totalOut
    val cashIn = credits.filter { it.mode == "CASH" }.sumOf { it.amount }
    val cashOut = debits.filter { it.mode == "CASH" }.sumOf { it.amount }
    val bankIn = totalIn - cashIn
    val bankOut = totalOut - cashOut

    val rides = credits.filter { it.mode == "UBER" || it.mode == "RAPIDO" }
    val shownRides = when (driverFilter) {
        "COMMUTE" -> rides.filter { !isWeekend(it.timestamp) }
        "WEEKEND" -> rides.filter { isWeekend(it.timestamp) }
        else -> rides
    }
    val uber = shownRides.filter { it.mode == "UBER" }.sumOf { it.amount }
    val rapido = shownRides.filter { it.mode == "RAPIDO" }.sumOf { it.amount }
    val petrol = debits.filter { it.txCategory == "Petrol" }.sumOf { it.amount }
    // FIX: old code subtracted the WHOLE month's petrol from only the filtered (commute/weekend) earnings.
    val netAfterFuel = rides.sumOf { it.amount } - petrol

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ScreenTitle("Overview", "Your money at a glance") }
        item { PeriodBar(vm) }

        // ---------- hero ----------
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)))).padding(20.dp)
            ) {
                Column {
                    Text("NET BALANCE", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.7f))
                    Text(money(net), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HeroPill("↓ Income", money(totalIn), Modifier.weight(1f))
                        HeroPill("↑ Expense", money(totalOut), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HeroPill("💵 Cash in hand", money(cashIn - cashOut), Modifier.weight(1f), "in ${money(cashIn)} • out ${money(cashOut)}")
                        HeroPill("📱 Bank / Online", money(bankIn - bankOut), Modifier.weight(1f), "in ${money(bankIn)} • out ${money(bankOut)}")
                    }
                }
            }
        }

        // ---------- budget ----------
        if (!period.isCustom) {
            item {
                AppCard {
                    SectionTitle("Budget", if (totalBudget > 0f || catBudgets.isNotEmpty()) "Edit" else null, onOpenBudgets)
                    Spacer(Modifier.height(8.dp))
                    if (totalBudget <= 0f && catBudgets.isEmpty()) {
                        Text("Set a monthly budget and get alerts at 80% and 100%.", fontSize = 13.sp, color = Th.textMuted)
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton("Set budget", onOpenBudgets)
                    } else {
                        if (totalBudget > 0f) {
                            BudgetRow("Monthly budget", totalOut, totalBudget.toDouble())
                        }
                        catBudgets.entries
                            .map { (c, lim) -> Triple(c, debits.filter { it.txCategory == c }.sumOf { it.amount }, lim.toDouble()) }
                            .sortedByDescending { it.second / it.third }
                            .take(3)
                            .forEach { (c, spent, lim) -> Spacer(Modifier.height(12.dp)); BudgetRow("${categoryEmoji(c)} $c", spent, lim) }
                    }
                }
            }
        }

        // ---------- ride earnings ----------
        item {
            AppCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Ride earnings", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Th.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("COMMUTE" to "Commute", "WEEKEND" to "Weekend", "ALL" to "All").forEach { (k, l) ->
                            Chip(l, driverFilter == k, { driverFilter = k })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(money(uber + rapido), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Th.text)
                Text("${shownRides.size} trips", fontSize = 12.sp, color = Th.textMuted)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("UBER", money(uber), Th.info, Modifier.weight(1f))
                    StatTile("RAPIDO", money(rapido), Th.warning, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background((if (netAfterFuel >= 0) Th.income else Th.expense).copy(alpha = 0.12f)).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("All rides – petrol (${money(petrol)})", fontSize = 12.sp, color = Th.textMuted)
                    Text(signedMoney(netAfterFuel), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (netAfterFuel >= 0) Th.income else Th.expense)
                }
            }
        }

        item { RapidoCard(txs) }

        // ---------- recent ----------
        item { SectionTitle("Recent transactions", "See all", onSeeAll) }
        if (txs.isEmpty()) {
            item {
                AppCard {
                    Text("No transactions in this period yet.\nTap  + Add  to record your first one.", fontSize = 13.sp, color = Th.textMuted)
                }
            }
        } else {
            item {
                AppCard(padding = 8.dp) {
                    txs.take(6).forEachIndexed { i, t ->
                        if (i > 0) Hairline()
                        TxRow(t) { onEdit(t) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroPill(label: String, value: String, modifier: Modifier, sub: String? = null) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.16f)).padding(12.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        if (sub != null) Text(sub, fontSize = 9.sp, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
fun BudgetRow(label: String, spent: Double, limit: Double) {
    val ratio = if (limit > 0) (spent / limit).toFloat() else 0f
    val color = if (ratio >= 1f) Th.expense else if (ratio >= 0.8f) Th.warning else Th.income
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Th.text)
            Text("${money(spent)} / ${money(limit)}", fontSize = 12.sp, color = Th.textMuted)
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(ratio, color)
        Spacer(Modifier.height(4.dp))
        Text(
            if (ratio >= 1f) "Over by ${money(spent - limit)}" else "${money(limit - spent)} left • ${(ratio * 100).toInt()}% used",
            fontSize = 11.sp, color = color
        )
    }
}
