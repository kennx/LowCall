package cc.niaoer.nocall

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import cc.niaoer.nocall.ui.whitelist.WhitelistScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {

    private val _isScreeningEnabled = MutableStateFlow(false)
    val isScreeningEnabled = _isScreeningEnabled.asStateFlow()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, app still works */ }

    private val screeningRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshScreeningStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermission()
        refreshScreeningStatus()

        setContent {
            NoCallTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val screeningEnabled by isScreeningEnabled.collectAsState()
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (!screeningEnabled) {
                            SetupBanner(onSetup = { requestScreeningRole() })
                        }
                        NoCallNavHost()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshScreeningStatus()
    }

    private fun refreshScreeningStatus() {
        _isScreeningEnabled.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } else {
            true // API < 29: user must manually enable in system settings
        }
    }

    private fun requestScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val intent = getSystemService(RoleManager::class.java)
                .createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            screeningRoleLauncher.launch(intent)
        } else {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun SetupBanner(onSetup: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.setup_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onSetup,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.open_settings))
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
                onWhitelist = { navController.navigate(NavRoutes.WHITELIST) },
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
        composable(NavRoutes.WHITELIST) {
            WhitelistScreen(
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
