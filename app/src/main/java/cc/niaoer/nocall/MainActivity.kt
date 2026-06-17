package cc.niaoer.nocall

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cc.niaoer.nocall.ui.history.CallHistoryScreen
import cc.niaoer.nocall.ui.navigation.NavRoutes
import cc.niaoer.nocall.ui.rules.RuleEditScreen
import cc.niaoer.nocall.ui.rules.RulesScreen
import cc.niaoer.nocall.ui.settings.SettingsScreen
import cc.niaoer.nocall.ui.test.RuleTestScreen
import cc.niaoer.nocall.ui.theme.NoCallTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, app still works */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermission()

        setContent {
            NoCallTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NoCallNavHost()
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun NoCallNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = NavRoutes.RULES
    ) {
        composable(NavRoutes.RULES) {
            RulesScreen(
                onAddRule = { navController.navigate(NavRoutes.RULE_ADD) },
                onEditRule = { id -> navController.navigate(NavRoutes.ruleEdit(id)) },
                onTestRule = { navController.navigate(NavRoutes.RULE_TEST) },
                onHistory = { navController.navigate(NavRoutes.HISTORY) },
                onSettings = { navController.navigate(NavRoutes.SETTINGS) }
            )
        }
        composable(NavRoutes.RULE_ADD) {
            RuleEditScreen(
                ruleId = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = NavRoutes.RULE_EDIT,
            arguments = listOf(navArgument("ruleId") { type = NavType.LongType })
        ) { backStackEntry ->
            val ruleId = backStackEntry.arguments?.getLong("ruleId")
            RuleEditScreen(
                ruleId = ruleId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.RULE_TEST) {
            RuleTestScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.HISTORY) {
            CallHistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
