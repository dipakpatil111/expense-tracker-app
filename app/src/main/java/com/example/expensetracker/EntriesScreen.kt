package com.example.expensetracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EntriesScreen(vm: AppViewModel, onEdit: (Transaction) -> Unit) {
    val context = LocalContext.current
    val txs by vm.transactions.collectAsState()
    val period by vm.period.collectAsState()
    val categories by vm.categories.collectAsState()

    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("ALL") }
    var modeFilter by remember { mutableStateOf<String?>(null) }
    var catFilter by remember { mutableStateOf<String?>(null) }

    val filtered = remember(txs, query, typeFilter, modeFilter, catFilter) {
        val q = query.trim().lowercase()
        txs.filter { t ->
            (typeFilter == "ALL" || t.type == typeFilter) &&
                (modeFilter == null || t.mode == modeFilter) &&
                (catFilter == null || t.txCategory == catFilter) &&
                (q.isEmpty() || t.description.lowercase().contains(q) || t.txCategory.lowercase().contains(q) ||
                    t.mode.lowercase().contains(q) || t.amount.toString().contains(q))
        }
    }
    val fIn = filtered.filter { it.type == "CREDIT" && !isTransfer(it) }.sumOf { it.amount }
    val fOut = filtered.filter { it.type == "DEBIT" && !isTransfer(it) }.sumOf { it.amount }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Entries", "${filtered.size} transactions")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("PDF", false, { exportPdf(context, period.label, filtered, fIn, fOut) })
                    Chip("CSV", false, { exportCsv(context, period.label, filtered) })
                }
            }
        }
        item { PeriodBar(vm) }
        item { AppTextField(query, { query = it }, "Search note, category, amount…") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("ALL" to "All", "CREDIT" to "Income", "DEBIT" to "Expense")) { (k, l) -> Chip(l, typeFilter == k, { typeFilter = k }) }
                items(listOf("CASH", "ONLINE", "UBER", "RAPIDO")) { m -> Chip(m, modeFilter == m, { modeFilter = if (modeFilter == m) null else m }) }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(categories) { c -> Chip("${categoryEmoji(c)} $c", catFilter == c, { catFilter = if (catFilter == c) null else c }) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("In ${money(fIn)}", color = Th.income, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Out ${money(fOut)}", color = Th.expense, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Net ${signedMoney(fIn - fOut)}", color = Th.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        if (filtered.isEmpty()) {
            item { AppCard { Text("Nothing matches. Try a different month or clear the filters.", color = Th.textMuted, fontSize = 13.sp) } }
        } else {
            txDayGroups(filtered, onEdit)
        }
    }
}
