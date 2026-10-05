package com.example.expensetracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
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
                    primary = Color(0xFF1E88E5),
                    secondary = Color(0xFF26A69A),
                    surface = Color(0xFFF8F9FA)
                )
            ) {
                ExpenseAppScreen(dao = dao, context = this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseAppScreen(dao: TransactionDao, context: Context) {
    val coroutineScope = rememberCoroutineScope()
    val transactions by dao.getAllTransactions().collectAsState(initial = emptyList())
    val totalDebit by dao.getTotalDebit().collectAsState(initial = 0.0)
    val totalCredit by dao.getTotalCredit().collectAsState(initial = 0.0)

    val prefs = remember { context.getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE) }
    var budgetLimit by remember { mutableFloatStateOf(prefs.getFloat("budget_limit", 10000f)) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showLimitDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<Transaction?>(null) }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, DEBIT, CREDIT

    val spent = totalDebit ?: 0.0
    val received = totalCredit ?: 0.0
    val limit = budgetLimit.toDouble()
    val progress = if (limit > 0) (spent / limit).toFloat().coerceIn(0f, 1f) else 0f
    val isOverLimit = spent >= limit

    // Delete Confirmation Dialog
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
            title = { Text("Delete Entry?") },
            text = { Text("Kya aap Rs. ${itemToDelete?.amount} ki ye entry delete karna chahte hain?") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    onClick = {
                        itemToDelete?.let { tx ->
                            coroutineScope.launch {
                                dao.deleteTransaction(tx)
                                itemToDelete = null
                            }
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Set Limit Dialog
    if (showLimitDialog) {
        var tempLimit by remember { mutableStateOf(budgetLimit.toInt().toString()) }
        AlertDialog(
            onDismissRequest = { showLimitDialog = false },
            title = { Text("Set Monthly Expense Limit") },
            text = {
                OutlinedTextField(
                    value = tempLimit,
                    onValueChange = { tempLimit = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Limit (₹)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val newLim = tempLimit.toFloatOrNull() ?: budgetLimit
                    budgetLimit = newLim
                    prefs.edit().putFloat("budget_limit", newLim).apply()
                    showLimitDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLimitDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Add Cash Entry Dialog
    if (showAddDialog) {
        var cashAmount by remember { mutableStateOf("") }
        var cashDesc by remember { mutableStateOf("") }
        var isExpense by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Manual Entry") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = isExpense,
                            onClick = { isExpense = true },
                            label = { Text("Expense (Cash)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !isExpense,
                            onClick = { isExpense = false },
                            label = { Text("Income (Cash)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = cashAmount,
                        onValueChange = { cashAmount = it },
                        label = { Text("Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cashDesc,
                        onValueChange = { cashDesc = it },
                        label = { Text("Note / Description") },
                        placeholder = { Text("e.g. Chai, Auto, Grocery") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = cashAmount.toDoubleOrNull()
                    if (amt != null && amt > 0) {
                        coroutineScope.launch {
                            dao.insertTransaction(
                                Transaction(
                                    amount = amt,
                                    type = if (isExpense) "DEBIT" else "CREDIT",
                                    mode = "CASH",
                                    description = cashDesc.ifBlank { if (isExpense) "Cash Expense" else "Cash Received" }
                                )
                            )
                            showAddDialog = false
                        }
                    }
                }) {
                    Text("Add Entry")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Expense Tracker", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Auto SMS & Cash Manager", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                actions = {
                    IconButton(onClick = { showLimitDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Budget Limit", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Cash") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF5F6F8))
        ) {
            // Summary Dashboard Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Kharcha (Debited)", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                "₹${String.format("%,.0f", spent)}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFD32F2F)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total Aaya (Credited)", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                "₹${String.format("%,.0f", received)}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Budget Progress Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isOverLimit) "⚠️️ Limit Cross Ho Gayi!" else "Budget: ₹${String.format("%,.0f", spent)} / ₹${String.format("%,.0f", limit)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isOverLimit) Color.Red else Color(0xFF555555)
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOverLimit) Color.Red else MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = when {
                            isOverLimit -> Color(0xFFD32F2F)
                            progress >= 0.8f -> Color(0xFFFF9800)
                            else -> Color(0xFF1E88E5)
                        },
                        trackColor = Color(0xFFEEEEEE)
                    )
                }
            }

            // Filter Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL" to "All", "DEBIT" to "Kharcha", "CREDIT" to "Income").forEach { (key, label) ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Filtered Transactions List
            val filteredTransactions = transactions.filter {
                if (selectedFilter == "ALL") true else it.type == selectedFilter
            }

            if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Koi entry nahi mili", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTransactions, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Mode Icon (SMS vs Cash)
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (item.type == "DEBIT") Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (item.mode == "BANK_SMS") Icons.Default.Email else Icons.Default.AccountBox,
                                            contentDescription = null,
                                            tint = if (item.type == "DEBIT") Color(0xFFD32F2F) else Color(0xFF2E7D32),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(
                                            text = item.description,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "${if (item.mode == "BANK_SMS") "Bank SMS" else "Cash"} • ${sdf.format(Date(item.timestamp))}",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = (if (item.type == "DEBIT") "- " else "+ ") + "₹${String.format("%,.0f", item.amount)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (item.type == "DEBIT") Color(0xFFD32F2F) else Color(0xFF2E7D32)
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Delete Button
                                    IconButton(
                                        onClick = { itemToDelete = item },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color.LightGray,
                                            modifier = Modifier.size(18.dp)
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
}