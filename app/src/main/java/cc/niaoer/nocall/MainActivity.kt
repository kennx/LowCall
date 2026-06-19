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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cc.niaoer.nocall.ui.history.CallHistoryScreen
import cc.niaoer.nocall.ui.home.HomeScreen
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
                    NoCallApp(screeningEnabled = screeningEnabled, onSetup = { requestScreeningRole() })
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
fun NoCallApp(screeningEnabled: Boolean, onSetup: () -> Unit) {
    val navController = rememberNavController()

    val bottomNavItems = listOf(
        NavItem(NavRoutes.HOME, R.string.home_title, Icons.Default.Home),
        NavItem(NavRoutes.RULES, R.string.rules_title, Icons.Default.Rule),
        NavItem(NavRoutes.HISTORY, R.string.history_title, Icons.Default.History),
        NavItem(NavRoutes.WHITELIST, R.string.whitelist_title, Icons.Default.PlaylistAddCheck),
        NavItem(NavRoutes.SETTINGS, R.string.settings_title, Icons.Default.Settings)
    )

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val showBottomBar = bottomNavItems.any { it.route == currentDestination?.route }
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.labelRes)) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (!screeningEnabled) {
                SetupBanner(onSetup = onSetup)
            }
            NoCallNavHost(navController = navController)
        }
    }
}

private data class NavItem(
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun SetupBanner(onSetup: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
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
fun NoCallNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.HOME
    ) {
        composable(NavRoutes.HOME) {
            HomeScreen(
                onNavigateToRules = { navController.navigate(NavRoutes.RULES) },
                onNavigateToHistory = { navController.navigate(NavRoutes.HISTORY) },
                onNavigateToTest = { navController.navigate(NavRoutes.RULE_TEST) },
                onNavigateToAddRule = { navController.navigate(NavRoutes.RULE_ADD) }
            )
        }
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
