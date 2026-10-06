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
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

    val totalDebit = transactions.filter { it.type == "DEBIT" }.sumOf { it.amount }
    val totalCredit = transactions.filter { it.type == "CREDIT" }.sumOf { it.amount }
    val balance = totalCredit - totalDebit

    val prefs = remember { context.getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE) }
    var budgetLimit by remember { mutableFloatStateOf(prefs.getFloat("budget_limit", 15000f)) }

    var showAddModal by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<Transaction?>(null) }
    var deletingItem by remember { mutableStateOf<Transaction?>(null) }

    val isLimitExceeded = totalDebit >= budgetLimit

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Expense Dashboard", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(currentMonth.label, fontSize = 12.sp, color = Color.Gray)
                    }
                },
                actions = {
                    // Month Selector Dropdown
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

                    // PDF Export Button
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
                onClick = { showAddModal = true },
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Manual Entry") }
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
            // Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Net Balance", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        Text(
                            text = "₹${String.format("%,.2f", balance)}",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Income Box
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Credit (Aaya)", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                                }
                                Text("₹${String.format("%,.0f", totalCredit)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            }

                            // Expense Box
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFEF4444))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Debit (Kharcha)", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                                }
                                Text("₹${String.format("%,.0f", totalDebit)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                            }
                        }

                        if (isLimitExceeded) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0x33EF4444)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "⚠ Warning: Monthly limit (₹$budgetLimit) cross ho gayi hai!",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Analytics Bar Chart Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Month Overview Chart", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(14.dp))
                        AnalyticsBarChart(credit = totalCredit.toFloat(), debit = totalDebit.toFloat())
                    }
                }
            }

            // Transactions Header with PDF Icon
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Transactions (${transactions.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    TextButton(onClick = {
                        exportPdf(context, currentMonth.label, transactions, totalCredit, totalDebit)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF Export", fontSize = 12.sp)
                    }
                }
            }

            // Transaction Items
            if (transactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Is mahine koi transaction nahi hai", color = Color.Gray, fontSize = 14.sp)
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
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (item.type == "CREDIT") Color(0xFFDCFCE7) else Color(0xFFFEE2E2)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (item.mode == "ONLINE") Icons.Default.Email else Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = if (item.type == "CREDIT") Color(0xFF16A34A) else Color(0xFFDC2626),
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

    // Modal: Add Entry
    if (showAddModal) {
        EntryDialog(
            title = "Add Manual Entry",
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
        EntryDialog(
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

@Composable
fun AnalyticsBarChart(credit: Float, debit: Float) {
    val maxVal = maxOf(credit, debit, 1f)
    val creditRatio = credit / maxVal
    val debitRatio = debit / maxVal

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            // Credit Bar
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("₹${String.format("%,.0f", credit)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .fillMaxHeight(creditRatio.coerceIn(0.08f, 1f))
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(Color(0xFF10B981))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("Credit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            // Debit Bar
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("₹${String.format("%,.0f", debit)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .fillMaxHeight(debitRatio.coerceIn(0.08f, 1f))
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(Color(0xFFEF4444))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("Debit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDialog(
    title: String,
    initialAmount: String = "",
    initialDesc: String = "",
    initialType: String = "DEBIT",
    initialMode: String = "CASH",
    onDismiss: () -> Unit,
    onSave: (amount: Double, desc: String, type: String, mode: String) -> Unit
) {
    var amount by remember { mutableStateOf(initialAmount) }
    var desc by remember { mutableStateOf(initialDesc) }
    var type by remember { mutableStateOf(initialType) } // DEBIT, CREDIT
    var mode by remember { mutableStateOf(initialMode) } // CASH, ONLINE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Type Select
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "DEBIT",
                        onClick = { type = "DEBIT" },
                        label = { Text("Debit (Expense)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = type == "CREDIT",
                        onClick = { type = "CREDIT" },
                        label = { Text("Credit (Income)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Category Mode Select
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == "CASH",
                        onClick = { mode = "CASH" },
                        label = { Text("Cash") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = mode == "ONLINE",
                        onClick = { mode = "ONLINE" },
                        label = { Text("Online") },
                        modifier = Modifier.weight(1f)
                    )
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
                    placeholder = { Text("e.g. Shopping, Dinner, Salary") },
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
                        onSave(amt, desc.ifBlank { if (type == "DEBIT") "Expense" else "Income" }, type, mode)
                    }
                }
            ) { Text("Save") }
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
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 Size
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas
    val paint = Paint()

    // Title Header
    paint.textSize = 20f
    paint.isFakeBoldText = true
    paint.color = android.graphics.Color.BLACK
    canvas.drawText("Expense Statement - $monthLabel", 40f, 50f, paint)

    paint.textSize = 12f
    paint.isFakeBoldText = false
    paint.color = android.graphics.Color.DKGRAY
    canvas.drawText("Generated on: ${SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date())}", 40f, 70f, paint)

    // Summary Box
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

    // Table Header
    var y = 160f
    paint.isFakeBoldText = true
    canvas.drawText("Date", 40f, y, paint)
    canvas.drawText("Description", 150f, y, paint)
    canvas.drawText("Category", 350f, y, paint)
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
    val file = File(dir, "Statement_${monthLabel.replace(" ", "_")}.pdf")

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