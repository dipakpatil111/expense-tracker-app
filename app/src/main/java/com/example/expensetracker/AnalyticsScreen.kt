package com.example.expensetracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

@Composable
fun AnalyticsScreen(vm: AppViewModel) {
    val txs by vm.transactions.collectAsState()
    val prev by vm.previousTransactions.collectAsState()
    val period by vm.period.collectAsState()
    val catBudgets by vm.categoryBudgets.collectAsState()

    val expenses = txs.filter { it.type == "DEBIT" }
    val income = txs.filter { it.type == "CREDIT" }
    val outTotal = expenses.sumOf { it.amount }
    val inTotal = income.sumOf { it.amount }
    val prevOut = prev.filter { it.type == "DEBIT" }.sumOf { it.amount }
    val prevIn = prev.filter { it.type == "CREDIT" }.sumOf { it.amount }
    val savingsRate = if (inTotal > 0) ((inTotal - outTotal) / inTotal * 100).toInt() else null

    // ----- daily (or monthly for long ranges) spending series -----
    val days = ((period.end - period.start) / DAY_MS).toInt() + 1
    val series: List<Pair<String, Double>> = remember(expenses, period) {
        if (days <= 45) {
            val byDay = expenses.groupBy { dayKey(it.timestamp) }.mapValues { e -> e.value.sumOf { it.amount } }
            (0 until days).map { i ->
                val ts = Calendar.getInstance().apply { timeInMillis = period.start; add(Calendar.DAY_OF_YEAR, i) }.timeInMillis
                val k = dayKey(ts)
                SimpleDateFormat("dd MMM", Locale.getDefault()).format(java.util.Date(ts)) to (byDay[k] ?: 0.0)
            }
        } else {
            expenses.groupBy { monthKey(it.timestamp) }.toSortedMap().map { (k, v) -> k to v.sumOf { it.amount } }
        }
    }
    val elapsedDays = (((minOf(System.currentTimeMillis(), period.end) - period.start) / DAY_MS).toInt() + 1).coerceAtLeast(1)
    val peak = series.maxByOrNull { it.second }

    val byCat = expenses.groupBy { it.txCategory }.map { (c, l) -> c to l.sumOf { it.amount } }.sortedByDescending { it.second }

    val rides = income.filter { it.mode == "UBER" || it.mode == "RAPIDO" }
    val rideTotal = rides.sumOf { it.amount }
    val petrol = expenses.filter { it.txCategory == "Petrol" }.sumOf { it.amount }
    val bestDay = rides.groupBy { dayKey(it.timestamp) }.map { (k, v) -> k to v.sumOf { it.amount } }.maxByOrNull { it.second }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ScreenTitle("Analytics", "Where your money goes") }
        item { PeriodBar(vm) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("INCOME", money(inTotal), Th.income, Modifier.weight(1f))
                StatTile("EXPENSE", money(outTotal), Th.expense, Modifier.weight(1f))
                StatTile("SAVED", if (savingsRate != null) "$savingsRate%" else "–", if ((savingsRate ?: 0) >= 0) Th.primary else Th.expense, Modifier.weight(1f), "of income")
            }
        }

        item {
            AppCard {
                SectionTitle("Compared with previous period")
                Spacer(Modifier.height(12.dp))
                ChangeRow("Expense", outTotal, prevOut, upIsGood = false)
                Spacer(Modifier.height(10.dp))
                Hairline()
                Spacer(Modifier.height(10.dp))
                ChangeRow("Income", inTotal, prevIn, upIsGood = true)
            }
        }

        item {
            AppCard {
                SectionTitle(if (days <= 45) "Daily spending" else "Monthly spending")
                Spacer(Modifier.height(12.dp))
                if (outTotal <= 0) Text("No expenses in this period", color = Th.textMuted, fontSize = 13.sp)
                else {
                    BarChart(series.map { it.second.toFloat() }, Th.primary)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(series.first().first, fontSize = 10.sp, color = Th.textMuted)
                        Text(series.last().first, fontSize = 10.sp, color = Th.textMuted)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("AVG / DAY", money(outTotal / elapsedDays), Th.text, Modifier.weight(1f))
                        StatTile("PEAK", money(peak?.second ?: 0.0), Th.expense, Modifier.weight(1f), peak?.first)
                    }
                }
            }
        }

        item {
            AppCard {
                SectionTitle("Spending by category")
                Spacer(Modifier.height(14.dp))
                if (byCat.isEmpty()) Text("No expenses in this period", color = Th.textMuted, fontSize = 13.sp)
                else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(contentAlignment = Alignment.Center) {
                            Donut(byCat)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total", fontSize = 11.sp, color = Th.textMuted)
                                Text(money(outTotal), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Th.text)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    byCat.forEachIndexed { i, (cat, amt) ->
                        if (i > 0) Spacer(Modifier.height(12.dp))
                        val pct = (amt / outTotal * 100).toInt()
                        val limit = catBudgets[cat]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryAvatar(cat, 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(cat, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Th.text)
                                    Text(if (limit != null) "${money(amt)} / ${money(limit.toDouble())}" else "${money(amt)} • $pct%", fontSize = 12.sp, color = Th.textMuted)
                                }
                                Spacer(Modifier.height(5.dp))
                                ProgressBar(if (limit != null) (amt / limit).toFloat() else (amt / outTotal).toFloat(),
                                    if (limit != null && amt > limit) Th.expense else categoryColor(cat), height = 6.dp)
                            }
                        }
                    }
                }
            }
        }

        item {
            AppCard {
                SectionTitle("Ride insights")
                Spacer(Modifier.height(12.dp))
                if (rides.isEmpty()) Text("No Uber / Rapido income recorded in this period", color = Th.textMuted, fontSize = 13.sp)
                else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("TRIPS", "${rides.size}", Th.text, Modifier.weight(1f))
                        StatTile("AVG / TRIP", money(rideTotal / rides.size), Th.info, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("FUEL / EARNINGS", if (rideTotal > 0) "${(petrol / rideTotal * 100).toInt()}%" else "–", Th.warning, Modifier.weight(1f), "petrol ${money(petrol)}")
                        StatTile("NET AFTER FUEL", signedMoney(rideTotal - petrol), if (rideTotal - petrol >= 0) Th.income else Th.expense, Modifier.weight(1f))
                    }
                    if (bestDay != null) {
                        Spacer(Modifier.height(10.dp))
                        Text("Best day: ${bestDay.first}  •  ${money(bestDay.second)}", fontSize = 12.sp, color = Th.textMuted)
                    }
                }
            }
        }

        item { RapidoCard(txs) }

        item {
            AppCard(padding = 8.dp) {
                Text("Biggest expenses", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Th.text, modifier = Modifier.padding(8.dp))
                val top = expenses.sortedByDescending { it.amount }.take(5)
                if (top.isEmpty()) Text("–", color = Th.textMuted, modifier = Modifier.padding(8.dp))
                top.forEachIndexed { i, t -> if (i > 0) Hairline(); TxRow(t) {} }
            }
        }
    }
}

