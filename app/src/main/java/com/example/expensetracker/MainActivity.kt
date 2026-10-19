package com.example.expensetracker

import android.Manifest
import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

enum class Tab(val label: String, val emoji: String) {
    HOME("Home", "🏠"), ENTRIES("Entries", "🧾"), STATS("Analytics", "📊"), KHATA("Khata", "🤝"), MORE("More", "⚙️")
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    private val credLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) vm.unlocked = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        DailyWorker.schedule(this)

        setContent {
            val theme by vm.themeMode.collectAsState()
            val lock by vm.appLock.collectAsState()
            ExpenseTheme(theme) {
                if (lock && !vm.unlocked) LockScreen(onUnlock = { promptUnlock() }) else MainApp(vm)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (vm.appLock.value && !isChangingConfigurations) vm.unlocked = false
    }

    private fun promptUnlock() {
        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isDeviceSecure) { vm.unlocked = true; return }
        @Suppress("DEPRECATION")
        val intent = km.createConfirmDeviceCredentialIntent("Unlock Expense Tracker", "Verify it's you")
        if (intent == null) vm.unlocked = true else credLauncher.launch(intent)
    }

    private fun requestPermissions() {
        val perms = mutableListOf<String>()
        if (vm.smsTracking.value) { perms += Manifest.permission.RECEIVE_SMS; perms += Manifest.permission.READ_SMS }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms += Manifest.permission.POST_NOTIFICATIONS
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Text("🔒", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text("Expense Tracker is locked", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Th.text)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Unlock", onUnlock, Modifier.fillMaxWidth(0.6f))
    }
}

@Composable
fun MainApp(vm: AppViewModel) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.values()[tabIndex]
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var addTx by remember { mutableStateOf(false) }
    var editTx by remember { mutableStateOf<Transaction?>(null) }
    var addLoan by remember { mutableStateOf(false) }
    var pendingSub by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = tab != Tab.HOME) { tabIndex = 0 }

    val onDelete: (Transaction) -> Unit = { tx ->
        vm.deleteTransaction(tx)
        scope.launch {
            val r = snackbar.showSnackbar("Entry deleted", actionLabel = "UNDO", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.restoreTransaction(tx)
        }
    }

    Scaffold(
        containerColor = Th.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Th.surface) {
                Tab.values().forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tabIndex == i, onClick = { tabIndex = i },
                        icon = { Text(t.emoji, fontSize = 20.sp) },
                        label = { Text(t.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = Th.primary, unselectedTextColor = Th.textMuted, indicatorColor = Th.surfaceAlt
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.HOME || tab == Tab.ENTRIES) {
                ExtendedFloatingActionButton(
                    onClick = { addTx = true }, containerColor = Th.primary, contentColor = Th.onPrimary,
                    text = { Text("+  Add", fontWeight = FontWeight.Bold) }, icon = {}
                )
            } else if (tab == Tab.KHATA) {
                ExtendedFloatingActionButton(
                    onClick = { addLoan = true }, containerColor = Th.primary, contentColor = Th.onPrimary,
                    text = { Text("+  Add record", fontWeight = FontWeight.Bold) }, icon = {}
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.HOME -> HomeScreen(vm, onSeeAll = { tabIndex = 1 }, onEdit = { editTx = it },
                    onOpenBudgets = { pendingSub = "BUDGETS"; tabIndex = Tab.MORE.ordinal })
                Tab.ENTRIES -> EntriesScreen(vm, onEdit = { editTx = it })
                Tab.STATS -> AnalyticsScreen(vm)
                Tab.KHATA -> KhataScreen(vm, showAddLoan = addLoan, onAddLoanDismiss = { addLoan = false })
                Tab.MORE -> SettingsScreen(vm, initialSub = pendingSub, onConsumed = { pendingSub = null })
            }
        }
    }

    if (addTx) TxDialog(vm, null, onDismiss = { addTx = false })
    editTx?.let { tx -> TxDialog(vm, tx, onDismiss = { editTx = null }, onDelete = onDelete) }
}
