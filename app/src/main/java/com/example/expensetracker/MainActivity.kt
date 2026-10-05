package com.example.expensetracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

        // Runtime Permissions check
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
            MaterialTheme {
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
    var budgetInput by remember { mutableStateOf(prefs.getFloat("budget_limit", 10000f).toString()) }

    var cashAmount by remember { mutableStateOf("") }
    var cashDesc by remember { mutableStateOf("") }

    val currentTotalDebit = totalDebit ?: 0.0
    val currentLimit = budgetInput.toDoubleOrNull() ?: 10000.0
    val isOverBudget = currentTotalDebit >= currentLimit

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Expense & SMS Tracker") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            // Summary Cards
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOverBudget) Color(0xFFFFCDD2) else Color(0xFFE8F5E9)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Total Debited (Kharcha): Rs. ${String.format("%.2f", currentTotalDebit)}", fontWeight = FontWeight.Bold, color = Color.Red)
                    Text("Total Credited (Aaya): Rs. ${String.format("%.2f", totalCredit ?: 0.0)}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isOverBudget) {
                        Text("WARNING: Aapka kharcha limit cross kar chuka hai!", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Budget Limit Setter
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = budgetInput,
                    onValueChange = { budgetInput = it },
                    label = { Text("Expense Limit (Rs.)") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    val limit = budgetInput.toFloatOrNull() ?: 10000f
                    prefs.edit().putFloat("budget_limit", limit).apply()
                }) {
                    Text("Set Limit")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cash Entry Form
            Text("Add Cash Expense:", fontWeight = FontWeight.SemiBold)
            Row {
                OutlinedTextField(
                    value = cashAmount,
                    onValueChange = { cashAmount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = cashDesc,
                    onValueChange = { cashDesc = it },
                    label = { Text("Note (e.g. Chai, Auto)") },
                    modifier = Modifier.weight(1.5f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val amt = cashAmount.toDoubleOrNull()
                    if (amt != null && amt > 0) {
                        coroutineScope.launch {
                            dao.insertTransaction(
                                Transaction(
                                    amount = amt,
                                    type = "DEBIT",
                                    mode = "CASH",
                                    description = cashDesc.ifBlank { "Cash Expense" }
                                )
                            )
                            cashAmount = ""
                            cashDesc = ""
                        }
                    }
                }
            ) {
                Text("Save Cash Entry")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Transactions History List
            Text("Recent Transactions (SMS & Cash):", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(transactions) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.description, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(
                                    "${item.mode} • ${sdf.format(Date(item.timestamp))}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            Text(
                                text = (if (item.type == "DEBIT") "- " else "+ ") + "Rs. ${item.amount}",
                                fontWeight = FontWeight.Bold,
                                color = if (item.type == "DEBIT") Color.Red else Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }
        }
    }
}