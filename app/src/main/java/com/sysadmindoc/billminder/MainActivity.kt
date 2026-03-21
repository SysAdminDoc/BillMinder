package com.sysadmindoc.billminder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sysadmindoc.billminder.ui.screens.*
import com.sysadmindoc.billminder.ui.theme.BillMinderTheme
import com.sysadmindoc.billminder.viewmodel.BillViewModel

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, we proceed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()

        setContent {
            BillMinderTheme {
                BillMinderNavHost()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun BillMinderNavHost() {
    val navController = rememberNavController()
    val viewModel: BillViewModel = viewModel()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onAddBill = { navController.navigate("add_edit/0") },
                onBillTap = { navController.navigate("detail/$it") },
                onCalendar = { navController.navigate("calendar") }
            )
        }

        composable(
            "add_edit/{billId}",
            arguments = listOf(navArgument("billId") { type = NavType.LongType })
        ) { backStackEntry ->
            val billId = backStackEntry.arguments?.getLong("billId")
            AddEditBillScreen(
                viewModel = viewModel,
                billId = if (billId == 0L) null else billId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            "detail/{billId}",
            arguments = listOf(navArgument("billId") { type = NavType.LongType })
        ) { backStackEntry ->
            val billId = backStackEntry.arguments?.getLong("billId") ?: return@composable
            BillDetailScreen(
                viewModel = viewModel,
                billId = billId,
                onNavigateBack = { navController.popBackStack() },
                onEdit = { navController.navigate("add_edit/$it") }
            )
        }

        composable("calendar") {
            CalendarScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onBillTap = { navController.navigate("detail/$it") }
            )
        }
    }
}
