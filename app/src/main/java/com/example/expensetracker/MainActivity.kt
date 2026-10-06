package com.example.expensetracker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            requestPermissions.launch(needed.toTypedArray())
        }

        val db = AppDatabase.getDatabase(this)
        val dao = db.transactionDao()

        lifecycleScope.launch(Dispatchers.IO) {
            FirebaseSync.syncFromCloud(dao)
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF10B981),
                    background = Color(0xFF0B0F19),
                    surface = Color(0xFF111827)
                )
            ) {
                FintechDashboardScreen(dao = dao, context = this)
            }
        }
    }
}

data class MonthItem(val label: String, val startMillis: Long, val endMillis: Long)

fun getAvailableMonths(): List<MonthItem> {
    val list = mutableListOf<MonthItem>()
    val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val cal = Calendar.getInstance()

    for (i in 0 until 12) {
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis

        list.add(MonthItem(sdf.format(Date(start)), start, end))
        cal.add(Calendar.MONTH, -1)
    }
    return list
}

fun isWeekend(timestamp: Long): Boolean {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val day = cal.get(Calendar.DAY_OF_WEEK)
    return day == Calendar.SATURDAY || day == Calendar.SUNDAY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FintechDashboardScreen(dao: TransactionDao, context: Context) {
    val coroutineScope = rememberCoroutineScope()
    val months = remember { getAvailableMonths() }
    var selectedMonthIndex by remember { mutableIntStateOf(0) }
    var monthMenuExpanded by remember { mutableStateOf(false) }

    // Date Range Picker States
    var isCustomRange by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    val currentMonth = months[selectedMonthIndex]
    var customStartMillis by remember { mutableLongStateOf(currentMonth.startMillis) }
    var customEndMillis by remember { mutableLongStateOf(currentMonth.endMillis) }

    val activeStartMillis = if (isCustomRange) customStartMillis else currentMonth.startMillis
    val activeEndMillis = if (isCustomRange) customEndMillis else currentMonth.endMillis

    val monthTransactions by dao.getTransactionsByDateRange(activeStartMillis, activeEndMillis)
        .collectAsState(initial = emptyList())

    // Filter Mode: COMMUTE (Mon-Fri), WEEKEND (Sat-Sun), ALL
    var driverFilter by remember { mutableStateOf("COMMUTE") }

    val totalDebit = monthTransactions.filter { it.type == "DEBIT" }.sumOf { it.amount }
    val totalCredit = monthTransactions.filter { it.type == "CREDIT" }.sumOf { it.amount }
    val balance = totalCredit - totalDebit

    val driverTxs = monthTransactions.filter { (it.mode == "UBER" || it.mode == "RAPIDO") && it.type == "CREDIT" }
    val filteredDriverTxs = when (driverFilter) {
        "COMMUTE" -> driverTxs.filter { !isWeekend(it.timestamp) }
        "WEEKEND" -> driverTxs.filter { isWeekend(it.timestamp) }
        else -> driverTxs
    }

    val uberTotal = filteredDriverTxs.filter { it.mode == "UBER" }.sumOf { it.amount }
    val rapidoTotal = filteredDriverTxs.filter { it.mode == "RAPIDO" }.sumOf { it.amount }
    val totalDriverEarn = uberTotal + rapidoTotal

    val petrolSpent = monthTransactions.filter { it.category == "Petrol" && it.type == "DEBIT" }.sumOf { it.amount }
    val netExtraIncome = totalDriverEarn - petrolSpent

    var showAddModal by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<Transaction?>(null) }
    var deletingItem by remember { mutableStateOf<Transaction?>(null) }

    val activeDateLabel = if (isCustomRange) {
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
        "${sdf.format(Date(activeStartMillis))} - ${sdf.format(Date(activeEndMillis))}"
    } else {
        currentMonth.label
    }

    Scaffold(
        containerColor = Color(0xFF0B0F19),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Finance & Side-Drive", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                        Text(activeDateLabel, fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }
                },
                actions = {
                    // Custom Date Range Picker Button
                    IconButton(onClick = { showRangePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Custom Date Range", tint = Color(0xFF38BDF8))
                    }

                    // Month Selector Dropdown
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.clickable { monthMenuExpanded = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isCustomRange) "Month" else currentMonth.label,
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = monthMenuExpanded,
                        onDismissRequest = { monthMenuExpanded = false }
                    ) {
                        months.forEachIndexed { index, item ->
                            DropdownMenuItem(
                                text = { Text(item.label, fontWeight = if (!isCustomRange && index == selectedMonthIndex) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    selectedMonthIndex = index
                                    isCustomRange = false
                                    monthMenuExpanded = false
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // PDF Export Button
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.clickable {
                            exportPdf(context, activeDateLabel, monthTransactions, totalCredit, totalDebit)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0F19))
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddModal = true },
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White,
                shape = RoundedCornerShape(14.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("+ Add Entry / Ride", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFF0B0F19)),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // CARD 1: COMMUTE & WEEKEND EARNINGS
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF10B981)))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("COMMUTE & WEEKEND EARNINGS", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B))
                                    .padding(2.dp)
                            ) {
                                listOf("COMMUTE" to "Commute", "WEEKEND" to "Weekend", "ALL" to "All").forEach { (key, label) ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (driverFilter == key) Color(0xFF10B981) else Color.Transparent)
                                            .clickable { driverFilter = key }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            label,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (driverFilter == key) Color.Black else Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text("Platform Earning", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                Text(
                                    "₹${String.format("%,.0f", totalDriverEarn)}",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (netExtraIncome >= 0) Color(0x2210B981) else Color(0x22EF4444)
                            ) {
                                Text(
                                    text = if (netExtraIncome >= 0) "Net: +₹${String.format("%,.0f", netExtraIncome)} (Petrol Free!)" else "Net: ₹${String.format("%,.0f", netExtraIncome)}",
                                    color = if (netExtraIncome >= 0) Color(0xFF34D399) else Color(0xFFF87171),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF030712))
                                    .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text("UBER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("₹${String.format("%,.0f", uberTotal)}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF030712))
                                    .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text("RAPIDO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("₹${String.format("%,.0f", rapidoTotal)}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                                }
                            }
                        }
                    }
                }
            }

            // CARD 2: CATEGORY DONUT EXPENSE CHART
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1F2937)))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Where is money going? (Kharcha)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFF1F5F9))
                        Spacer(modifier = Modifier.height(16.dp))

                        val expenseItems = monthTransactions.filter { it.type == "DEBIT" }
                        if (expenseItems.isEmpty()) {
                            Text("Is range me koi kharcha record nahi hua", color = Color(0xFF64748B), fontSize = 12.sp)
                        } else {
                            CategoryDonutSection(expenses = expenseItems)
                        }
                    }
                }
            }

            // TRANSACTIONS LIST HEADER
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Transactions (${monthTransactions.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                }
            }

            if (monthTransactions.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("Koi record nahi mila", color = Color(0xFF64748B), fontSize = 13.sp)
                    }
                }
            } else {
                val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                items(monthTransactions, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                val iconColor = when (item.category) {
                                    "Petrol" -> Color(0xFFEF4444)
                                    "Food" -> Color(0xFFF59E0B)
                                    "Bills" -> Color(0xFF38BDF8)
                                    "Maintenance" -> Color(0xFFA78BFA)
                                    "Rides" -> Color(0xFF10B981)
                                    else -> Color(0xFF94A3B8)
                                }

                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0F172A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (item.category) {
                                            "Petrol" -> Icons.Default.Place
                                            "Food" -> Icons.Default.ShoppingCart
                                            "Bills" -> Icons.Default.Email
                                            "Maintenance" -> Icons.Default.Build
                                            "Rides" -> Icons.Default.Star
                                            else -> Icons.Default.Info
                                        },
                                        contentDescription = null,
                                        tint = iconColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(item.description, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White, maxLines = 1)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(item.mode, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF38BDF8))
                                        Text(" • ", fontSize = 10.sp, color = Color(0xFF64748B))
                                        Text(item.category, fontSize = 10.sp, color = Color(0xFFCBD5E1))
                                        Text(" • ", fontSize = 10.sp, color = Color(0xFF64748B))
                                        Text(sdf.format(Date(item.timestamp)), fontSize = 10.sp, color = Color(0xFF64748B))
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = (if (item.type == "CREDIT") "+ " else "- ") + "₹${String.format("%,.0f", item.amount)}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = if (item.type == "CREDIT") Color(0xFF10B981) else Color(0xFFEF4444)
                                )

                                IconButton(onClick = { editingItem = item }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF64748B), modifier = Modifier.size(15.dp))
                                }
                                IconButton(onClick = { deletingItem = item }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Custom Date Range Picker Dialog
    if (showRangePicker) {
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = dateRangePickerState.selectedStartDateMillis
                    val end = dateRangePickerState.selectedEndDateMillis
                    if (start != null && end != null) {
                        customStartMillis = start
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = end
                            set(Calendar.HOUR_OF_DAY, 23)
                            set(Calendar.MINUTE, 59)
                            set(Calendar.SECOND, 59)
                            set(Calendar.MILLISECOND, 999)
                        }
                        customEndMillis = cal.timeInMillis
                        isCustomRange = true
                    }
                    showRangePicker = false
                }) { Text("Apply", color = Color(0xFF10B981), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showRangePicker = false }) { Text("Cancel", color = Color.Gray) }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = { Text("Select Date Range", modifier = Modifier.padding(16.dp), color = Color.White) },
                showModeToggle = false
            )
        }
    }

    // Modal: 1-Tap Fast Entry Dialog
    if (showAddModal) {
        ModernFastEntryDialog(
            title = "New Transaction",
            onDismiss = { showAddModal = false },
            onSave = { amount, desc, type, mode, category ->
                coroutineScope.launch {
                    val newTx = Transaction(
                        amount = amount,
                        type = type,
                        mode = mode,
                        category = category,
                        description = desc
                    )
                    dao.insertTransaction(newTx)
                    FirebaseSync.saveToFirebase(newTx)
                    showAddModal = false
                }
            }
        )
    }

    // Modal: Edit
    editingItem?.let { tx ->
        ModernFastEntryDialog(
            title = "Edit Entry",
            initialAmount = tx.amount.toString(),
            initialDesc = tx.description,
            initialType = tx.type,
            initialMode = tx.mode,
            initialCategory = tx.category,
            onDismiss = { editingItem = null },
            onSave = { amount, desc, type, mode, category ->
                coroutineScope.launch {
                    val updatedTx = tx.copy(
                        amount = amount,
                        description = desc,
                        type = type,
                        mode = mode,
                        category = category
                    )
                    dao.updateTransaction(updatedTx)
                    FirebaseSync.saveToFirebase(updatedTx)
                    editingItem = null
                }
            }
        )
    }

    // Modal: Delete Confirm
    deletingItem?.let { tx ->
        AlertDialog(
            containerColor = Color(0xFF111827),
            titleContentColor = Color.White,
            textContentColor = Color(0xFF94A3B8),
            onDismissRequest = { deletingItem = null },
            title = { Text("Delete Entry") },
            text = { Text("₹${tx.amount} ki entry delete karni hai?") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    onClick = {
                        coroutineScope.launch {
                            dao.deleteTransaction(tx)
                            FirebaseSync.deleteFromFirebase(tx.timestamp)
                            deletingItem = null
                        }
                    }
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { deletingItem = null }) { Text("Cancel", color = Color.Gray) } }
        )
    }
}