@Composable
private fun ChangeRow(label: String, cur: Double, prev: Double, upIsGood: Boolean) {
    val pct = if (prev > 0) (cur - prev) / prev * 100 else null
    val good = pct == null || (pct >= 0) == upIsGood
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Th.text)
        Column(horizontalAlignment = Alignment.End) {
            Text(money(cur), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Th.text)
            Text(
                if (pct == null) "no previous data" else "${if (pct >= 0) "▲" else "▼"} ${abs(pct).toInt()}%  vs ${money(prev)}",
                fontSize = 11.sp, color = if (pct == null) Th.textMuted else if (good) Th.income else Th.expense
            )
        }
    }
}

@Composable
private fun BarChart(values: List<Float>, color: Color) {
    val maxV = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val n = values.size.coerceAtLeast(1)
        val gap = 3.dp.toPx()
        val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(2f)
        values.forEachIndexed { i, v ->
            val h = ((v / maxV) * size.height).coerceAtLeast(3f)
            drawRoundRect(
                color = if (v == maxV) color else color.copy(alpha = 0.45f),
                topLeft = Offset(i * (barW + gap), size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
        }
    }
}

@Composable
private fun Donut(parts: List<Pair<String, Double>>) {
    val total = parts.sumOf { it.second }.coerceAtLeast(1.0)
    Canvas(Modifier.size(170.dp)) {
        val stroke = 30.dp.toPx()
        val inset = stroke / 2
        var start = -90f
        parts.forEach { (cat, amt) ->
            val sweep = (amt / total * 360.0).toFloat()
            drawArc(
                color = categoryColor(cat), startAngle = start, sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f), useCenter = false,
                topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke), style = Stroke(width = stroke)
            )
            start += sweep
        }
    }
}
