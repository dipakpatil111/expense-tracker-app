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
        val transactionDao = db.transactionDao()
        val loanDao = db.loanDao()

        lifecycleScope.launch(Dispatchers.IO) {
            FirebaseSync.syncFromCloud(transactionDao, loanDao)
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF10B981),
                    background = Color(0xFF0B0F19),
                    surface = Color(0xFF111827)
                )
            ) {
                MainAppWithDrawer(
                    transactionDao = transactionDao,
                    loanDao = loanDao,
                    context = this
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppWithDrawer(
    transactionDao: TransactionDao,
    loanDao: LoanDao,
    context: Context
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("EXPENSES") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF111827),
                drawerContentColor = Color.White,
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text("Finance & Drive", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
                    Text("Daily Expenses & Credit Book", fontSize = 12.sp, color = Color(0xFF94A3B8))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Divider(color = Color(0xFF1F2937))
                Spacer(modifier = Modifier.height(12.dp))

                NavigationDrawerItem(
                    label = { Text("Daily Expense & Driver", fontWeight = FontWeight.Bold) },
                    selected = currentScreen == "EXPENSES",
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Color(0xFF1E293B),
                        selectedTextColor = Color(0xFF38BDF8),
                        selectedIconColor = Color(0xFF38BDF8),
                        unselectedTextColor = Color(0xFF94A3B8),
                        unselectedIconColor = Color(0xFF94A3B8)
                    ),
                    onClick = {
                        currentScreen = "EXPENSES"
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Loan & Interest Book", fontWeight = FontWeight.Bold) },
                    selected = currentScreen == "KHATA",
                    icon = { Icon(Icons.Default.AccountBox, contentDescription = null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Color(0xFF1E293B),
                        selectedTextColor = Color(0xFF10B981),
                        selectedIconColor = Color(0xFF10B981),
                        unselectedTextColor = Color(0xFF94A3B8),
                        unselectedIconColor = Color(0xFF94A3B8)
                    ),
                    onClick = {
                        currentScreen = "KHATA"
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    ) {
        if (currentScreen == "EXPENSES") {
            FintechDashboardScreen(
                dao = transactionDao,
                context = context,
                onOpenDrawer = { coroutineScope.launch { drawerState.open() } }
            )
        } else {
            UdhaarKhataScreen(
                loanDao = loanDao,
                transactionDao = transactionDao,
                onOpenDrawer = { coroutineScope.launch { drawerState.open() } }
            )
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
fun FintechDashboardScreen(
    dao: TransactionDao,
    context: Context,
    onOpenDrawer: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val months = remember { getAvailableMonths() }
    var selectedMonthIndex by remember { mutableIntStateOf(0) }
    var monthMenuExpanded by remember { mutableStateOf(false) }

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

    var driverFilter by remember { mutableStateOf("COMMUTE") }

    // FINANCIAL TOTALS
    val totalCredits = monthTransactions.filter { it.type == "CREDIT" }.sumOf { it.amount }
    val totalDebits = monthTransactions.filter { it.type == "DEBIT" }.sumOf { it.amount }
    val netBalance = totalCredits - totalDebits

    // CASH VS ONLINE BREAKDOWN
    val cashReceived = monthTransactions.filter { it.type == "CREDIT" && it.mode == "CASH" }.sumOf { it.amount }
    val cashSpent = monthTransactions.filter { it.type == "DEBIT" && it.mode == "CASH" }.sumOf { it.amount }
    val currentCashInHand = cashReceived - cashSpent

    val onlineReceived = monthTransactions.filter { it.type == "CREDIT" && (it.mode == "ONLINE" || it.mode == "UBER" || it.mode == "RAPIDO") }.sumOf { it.amount }
    val onlineSpent = monthTransactions.filter { it.type == "DEBIT" && it.mode == "ONLINE" }.sumOf { it.amount }
    val currentOnlineBalance = onlineReceived - onlineSpent

    // DRIVER SPECIFIC EARNINGS
    val driverTxs = monthTransactions.filter { (it.mode == "UBER" || it.mode == "RAPIDO") && it.type == "CREDIT" }
    val filteredDriverTxs = when (driverFilter) {
        "COMMUTE" -> driverTxs.filter { !isWeekend(it.timestamp) }
        "WEEKEND" -> driverTxs.filter { isWeekend(it.timestamp) }
        else -> driverTxs
    }

    val uberTotal = filteredDriverTxs.filter { it.mode == "UBER" }.sumOf { it.amount }
    val rapidoTotal = filteredDriverTxs.filter { it.mode == "RAPIDO" }.sumOf { it.amount }
    val totalDriverEarn = uberTotal + rapidoTotal

    val petrolSpent = monthTransactions.filter { it.txCategory == "Petrol" && it.type == "DEBIT" }.sumOf { it.amount }
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
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color(0xFF38BDF8))
                    }
                },
                title = {
                    Column {
                        Text("Finance & Side-Drive", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                        Text(activeDateLabel, fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }
                },
                actions = {
                    IconButton(onClick = { showRangePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Custom Date Range", tint = Color(0xFF38BDF8))
                    }

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

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.clickable {
                            exportPdf(context, activeDateLabel, monthTransactions, totalCredits, totalDebits)
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
                text = { Text("+ Add Transaction", fontWeight = FontWeight.Bold) }
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
            // CARD 1: LIVE WALLET & CASH / ONLINE BALANCE CARD
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF38BDF8)))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("NET POCKET & BANK BALANCE", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            Text(
                                text = "Total Net: ₹${String.format("%,.0f", netBalance)}",
                                color = if (netBalance >= 0) Color(0xFF10B981) else Color(0xFFEF4444),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // CASH BALANCE BOX
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF030712))
                                    .border(1.dp, Color(0xFF10B981), RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text("💵 CASH IN HAND", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF34D399))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("₹${String.format("%,.0f", currentCashInHand)}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("In: ₹${cashReceived.toInt()} • Out: ₹${cashSpent.toInt()}", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                }
                            }

                            // ONLINE / BANK BALANCE BOX
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF030712))
                                    .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text("📱 ONLINE / BANK", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF38BDF8))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("₹${String.format("%,.0f", currentOnlineBalance)}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("In: ₹${onlineReceived.toInt()} • Out: ₹${onlineSpent.toInt()}", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // RECEIVED VS SPENT STRIP
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E293B))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Received (Income/Credits): +₹${String.format("%,.0f", totalCredits)}", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Spent (Expenses/Debits): -₹${String.format("%,.0f", totalDebits)}", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // CARD 2: COMMUTE & WEEKEND EARNINGS CARD
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
                                Text("Platform Earnings", fontSize = 11.sp, color = Color(0xFF94A3B8))
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
                                    text = if (netExtraIncome >= 0) "Net: +₹${String.format("%,.0f", netExtraIncome)} (Fuel Covered!)" else "Net: ₹${String.format("%,.0f", netExtraIncome)}",
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

            // CARD 3: DONUT CHART
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1F2937)))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Expense Breakdown by Category", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFF1F5F9))
                        Spacer(modifier = Modifier.height(16.dp))

                        val expenseItems = monthTransactions.filter { it.type == "DEBIT" }
                        if (expenseItems.isEmpty()) {
                            Text("No expenses recorded in this period", color = Color(0xFF64748B), fontSize = 12.sp)
                        } else {
                            CategoryDonutSection(expenses = expenseItems)
                        }
                    }
                }
            }

            // TRANSACTIONS LIST
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
                        Text("No transactions found", color = Color(0xFF64748B), fontSize = 13.sp)
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
                                val iconColor = when (item.txCategory) {
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
                                        imageVector = when (item.txCategory) {
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
                                        Text(item.txCategory, fontSize = 10.sp, color = Color(0xFFCBD5E1))
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

    if (showAddModal) {
        ModernFastEntryDialog(
            title = "New Transaction",
            onDismiss = { showAddModal = false },
            onSave = { amount, desc, type, mode, categorySelected ->
                coroutineScope.launch {
                    val newTx = Transaction(
                        amount = amount,
                        type = type,
                        mode = mode,
                        txCategory = categorySelected,
                        description = desc
                    )
                    dao.insertTransaction(newTx)
                    FirebaseSync.saveToFirebase(newTx)
                    showAddModal = false
                }
            }
        )
    }

    editingItem?.let { tx ->
        ModernFastEntryDialog(
            title = "Edit Entry",
            initialAmount = tx.amount.toString(),
            initialDesc = tx.description,
            initialType = tx.type,
            initialMode = tx.mode,
            initialCategory = tx.txCategory,
            onDismiss = { editingItem = null },
            onSave = { amount, desc, type, mode, categorySelected ->
                coroutineScope.launch {
                    val updatedTx = tx.copy(
                        amount = amount,
                        description = desc,
                        type = type,
                        mode = mode,
                        txCategory = categorySelected
                    )
                    dao.updateTransaction(updatedTx)
                    FirebaseSync.saveToFirebase(updatedTx)
                    editingItem = null
                }
            }
        )
    }

    deletingItem?.let { tx ->
        AlertDialog(
            containerColor = Color(0xFF111827),
            titleContentColor = Color.White,
            textContentColor = Color(0xFF94A3B8),
            onDismissRequest = { deletingItem = null },
            title = { Text("Delete Entry") },
            text = { Text("Are you sure you want to delete this ₹${tx.amount} entry?") },
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
    val grouped = expenses.groupBy { it.txCategory }
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
    onSave: (amount: Double, desc: String, type: String, mode: String, categorySelected: String) -> Unit
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
                Text("Fast 1-tap entry for income & expenses", fontSize = 11.sp, color = Color(0xFF94A3B8))

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
                                category = "Other"
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Income / Received (+)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (type == "CREDIT") Color.Black else Color(0xFF94A3B8))
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
                        Text("Expense / Spent (-)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (type == "DEBIT") Color.White else Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("PAYMENT MODE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF64748B))
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CASH", "ONLINE", "UBER", "RAPIDO").forEach { m ->
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
                    val shortcuts = if (type == "CREDIT") listOf(48 to "Cashback", 100 to "Received", 200 to "Trip", 500 to "Salary")
                    else listOf(100 to "Fuel", 200 to "Fuel", 20 to "Tea", 500 to "Service")

                    items(shortcuts) { (amtVal, label) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E293B),
                            modifier = Modifier.clickable {
                                amount = amtVal.toString()
                                if (label == "Cashback") {
                                    category = "Other"
                                    mode = "ONLINE"
                                }
                                if (label == "Fuel") category = "Petrol"
                                if (label == "Tea") category = "Food"
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
                    val cats = listOf("Other", "Petrol", "Food", "Bills", "Rides", "Maintenance")
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
                    label = { Text("Note / Description (Kisne diya / Kahan kharcha hua)", color = Color(0xFF94A3B8)) },
                    placeholder = { Text("e.g. Credit Card Cashback, Fuel, Raj gave money", color = Color(0xFF64748B)) },
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
                            onSave(amt, desc.ifBlank { if (type == "CREDIT") "$mode Income" else category }, type, mode, category)
                        }
                    }
                ) {
                    Text(
                        text = if (type == "CREDIT") "Save Income (₹${amount.ifBlank { "0" }})" else "Save Expense (₹${amount.ifBlank { "0" }})",
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

// ---------------- UDHAAR & BYAAJ KHATA SCREEN ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UdhaarKhataScreen(
    loanDao: LoanDao,
    transactionDao: TransactionDao,
    onOpenDrawer: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val loans by loanDao.getAllLoans().collectAsState(initial = emptyList())

    val totalBorrowed = loans.filter { it.type == "TAKEN" && !it.isSettled }.sumOf { it.amount }
    val totalLent = loans.filter { it.type == "GIVEN" && !it.isSettled }.sumOf { it.amount }

    var showAddLoanModal by remember { mutableStateOf(false) }
    var selectedLoanForPassbook by remember { mutableStateOf<LoanRecord?>(null) }
    var activeLoanForPayment by remember { mutableStateOf<LoanRecord?>(null) }
    var paymentActionType by remember { mutableStateOf("INTEREST") }
    var loanToSettle by remember { mutableStateOf<LoanRecord?>(null) }

    Scaffold(
        containerColor = Color(0xFF0B0F19),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color(0xFF38BDF8))
                    }
                },
                title = {
                    Column {
                        Text("Loan & Interest Book", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                        Text("Lend, Borrow & Dynamic Interest Recalculation", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0F19))
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddLoanModal = true },
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White,
                shape = RoundedCornerShape(14.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("+ Add New Record", fontWeight = FontWeight.Bold) }
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
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFEF4444)))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("BORROWED (DEBT)", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFF87171))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("₹${String.format("%,.0f", totalBorrowed)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${loans.count { it.type == "TAKEN" && !it.isSettled }} Active", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF10B981)))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("LENT (CREDIT)", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF34D399))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("₹${String.format("%,.0f", totalLent)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${loans.count { it.type == "GIVEN" && !it.isSettled }} Active", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }
            }

            item {
                Text("Active Accounts & Details", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            }

            if (loans.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("No active loan records found", color = Color(0xFF64748B), fontSize = 13.sp)
                    }
                }
            } else {
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                items(loans, key = { it.id }) { loan ->
                    val currentMonthlyInterest = if (loan.hasInterest) (loan.amount * loan.monthlyRate) / 100.0 else 0.0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedLoanForPassbook = loan },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (loan.isSettled) Color(0xFF0F172A) else Color(0xFF111827)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(if (loan.isSettled) Color(0xFF1F2937) else Color(0xFF334155)))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (loan.isSettled) Color(0xFF334155) else if (loan.type == "TAKEN") Color(0xFFEF4444) else Color(0xFF10B981)
                                ) {
                                    Text(
                                        text = if (loan.isSettled) "FULLY SETTLED" else if (loan.type == "TAKEN") "BORROWED (DEBT)" else "LENT (CREDIT)",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (loan.isSettled || loan.type == "TAKEN") Color.White else Color.Black,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Text("Start: ${sdf.format(Date(loan.startDate))}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(loan.personName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    if (loan.originalAmount > loan.amount) {
                                        Text("Original: ₹${String.format("%,.0f", loan.originalAmount)} (₹${String.format("%,.0f", loan.originalAmount - loan.amount)} Paid)", fontSize = 11.sp, color = Color(0xFF10B981))
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "₹${String.format("%,.0f", loan.amount)}",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (loan.type == "TAKEN") Color(0xFFF87171) else Color(0xFF34D399)
                                    )
                                    Text("Balance Due", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                }
                            }

                            if (loan.hasInterest && !loan.isSettled) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF030712))
                                        .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Interest: ${loan.monthlyRate}% / month", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                            Text("₹${String.format("%,.0f", currentMonthlyInterest)}/mo", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "₹${currentMonthlyInterest.toInt()} monthly interest on remaining ₹${loan.amount.toInt()} balance",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFBBF24)
                                        )
                                    }
                                }
                            } else if (!loan.hasInterest && !loan.isSettled) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Zero Interest (Friend/Family)", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }

                            if (!loan.isSettled) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (loan.hasInterest) {
                                        Button(
                                            modifier = Modifier.weight(1f).height(36.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                                            onClick = {
                                                paymentActionType = "INTEREST"
                                                activeLoanForPayment = loan
                                            }
                                        ) {
                                            Text("Pay Interest", fontSize = 10.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Button(
                                        modifier = Modifier.weight(1.1f).height(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                                        onClick = {
                                            paymentActionType = "PRINCIPAL"
                                            activeLoanForPayment = loan
                                        }
                                    ) {
                                        Text("Repay Principal", fontSize = 10.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        modifier = Modifier.weight(0.9f).height(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = if (loan.type == "TAKEN") Color(0xFF10B981) else Color(0xFF2563EB)),
                                        onClick = { loanToSettle = loan }
                                    ) {
                                        Text(
                                            text = if (loan.type == "TAKEN") "Settle" else "Received",
                                            fontSize = 10.sp,
                                            color = if (loan.type == "TAKEN") Color.Black else Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddLoanModal) {
        AddNewLoanDialog(
            onDismiss = { showAddLoanModal = false },
            onSave = { name, amount, type, hasInt, rate, date, note ->
                coroutineScope.launch {
                    val newLoan = LoanRecord(
                        personName = name,
                        amount = amount,
                        originalAmount = amount,
                        type = type,
                        hasInterest = hasInt,
                        monthlyRate = rate,
                        startDate = date,
                        note = note
                    )
                    val id = loanDao.insertLoan(newLoan)
                    FirebaseSync.saveLoanToFirebase(newLoan.copy(id = id))
                    showAddLoanModal = false
                }
            }
        )
    }

    activeLoanForPayment?.let { loan ->
        val defaultAmt = if (paymentActionType == "INTEREST") {
            (loan.amount * loan.monthlyRate / 100.0).toString()
        } else ""

        RecordPaymentDialog(
            loan = loan,
            paymentType = paymentActionType,
            defaultAmount = defaultAmt,
            onDismiss = { activeLoanForPayment = null },
            onConfirm = { amountPaid, mode, payDate, note ->
                coroutineScope.launch {
                    val payment = InterestPayment(
                        loanId = loan.id,
                        amount = amountPaid,
                        paymentType = paymentActionType,
                        paymentMode = mode,
                        paymentDate = payDate,
                        note = note
                    )
                    loanDao.insertInterestPayment(payment)
                    FirebaseSync.savePaymentToFirebase(payment)

                    if (paymentActionType == "PRINCIPAL") {
                        val newBalance = (loan.amount - amountPaid).coerceAtLeast(0.0)
                        val isFullyPaid = newBalance <= 0.0
                        val updatedLoan = loan.copy(amount = newBalance, isSettled = isFullyPaid)
                        loanDao.updateLoan(updatedLoan)
                        FirebaseSync.saveLoanToFirebase(updatedLoan)
                    }

                    val tx = Transaction(
                        amount = amountPaid,
                        type = if (loan.type == "TAKEN") "DEBIT" else "CREDIT",
                        mode = mode,
                        txCategory = if (paymentActionType == "INTEREST") "Bills" else "Other",
                        description = if (paymentActionType == "INTEREST") "Interest to ${loan.personName}" else "Loan Return to ${loan.personName}",
                        timestamp = payDate
                    )
                    transactionDao.insertTransaction(tx)
                    FirebaseSync.saveToFirebase(tx)

                    activeLoanForPayment = null
                }
            }
        )
    }

    selectedLoanForPassbook?.let { loan ->
        LoanPassbookDialog(
            loan = loan,
            loanDao = loanDao,
            onDismiss = { selectedLoanForPassbook = null }
        )
    }

    loanToSettle?.let { loan ->
        AlertDialog(
            containerColor = Color(0xFF111827),
            titleContentColor = Color.White,
            textContentColor = Color(0xFF94A3B8),
            onDismissRequest = { loanToSettle = null },
            title = { Text("Complete Settlement?") },
            text = { Text("Has the remaining ₹${loan.amount} for ${loan.personName} been settled?") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    onClick = {
                        coroutineScope.launch {
                            val settledLoan = loan.copy(amount = 0.0, isSettled = true)
                            loanDao.updateLoan(settledLoan)
                            FirebaseSync.saveLoanToFirebase(settledLoan)
                            loanToSettle = null
                        }
                    }
                ) { Text("Yes, Mark Settled", color = Color.Black, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { loanToSettle = null }) { Text("Cancel", color = Color.Gray) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordPaymentDialog(
    loan: LoanRecord,
    paymentType: String,
    defaultAmount: String,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, mode: String, date: Long, note: String) -> Unit
) {
    var amount by remember { mutableStateOf(defaultAmount) }
    var mode by remember { mutableStateOf("ONLINE") }
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var note by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
    val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF111827),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (paymentType == "INTEREST") "Pay Interest (${loan.personName})" else "Repay Principal (${loan.personName})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.White
                )
                Text(
                    text = if (paymentType == "INTEREST") "Record monthly interest payment" else "Balance due: ₹${loan.amount.toInt()}. Enter amount:",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("PAYMENT MODE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF64748B))
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ONLINE" to "📱 ONLINE / UPI", "CASH" to "💵 CASH").forEach { (key, label) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (mode == key) Color(0xFF1E293B) else Color(0xFF0F172A))
                                .border(1.dp, if (mode == key) Color(0xFF38BDF8) else Color(0xFF1F2937), RoundedCornerShape(8.dp))
                                .clickable { mode = key }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (mode == key) Color(0xFF38BDF8) else Color(0xFF94A3B8))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0B0F19),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Date: ${sdf.format(Date(selectedDate))}", color = Color.White, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (Optional, e.g. PhonePe Ref)", color = Color(0xFF94A3B8)) },
                    singleLine = true,
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    onClick = {
                        val amt = amount.toDoubleOrNull()
                        if (amt != null && amt > 0) {
                            onConfirm(amt, mode, selectedDate, note.ifBlank { if (paymentType == "INTEREST") "Interest Payment" else "Principal Repayment" })
                        }
                    }
                ) {
                    Text("Confirm Payment (₹${amount.ifBlank { "0" }})", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(modifier = Modifier.fillMaxWidth(), onClick = onDismiss) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDate = it }
                    showDatePicker = false
                }) { Text("OK", color = Color(0xFF10B981), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNewLoanDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, amount: Double, type: String, hasInt: Boolean, rate: Double, date: Long, note: String) -> Unit
) {
    var personName by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("TAKEN") }
    var hasInterest by remember { mutableStateOf(false) }
    var interestRate by remember { mutableStateOf("2.0") }
    var note by remember { mutableStateOf("") }

    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
    val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF111827),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("New Credit / Loan Record", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                Text("Track money borrowed or lent with interest", fontSize = 11.sp, color = Color(0xFF94A3B8))

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
                            .background(if (type == "TAKEN") Color(0xFFEF4444) else Color.Transparent)
                            .clickable { type = "TAKEN" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Borrowed (Debt)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (type == "TAKEN") Color.White else Color(0xFF94A3B8))
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (type == "GIVEN") Color(0xFF10B981) else Color.Transparent)
                            .clickable { type = "GIVEN" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Lent (Credit)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (type == "GIVEN") Color.Black else Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = personName,
                    onValueChange = { personName = it },
                    label = { Text("Person Name (e.g. Raj, John)", color = Color(0xFF94A3B8)) },
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

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Principal Amount (₹)", color = Color(0xFF94A3B8)) },
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

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0B0F19),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Date: ${sdf.format(Date(selectedDateMillis))}", color = Color.White, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF030712),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (hasInterest) Color(0xFF10B981) else Color(0xFF1F2937)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Apply Monthly Interest?", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Switch(
                                checked = hasInterest,
                                onCheckedChange = { hasInterest = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF10B981))
                            )
                        }

                        if (hasInterest) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = interestRate,
                                onValueChange = { interestRate = it },
                                label = { Text("Monthly Rate % (e.g. 2.0)", color = Color(0xFF94A3B8)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF10B981),
                                    unfocusedBorderColor = Color(0xFF334155)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    onClick = {
                        val amt = amount.toDoubleOrNull()
                        val rate = interestRate.toDoubleOrNull() ?: 0.0
                        if (personName.isNotBlank() && amt != null && amt > 0) {
                            onSave(personName, amt, type, hasInterest, rate, selectedDateMillis, note)
                        }
                    }
                ) {
                    Text("Save Record", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(modifier = Modifier.fillMaxWidth(), onClick = onDismiss) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDateMillis = it }
                    showDatePicker = false
                }) { Text("OK", color = Color(0xFF10B981), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun LoanPassbookDialog(
    loan: LoanRecord,
    loanDao: LoanDao,
    onDismiss: () -> Unit
) {
    val payments by loanDao.getInterestPayments(loan.id).collectAsState(initial = emptyList())
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF111827),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("${loan.personName} - Passbook", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                Text("Balance Due: ₹${String.format("%,.0f", loan.amount)} • Original: ₹${String.format("%,.0f", loan.originalAmount)}", fontSize = 11.sp, color = Color(0xFF94A3B8))

                Spacer(modifier = Modifier.height(14.dp))

                if (payments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No payment records found yet", color = Color(0xFF64748B), fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(payments) { p ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0F172A))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = if (p.paymentType == "INTEREST") "Interest Paid (${p.paymentMode})" else "Principal Paid (${p.paymentMode})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (p.paymentType == "INTEREST") Color(0xFF38BDF8) else Color(0xFF34D399)
                                        )
                                        Text(sdf.format(Date(p.paymentDate)), fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    }
                                    Text("₹${String.format("%,.0f", p.amount)}", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    onClick = onDismiss
                ) {
                    Text("Close", color = Color.White)
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
    canvas.drawText("Statement - $dateRangeLabel", 40f, 50f, paint)

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
        canvas.drawText(tx.txCategory, 310f, y, paint)
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