@Composable
fun CategoryDonutSection(expenses: List<Transaction>) {
    val totalExpense = expenses.sumOf { it.amount }.toFloat().coerceAtLeast(1f)
    val grouped = expenses.groupBy { it.category }
        .mapValues { it.value.sumOf { tx -> tx.amount }.toFloat() }
        .toList()
        .sortedByDescending { it.second }

    val categoryColors = mapOf(
        "Petrol" to Color(0xFFEF4444),
        "Food" to Color(0xFFF59E0B),
        "Bills" to Color(0xFF38BDF8),
        "Maintenance" to Color(0xFFA78BFA),
        "Rides" to Color(0xFF10B981),
        "Other" to Color(0xFF64748B)
    )

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var startAngle = -90f
                grouped.forEach { (cat, amt) ->
                    val sweep = (amt / totalExpense) * 360f
                    val col = categoryColors[cat] ?: Color(0xFF64748B)
                    drawArc(
                        color = col,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = 16f, cap = StrokeCap.Round)
                    )
                    startAngle += sweep
                }
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            grouped.take(4).forEach { (cat, amt) ->
                val pct = ((amt / totalExpense) * 100).toInt()
                val col = categoryColors[cat] ?: Color(0xFF64748B)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(col))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("$cat: ₹${amt.toInt()} ($pct%)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernFastEntryDialog(
    title: String,
    initialAmount: String = "",
    initialDesc: String = "",
    initialType: String = "CREDIT",
    initialMode: String = "UBER",
    initialCategory: String = "Rides",
    onDismiss: () -> Unit,
    onSave: (amount: Double, desc: String, type: String, mode: String, category: String) -> Unit
) {
    var amount by remember { mutableStateOf(initialAmount) }
    var desc by remember { mutableStateOf(initialDesc) }
    var type by remember { mutableStateOf(initialType) }
    var mode by remember { mutableStateOf(initialMode) }
    var category by remember { mutableStateOf(initialCategory) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF111827),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                Text("Fast 1-tap entry for rides & expenses", fontSize = 11.sp, color = Color(0xFF94A3B8))

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF030712))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (type == "CREDIT") Color(0xFF10B981) else Color.Transparent)
                            .clickable {
                                type = "CREDIT"
                                category = "Rides"
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Earning / Ride (+)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (type == "CREDIT") Color.Black else Color(0xFF94A3B8))
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (type == "DEBIT") Color(0xFFEF4444) else Color.Transparent)
                            .clickable {
                                type = "DEBIT"
                                category = "Petrol"
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Expense (-)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (type == "DEBIT") Color.White else Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("PLATFORM / MODE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF64748B))
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("UBER", "RAPIDO", "CASH", "ONLINE").forEach { m ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (mode == m) Color(0xFF1E293B) else Color(0xFF0F172A))
                                .border(1.dp, if (mode == m) Color(0xFF38BDF8) else Color(0xFF1F2937), RoundedCornerShape(8.dp))
                            .clickable {
                                mode = m
                                if (desc.isBlank()) {
                                    desc = if (m == "UBER") "Uber Ride" else if (m == "RAPIDO") "Rapido Ride" else ""
                                }
                            }
                            .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(m, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (mode == m) Color(0xFF38BDF8) else Color(0xFF94A3B8))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("QUICK SHORTCUTS", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF64748B))
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val shortcuts = if (type == "CREDIT") listOf(50 to "Ride", 80 to "Ride", 120 to "Ride", 180 to "Ride")
                    else listOf(100 to "Petrol", 200 to "Petrol", 20 to "Chai", 500 to "Fuel")

                    items(shortcuts) { (amtVal, label) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E293B),
                            modifier = Modifier.clickable {
                                amount = amtVal.toString()
                                if (label == "Petrol" || label == "Fuel") category = "Petrol"
                                if (label == "Chai") category = "Food"
                                desc = "$label ₹$amtVal"
                            }
                        ) {
                            Text("+ ₹$amtVal $label", fontSize = 11.sp, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)", color = Color(0xFF94A3B8)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("CATEGORY", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF64748B))
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val cats = listOf("Rides", "Petrol", "Food", "Bills", "Maintenance", "Other")
                    items(cats) { c ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (category == c) Color(0xFF2563EB) else Color(0xFF1E293B))
                                .clickable { category = c }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(c, fontSize = 11.sp, color = if (category == c) Color.White else Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Note / Description", color = Color(0xFF94A3B8)) },
                    placeholder = { Text("e.g. Office to Home, Tea, etc.", color = Color(0xFF64748B)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (type == "CREDIT") Color(0xFF10B981) else Color(0xFFEF4444)),
                    onClick = {
                        val amt = amount.toDoubleOrNull()
                        if (amt != null && amt > 0) {
                            onSave(amt, desc.ifBlank { if (type == "CREDIT") "$mode Earning" else category }, type, mode, category)
                        }
                    }
                ) {
                    Text(
                        text = if (type == "CREDIT") "Save Earning (₹${amount.ifBlank { "0" }})" else "Save Expense (₹${amount.ifBlank { "0" }})",
                        color = if (type == "CREDIT") Color.Black else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss
                ) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        }
    }
}

