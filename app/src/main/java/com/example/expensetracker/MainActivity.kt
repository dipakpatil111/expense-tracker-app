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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF0F172A),
                    secondary = Color(0xFF2563EB),
                    surface = Color(0xFFF8FAFC)
                )
            ) {
                ExpenseDashboardScreen(dao = dao, context = this)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDashboardScreen(dao: TransactionDao, context: Context) {
    val coroutineScope = rememberCoroutineScope()
    val months = remember { getAvailableMonths() }
    var selectedMonthIndex by remember { mutableIntStateOf(0) }
    var monthMenuExpanded by remember { mutableStateOf(false) }

    val currentMonth = months[selectedMonthIndex]
    val transactions by dao.getTransactionsByDateRange(currentMonth.startMillis, currentMonth.endMillis)
        .collectAsState(initial = emptyList())

    val allTransactions by dao.getAllTransactions().collectAsState(initial = emptyList())

    val totalDebit = transactions.filter { it.type == "DEBIT" }.sumOf { it.amount }
    val totalCredit = transactions.filter { it.type == "CREDIT" }.sumOf { it.amount }
    val balance = totalCredit - totalDebit

    val prefs = remember { context.getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE) }
    var budgetLimit by remember { mutableFloatStateOf(prefs.getFloat("budget_limit", 15000f)) }

    var showAddModal by remember { mutableStateOf(false) }
    var defaultModalMode by remember { mutableStateOf("UBER") }
    var editingItem by remember { mutableStateOf<Transaction?>(null) }
    var deletingItem by remember { mutableStateOf<Transaction?>(null) }

    // Driver Earnings Timeframe Filter: DAILY, WEEKLY, MONTHLY
    var earningsTimeframe by remember { mutableStateOf("DAILY") }

    // Calculate timestamps for Daily, Weekly, Monthly
    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance()

    // Daily start (12:00 AM today)
    val startOfDay = remember(now) {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.timeInMillis
    }

    // Weekly start (Start of current week)
    val startOfWeek = remember(now) {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_WEEK, c.firstDayOfWeek)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.timeInMillis
    }

    // Filter transactions for Driver earnings
    val driverEarningTxs = allTransactions.filter { (it.mode == "UBER" || it.mode == "RAPIDO") && it.type == "CREDIT" }

    val filteredDriverTxs = when (earningsTimeframe) {
        "DAILY" -> driverEarningTxs.filter { it.timestamp >= startOfDay }
        "WEEKLY" -> driverEarningTxs.filter { it.timestamp >= startOfWeek }
        else -> driverEarningTxs.filter { it.timestamp >= currentMonth.startMillis && it.timestamp <= currentMonth.endMillis }
    }

    val uberTotal = filteredDriverTxs.filter { it.mode == "UBER" }.sumOf { it.amount }
    val rapidoTotal = filteredDriverTxs.filter { it.mode == "RAPIDO" }.sumOf { it.amount }
    val driverTotal = uberTotal + rapidoTotal

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Driver & Expense Tracker", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(currentMonth.label, fontSize = 12.sp, color = Color.Gray)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { monthMenuExpanded = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Select Month")
                        }
                        DropdownMenu(
                            expanded = monthMenuExpanded,
                            onDismissRequest = { monthMenuExpanded = false }
                        ) {
                            months.forEachIndexed { index, item ->
                                DropdownMenuItem(
                                    text = { Text(item.label, fontWeight = if (index == selectedMonthIndex) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        selectedMonthIndex = index
                                        monthMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        exportPdf(context, currentMonth.label, transactions, totalCredit, totalDebit)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export PDF", tint = Color(0xFF0F172A))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { 
                    defaultModalMode = "UBER"
                    showAddModal = true 
                },
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Earning / Expense") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF8FAFC)),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // DRIVER EARNINGS TRACKER CARD
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFFEF3C7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Driver Earnings", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }

                            // Timeframe Tabs (Daily / Weekly / Monthly)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("DAILY" to "Day", "WEEKLY" to "Week", "MONTHLY" to "Month").forEach { (key, label) ->
                                    FilterChip(
                                        selected = earningsTimeframe == key,
                                        onClick = { earningsTimeframe = key },
                                        label = { Text(label, fontSize = 11.sp) },
                                        modifier = Modifier.height(32.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Total Earnings Header
                        Text("Total Platform Earnings", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            "₹${String.format("%,.0f", driverTotal)}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1E293B)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Platform-wise Cards (Uber & Rapido)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Uber Card
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("UBER", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "₹${String.format("%,.0f", uberTotal)}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            // Rapido Card
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("RAPIDO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "₹${String.format("%,.0f", rapidoTotal)}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD97706)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // MONTH OVERALL BALANCE & EXPENSES
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Monthly Net Savings / Balance", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        Text(
                            "₹${String.format("%,.2f", balance)}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Total Income (All)", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                                Text("₹${String.format("%,.0f", totalCredit)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Total Kharcha (Debit)", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                                Text("₹${String.format("%,.0f", totalDebit)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                            }
                        }
                    }
                }
            }

            // TRANSACTIONS HISTORY
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("All Entries (${transactions.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            if (transactions.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                        Text("Is mahine koi record nahi mila", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            } else {
                val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                items(transactions, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                val iconBg = when (item.mode) {
                                    "UBER" -> Color(0xFF0F172A)
                                    "RAPIDO" -> Color(0xFFF59E0B)
                                    "ONLINE" -> Color(0xFF3B82F6)
                                    else -> Color(0xFF10B981)
                                }

                                Box(
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(iconBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (item.mode) {
                                            "UBER", "RAPIDO" -> Icons.Default.Place
                                            "ONLINE" -> Icons.Default.Email
                                            else -> Icons.Default.ShoppingCart
                                        },
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(item.description, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFF1F5F9)
                                        ) {
                                            Text(
                                                item.mode,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF475569),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(sdf.format(Date(item.timestamp)), fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = (if (item.type == "CREDIT") "+ " else "- ") + "₹${String.format("%,.0f", item.amount)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (item.type == "CREDIT") Color(0xFF16A34A) else Color(0xFFDC2626)
                                )

                                IconButton(onClick = { editingItem = item }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { deletingItem = item }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Add Entry (Supports UBER, RAPIDO, CASH, ONLINE)
    if (showAddModal) {
        DriverEntryDialog(
            title = "Add Earning / Expense",
            initialMode = defaultModalMode,
            onDismiss = { showAddModal = false },
            onSave = { amount, desc, type, mode ->
                coroutineScope.launch {
                    dao.insertTransaction(
                        Transaction(
                            amount = amount,
                            type = type,
                            mode = mode,
                            description = desc
                        )
                    )
                    showAddModal = false
                }
            }
        )
    }

    // Modal: Edit Entry
    editingItem?.let { tx ->
        DriverEntryDialog(
            title = "Edit Entry",
            initialAmount = tx.amount.toString(),
            initialDesc = tx.description,
            initialType = tx.type,
            initialMode = tx.mode,
            onDismiss = { editingItem = null },
            onSave = { amount, desc, type, mode ->
                coroutineScope.launch {
                    dao.updateTransaction(
                        tx.copy(
                            amount = amount,
                            description = desc,
                            type = type,
                            mode = mode
                        )
                    )
                    editingItem = null
                }
            }
        )
    }

    // Modal: Delete Confirm
    deletingItem?.let { tx ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text("Delete Entry") },
            text = { Text("Kya aap ₹${tx.amount} ki entry delete karna chahte hain?") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    onClick = {
                        coroutineScope.launch {
                            dao.deleteTransaction(tx)
                            deletingItem = null
                        }
                    }
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingItem = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverEntryDialog(
    title: String,
    initialAmount: String = "",
    initialDesc: String = "",
    initialType: String = "CREDIT",
    initialMode: String = "UBER",
    onDismiss: () -> Unit,
    onSave: (amount: Double, desc: String, type: String, mode: String) -> Unit
) {
    var amount by remember { mutableStateOf(initialAmount) }
    var desc by remember { mutableStateOf(initialDesc) }
    var type by remember { mutableStateOf(initialType) }
    var mode by remember { mutableStateOf(initialMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Type Select
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "CREDIT",
                        onClick = { type = "CREDIT" },
                        label = { Text("Earning (Kamai)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = type == "DEBIT",
                        onClick = { type = "DEBIT" },
                        label = { Text("Expense (Kharcha)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Platform / Mode Selection
                Text("Platform / Category:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("UBER", "RAPIDO", "CASH", "ONLINE").forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { 
                                mode = m
                                if (desc.isBlank()) {
                                    desc = if (m == "UBER") "Uber Rides" else if (m == "RAPIDO") "Rapido Rides" else ""
                                }
                            },
                            label = { Text(m, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description / Note") },
                    placeholder = { Text("e.g. 10 rides, Petrol, etc.") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                onClick = {
                    val amt = amount.toDoubleOrNull()
                    if (amt != null && amt > 0) {
                        onSave(amt, desc.ifBlank { if (type == "CREDIT") "$mode Earning" else "Expense" }, type, mode)
                    }
                }
            ) { Text("Save Entry") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

fun exportPdf(
    context: Context,
    monthLabel: String,
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
    canvas.drawText("Driver & Expense Statement - $monthLabel", 40f, 50f, paint)

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
    canvas.drawText("Total Income: Rs. $totalCredit", 40f, 110f, paint)
    canvas.drawText("Total Debit: Rs. $totalDebit", 220f, 110f, paint)
    canvas.drawText("Net: Rs. ${totalCredit - totalDebit}", 400f, 110f, paint)

    canvas.drawLine(40f, 130f, 550f, 130f, paint)

    var y = 160f
    paint.isFakeBoldText = true
    canvas.drawText("Date", 40f, y, paint)
    canvas.drawText("Description", 150f, y, paint)
    canvas.drawText("Platform", 350f, y, paint)
    canvas.drawText("Type", 430f, y, paint)
    canvas.drawText("Amount", 500f, y, paint)

    paint.isFakeBoldText = false
    val sdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

    for (tx in transactions) {
        y += 24f
        if (y > 800f) break

        canvas.drawText(sdf.format(Date(tx.timestamp)), 40f, y, paint)
        val shortDesc = if (tx.description.length > 22) tx.description.take(22) + ".." else tx.description
        canvas.drawText(shortDesc, 150f, y, paint)
        canvas.drawText(tx.mode, 350f, y, paint)
        canvas.drawText(tx.type, 430f, y, paint)
        canvas.drawText("Rs. ${tx.amount}", 500f, y, paint)
    }

    pdfDocument.finishPage(page)

    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
    val file = File(dir, "Driver_Statement_${monthLabel.replace(" ", "_")}.pdf")

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