fun exportPdf(
    context: Context,
    dateRangeLabel: String,
    transactions: List<Transaction>,
    totalCredit: Double,
    totalDebit: Double
) {
    val pdfDocument = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas
    val paint = Paint()

    paint.textSize = 20f
    paint.isFakeBoldText = true
    paint.color = android.graphics.Color.BLACK
    canvas.drawText("Fintech & Driver Statement - $dateRangeLabel", 40f, 50f, paint)

    paint.textSize = 12f
    paint.isFakeBoldText = false
    paint.color = android.graphics.Color.DKGRAY
    canvas.drawText("Generated on: ${SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date())}", 40f, 70f, paint)

    paint.color = android.graphics.Color.LTGRAY
    paint.strokeWidth = 1f
    canvas.drawLine(40f, 90f, 550f, 90f, paint)

    paint.color = android.graphics.Color.BLACK
    paint.textSize = 13f
    paint.isFakeBoldText = true
    canvas.drawText("Total Credit: Rs. $totalCredit", 40f, 110f, paint)
    canvas.drawText("Total Debit: Rs. $totalDebit", 220f, 110f, paint)
    canvas.drawText("Net: Rs. ${totalCredit - totalDebit}", 400f, 110f, paint)

    canvas.drawLine(40f, 130f, 550f, 130f, paint)

    var y = 160f
    paint.isFakeBoldText = true
    canvas.drawText("Date", 40f, y, paint)
    canvas.drawText("Description", 140f, y, paint)
    canvas.drawText("Category", 310f, y, paint)
    canvas.drawText("Mode", 410f, y, paint)
    canvas.drawText("Amount", 490f, y, paint)

    paint.isFakeBoldText = false
    val sdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

    for (tx in transactions) {
        y += 24f
        if (y > 800f) break

        canvas.drawText(sdf.format(Date(tx.timestamp)), 40f, y, paint)
        val shortDesc = if (tx.description.length > 20) tx.description.take(20) + ".." else tx.description
        canvas.drawText(shortDesc, 140f, y, paint)
        canvas.drawText(tx.category, 310f, y, paint)
        canvas.drawText(tx.mode, 410f, y, paint)
        canvas.drawText("Rs. ${tx.amount}", 490f, y, paint)
    }

    pdfDocument.finishPage(page)

    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
    val file = File(dir, "Statement_${dateRangeLabel.replace(" ", "_").replace("-", "_")}.pdf")

    try {
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Statement PDF"))
    } catch (e: Exception) {
        pdfDocument.close()
        Toast.makeText(context, "PDF Error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}