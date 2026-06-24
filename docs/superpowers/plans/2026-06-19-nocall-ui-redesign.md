# LowCall UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign LowCall app UI following OpenDesign spec: add bottom navigation, home screen, tab filtering, search, and redesigned cards while keeping all existing functionality.

**Architecture:** Material3 NavigationBar + NavHost with 5 top-level destinations. HomeScreen queries CallLogDao for stats. RulesScreen uses ScrollableTabRow with existing RuleType. WhitelistScreen adds search filtering existing entries and contacts. No database schema changes.

**Tech Stack:** Jetpack Compose, Material3, Navigation Compose, Room, Kotlin Coroutines/Flow

---

## File Structure

| File | Responsibility |
|------|---------------|
| `NavRoutes.kt` | Route constants, add `HOME` |
| `MainActivity.kt` | NavHost with BottomNavigation, startDestination = HOME |
| `ui/home/HomeScreen.kt` | Hero stats, quick cards, recent blocked list |
| `ui/home/HomeViewModel.kt` | Exposes stats + recent calls via StateFlow |
| `data/db/CallLogDao.kt` | Count queries for stats |
| `data/db/BlockRuleDao.kt` | Search by pattern/description |
| `data/db/WhitelistDao.kt` | Search by phone/note |
| `ui/rules/RulesScreen.kt` | ScrollableTabRow + ElevatedCard redesign |
| `ui/rules/RulesViewModel.kt` | Tab filtering + search |
| `ui/history/CallHistoryScreen.kt` | Date-grouped list with avatar styling |
| `ui/whitelist/WhitelistScreen.kt` | Search bar + contact search |
| `ui/whitelist/WhitelistViewModel.kt` | Search logic |
| `ui/test/RuleTestScreen.kt` | Result card with animation |
| `ui/settings/SettingsScreen.kt` | Grouped settings with about card |
| `res/values/strings.xml` | New strings |

---

## Task 1: Data Layer — Add Count and Search Queries

**Files:**
- Modify: `app/src/main/java/cc/niaoer/lowcall/data/db/CallLogDao.kt`
- Modify: `app/src/main/java/cc/niaoer/lowcall/data/db/BlockRuleDao.kt`
- Modify: `app/src/main/java/cc/niaoer/lowcall/data/db/WhitelistDao.kt`

- [ ] **Step 1: Add count queries to CallLogDao**

Add these methods to `CallLogDao`:

```kotlin
@Query("SELECT COUNT(*) FROM call_logs WHERE action = 'BLOCKED'")
suspend fun getTotalBlockedCount(): Int

@Query("SELECT COUNT(*) FROM call_logs WHERE action = 'BLOCKED' AND timestamp >= :startOfDay")
suspend fun getBlockedCountSince(startOfDay: Long): Int

@Query("SELECT * FROM call_logs WHERE action = 'BLOCKED' ORDER BY timestamp DESC LIMIT :limit")
suspend fun getRecentBlocked(limit: Int): List<CallLog>
```

- [ ] **Step 2: Add search to BlockRuleDao**

Add to `BlockRuleDao`:

```kotlin
@Query("SELECT * FROM block_rules WHERE pattern LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY id DESC")
suspend fun searchRules(query: String): List<BlockRule>
```

- [ ] **Step 3: Add search to WhitelistDao**

Add to `WhitelistDao`:

```kotlin
@Query("SELECT * FROM whitelist WHERE phone_number LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%' ORDER BY id DESC")
suspend fun searchEntries(query: String): List<WhitelistEntry>
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/data/db/
git commit -m "feat: add count and search queries to DAOs"
```

---

## Task 2: NavRoutes — Add Home Route

**Files:**
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/navigation/NavRoutes.kt`

- [ ] **Step 1: Add HOME constant**

Replace the file content with:

```kotlin
package cc.niaoer.lowcall.ui.navigation

object NavRoutes {
    const val HOME = "home"
    const val RULES = "rules"
    const val RULE_ADD = "rules/add"
    const val RULE_EDIT = "rules/{ruleId}"
    const val RULE_TEST = "rules/test"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val WHITELIST = "whitelist"

    fun ruleEdit(ruleId: Long): String = "rules/$ruleId"
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/ui/navigation/NavRoutes.kt
git commit -m "feat: add HOME route to NavRoutes"
```

---

## Task 3: HomeViewModel — Stats and Recent Calls

**Files:**
- Create: `app/src/main/java/cc/niaoer/lowcall/ui/home/HomeViewModel.kt`

- [ ] **Step 1: Create HomeViewModel**

```kotlin
package cc.niaoer.lowcall.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.lowcall.data.db.CallLogDao
import cc.niaoer.lowcall.data.model.CallLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeViewModel(
    private val callLogDao: CallLogDao = cc.niaoer.lowcall.AppContainer.database.callLogDao()
) : ViewModel() {

    data class UiState(
        val totalBlocked: Int = 0,
        val todayBlocked: Int = 0,
        val weekBlocked: Int = 0,
        val recentBlocked: List<CallLog> = emptyList()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            val total = callLogDao.getTotalBlockedCount()
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val today = callLogDao.getBlockedCountSince(todayStart)

            val weekStart = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -7)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val week = callLogDao.getBlockedCountSince(weekStart)

            val recent = callLogDao.getRecentBlocked(3)

            _uiState.value = UiState(
                totalBlocked = total,
                todayBlocked = today,
                weekBlocked = week,
                recentBlocked = recent
            )
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/ui/home/HomeViewModel.kt
git commit -m "feat: add HomeViewModel with stats and recent calls"
```

---

## Task 4: HomeScreen — Hero, Quick Cards, Recent List

**Files:**
- Create: `app/src/main/java/cc/niaoer/lowcall/ui/home/HomeScreen.kt`

- [ ] **Step 1: Create HomeScreen**

```kotlin
package cc.niaoer.lowcall.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.niaoer.lowcall.R
import cc.niaoer.lowcall.data.model.CallLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToRules: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToTest: () -> Unit,
    onNavigateToAddRule: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { /* notifications */ }) {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.Notifications,
                            contentDescription = stringResource(R.string.notification_setting),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAddRule) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_rule))
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                HeroSection(
                    totalBlocked = uiState.totalBlocked,
                    todayBlocked = uiState.todayBlocked,
                    weekBlocked = uiState.weekBlocked
                )
            }

            item {
                QuickGrid(
                    onNavigateToRules = onNavigateToRules,
                    onNavigateToHistory = onNavigateToHistory,
                    onNavigateToTest = onNavigateToTest,
                    onNavigateToAddRule = onNavigateToAddRule
                )
            }

            item {
                Text(
                    text = "最近拦截",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            items(uiState.recentBlocked, key = { it.id }) { log ->
                RecentBlockedItem(log = log)
            }
        }
    }
}

@Composable
private fun HeroSection(
    totalBlocked: Int,
    todayBlocked: Int,
    weekBlocked: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "防护已开启",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = totalBlocked.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "通骚扰来电被拦截",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "今日已拦截 ${todayBlocked} 通，本周累计 ${weekBlocked} 通",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun QuickGrid(
    onNavigateToRules: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToTest: () -> Unit,
    onNavigateToAddRule: () -> Unit
) {
    val items = listOf(
        Triple("拦截规则", "规则运行中") { onNavigateToRules() },
        Triple("拦截历史", "查看最近记录") { onNavigateToHistory() },
        Triple("测试拦截", "验证号码匹配") { onNavigateToTest() },
        Triple("添加规则", "新建拦截规则") { onNavigateToAddRule() }
    )

    val icons = listOf(
        Icons.Default.Rule,
        Icons.Default.History,
        Icons.Default.Science,
        Icons.Default.Add
    )

    val containerColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.primaryContainer
    )

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        for (row in 0 until 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (col in 0 until 2) {
                    val index = row * 2 + col
                    val (label, hint, onClick) = items[index]
                    QuickCard(
                        label = label,
                        hint = hint,
                        icon = icons[index],
                        containerColor = containerColors[index],
                        onClick = onClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickCard(
    label: String,
    hint: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .padding(8.dp)
            ) {
                Icon(icon, contentDescription = null)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentBlockedItem(log: CallLog) {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.phoneNumber,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${log.matchedRulePattern ?: "未知规则"} · ${dateFormat.format(Date(log.timestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

- [ ] **Step 2: Add new strings to strings.xml**

Add to `app/src/main/res/values/strings.xml` (inside `<resources>`):

```xml
<string name="home_title">首页</string>
<string name="home_greeting">防护已开启</string>
<string name="home_stat_suffix">通骚扰来电被拦截</string>
<string name="home_today_week">今日已拦截 %1$d 通，本周累计 %2$d 通</string>
<string name="recent_blocked">最近拦截</string>
<string name="quick_rules">拦截规则</string>
<string name="quick_rules_hint">规则运行中</string>
<string name="quick_history">拦截历史</string>
<string name="quick_history_hint">查看最近记录</string>
<string name="quick_test">测试拦截</string>
<string name="quick_test_hint">验证号码匹配</string>
<string name="quick_add">添加规则</string>
<string name="quick_add_hint">新建拦截规则</string>
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/ui/home/HomeScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat: add HomeScreen with hero, quick cards, and recent blocked list"
```

---

## Task 5: MainActivity — Bottom Navigation + NavHost Update

**Files:**
- Modify: `app/src/main/java/cc/niaoer/lowcall/MainActivity.kt`

- [ ] **Step 1: Update MainActivity with BottomNavigation**

Replace the `LowCallNavHost` composable and update imports:

```kotlin
package cc.niaoer.lowcall

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import cc.niaoer.lowcall.ui.history.CallHistoryScreen
import cc.niaoer.lowcall.ui.home.HomeScreen
import cc.niaoer.lowcall.ui.navigation.NavRoutes
import cc.niaoer.lowcall.ui.rules.RuleEditScreen
import cc.niaoer.lowcall.ui.rules.RulesScreen
import cc.niaoer.lowcall.ui.settings.SettingsScreen
import cc.niaoer.lowcall.ui.test.RuleTestScreen
import cc.niaoer.lowcall.ui.theme.LowCallTheme
import cc.niaoer.lowcall.ui.whitelist.WhitelistScreen
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
            LowCallTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val screeningEnabled by isScreeningEnabled.collectAsState()
                    LowCallApp(screeningEnabled = screeningEnabled, onSetup = { requestScreeningRole() })
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
            true
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
fun LowCallApp(screeningEnabled: Boolean, onSetup: () -> Unit) {
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
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (!screeningEnabled) {
                SetupBanner(onSetup = onSetup)
            }
            LowCallNavHost(navController = navController)
        }
    }
}

private data class NavItem(val route: String, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

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
            Text(
                text = stringResource(R.string.setup_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
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
fun LowCallNavHost(navController: NavHostController) {
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
```

- [ ] **Step 2: Add missing string for home title**

Add to `strings.xml`:

```xml
<string name="home_title">首页</string>
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/MainActivity.kt app/src/main/res/values/strings.xml
git commit -m "feat: add bottom navigation and home screen as start destination"
```

---

## Task 6: RulesScreen — Tab Filtering + Search + Card Redesign

**Files:**
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/rules/RulesScreen.kt`
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/rules/RulesViewModel.kt`

- [ ] **Step 1: Update RulesViewModel with filtering and search**

Replace `RulesViewModel`:

```kotlin
package cc.niaoer.lowcall.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.lowcall.AppContainer
import cc.niaoer.lowcall.data.db.BlockRuleDao
import cc.niaoer.lowcall.data.model.BlockRule
import cc.niaoer.lowcall.data.model.RuleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RulesViewModel(
    private val blockRuleDao: BlockRuleDao = AppContainer.database.blockRuleDao()
) : ViewModel() {

    data class UiState(
        val rules: List<BlockRule> = emptyList(),
        val selectedTab: RuleType? = null,
        val searchQuery: String = "",
        val isSearching: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadRules()
    }

    fun loadRules() {
        viewModelScope.launch {
            val rules = blockRuleDao.getAll()
            _uiState.value = _uiState.value.copy(rules = rules)
        }
    }

    fun selectTab(type: RuleType?) {
        _uiState.value = _uiState.value.copy(selectedTab = type)
        applyFilter()
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilter()
    }

    fun toggleSearch() {
        _uiState.value = _uiState.value.copy(
            isSearching = !_uiState.value.isSearching,
            searchQuery = ""
        )
        applyFilter()
    }

    private fun applyFilter() {
        viewModelScope.launch {
            val query = _uiState.value.searchQuery
            val tab = _uiState.value.selectedTab

            val rules = if (query.isNotBlank()) {
                blockRuleDao.searchRules(query)
            } else {
                blockRuleDao.getAll()
            }

            val filtered = if (tab != null) {
                rules.filter { it.ruleType == tab }
            } else {
                rules
            }

            _uiState.value = _uiState.value.copy(rules = filtered)
        }
    }

    fun toggleEnabled(rule: BlockRule) {
        viewModelScope.launch {
            blockRuleDao.update(rule.copy(enabled = !rule.enabled))
            applyFilter()
        }
    }

    fun deleteRule(rule: BlockRule) {
        viewModelScope.launch {
            blockRuleDao.delete(rule)
            applyFilter()
        }
    }
}
```

- [ ] **Step 2: Redesign RulesScreen with tabs and search**

Replace `RulesScreen`:

```kotlin
package cc.niaoer.lowcall.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.niaoer.lowcall.R
import cc.niaoer.lowcall.data.model.BlockRule
import cc.niaoer.lowcall.data.model.RuleType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    onAddRule: () -> Unit,
    onEditRule: (Long) -> Unit,
    onTestRule: () -> Unit,
    onWhitelist: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    viewModel: RulesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSearching) {
                        TextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            placeholder = { Text("搜索规则") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(stringResource(R.string.rules_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = viewModel::toggleSearch) {
                        Icon(
                            if (uiState.isSearching) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "搜索"
                        )
                    }
                    IconButton(onClick = { /* more menu */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRule) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_rule))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val tabs = listOf(null to "全部") + RuleType.entries.map { it to typeLabel(it) }
            val selectedIndex = tabs.indexOfFirst { it.first == uiState.selectedTab }

            ScrollableTabRow(
                selectedTabIndex = if (selectedIndex >= 0) selectedIndex else 0,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, (type, label) ->
                    Tab(
                        selected = selectedIndex == index,
                        onClick = { viewModel.selectTab(type) },
                        text = { Text(label) }
                    )
                }
            }

            if (uiState.rules.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_rules),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.rules, key = { it.id }) { rule ->
                        RuleCard(
                            rule = rule,
                            onToggle = { viewModel.toggleEnabled(rule) },
                            onClick = { onEditRule(rule.id) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: BlockRule,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        elevation = CardDefaults.elevatedCardElevation(),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled)
                MaterialTheme.colorScheme.surfaceContainerHigh
            else
                MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.pattern,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RuleTypeChip(rule.ruleType)
                    if (rule.description.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = rule.description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun RuleTypeChip(type: RuleType) {
    AssistChip(
        onClick = {},
        label = { Text(typeLabel(type), style = MaterialTheme.typography.labelSmall) }
    )
}

private fun typeLabel(type: RuleType): String = when (type) {
    RuleType.EXACT -> "精确"
    RuleType.WILDCARD -> "通配"
    RuleType.REGEX -> "正则"
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/ui/rules/
git commit -m "feat: redesign RulesScreen with tab filtering and search"
```

---

## Task 7: CallHistoryScreen — Date Grouping + Avatar Styling

**Files:**
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/history/CallHistoryScreen.kt`

- [ ] **Step 1: Redesign with date grouping**

Replace `CallHistoryScreen`:

```kotlin
package cc.niaoer.lowcall.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.niaoer.lowcall.R
import cc.niaoer.lowcall.data.normalizePhone
import cc.niaoer.lowcall.data.model.CallAction
import cc.niaoer.lowcall.data.model.CallLog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: CallHistoryViewModel = viewModel()
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val whitelistedNumbers by viewModel.whitelistedNumbers.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_history),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val grouped = groupByDate(logs)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                grouped.forEach { (dateLabel, dateLogs) ->
                    item {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(dateLogs, key = { it.id }) { log ->
                        CallLogItem(
                            log = log,
                            isWhitelisted = normalizePhone(log.phoneNumber) in whitelistedNumbers,
                            onAddToWhitelist = { viewModel.addToWhitelist(log.phoneNumber) }
                        )
                    }
                }
            }
        }
    }
}

private fun groupByDate(logs: List<CallLog>): List<Pair<String, List<CallLog>>> {
    val calendar = Calendar.getInstance()
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val yesterday = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val dateFormat = SimpleDateFormat("M月d日", Locale.getDefault())

    return logs.groupBy { log ->
        calendar.timeInMillis = log.timestamp
        val logDay = Calendar.getInstance().apply {
            timeInMillis = log.timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when {
            logDay.timeInMillis == today.timeInMillis -> "今天"
            logDay.timeInMillis == yesterday.timeInMillis -> "昨天"
            else -> dateFormat.format(Date(log.timestamp))
        }
    }.toList()
}

@Composable
private fun CallLogItem(
    log: CallLog,
    isWhitelisted: Boolean,
    onAddToWhitelist: () -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val isBlocked = log.action == CallAction.BLOCKED

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isBlocked) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                } else {
                    Icon(
                        Icons.Default.CallReceived,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.phoneNumber,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isBlocked) {
                        "${log.matchedRulePattern ?: "未知规则"} · 响铃 0 秒"
                    } else {
                        "已放行 · 白名单匹配"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            ActionChip(isBlocked)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = timeFormat.format(Date(log.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionChip(isBlocked: Boolean) {
    val label = if (isBlocked)
        stringResource(R.string.blocked)
    else
        stringResource(R.string.allowed)
    val containerColor = if (isBlocked)
        MaterialTheme.colorScheme.errorContainer
    else
        MaterialTheme.colorScheme.primaryContainer
    val labelColor = if (isBlocked)
        MaterialTheme.colorScheme.onErrorContainer
    else
        MaterialTheme.colorScheme.onPrimaryContainer

    AssistChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = labelColor
        )
    )
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/ui/history/CallHistoryScreen.kt
git commit -m "feat: redesign CallHistoryScreen with date grouping and avatar styling"
```

---

## Task 8: WhitelistScreen — Search Bar + Contact Search

**Files:**
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/whitelist/WhitelistScreen.kt`
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/whitelist/WhitelistViewModel.kt`

- [ ] **Step 1: Update WhitelistViewModel with search**

Replace `WhitelistViewModel`:

```kotlin
package cc.niaoer.lowcall.ui.whitelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.lowcall.AppContainer
import cc.niaoer.lowcall.data.ContactLookup
import cc.niaoer.lowcall.data.db.WhitelistDao
import cc.niaoer.lowcall.data.model.WhitelistEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WhitelistViewModel(
    private val whitelistDao: WhitelistDao = AppContainer.database.whitelistDao()
) : ViewModel() {

    data class UiState(
        val entries: List<WhitelistEntry> = emptyList(),
        val searchQuery: String = "",
        val contactResults: List<ContactLookup.ContactInfo> = emptyList()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadEntries()
    }

    fun loadEntries() {
        viewModelScope.launch {
            val entries = whitelistDao.getAll()
            _uiState.value = _uiState.value.copy(entries = entries)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applySearch()
    }

    private fun applySearch() {
        viewModelScope.launch {
            val query = _uiState.value.searchQuery
            val entries = if (query.isNotBlank()) {
                whitelistDao.searchEntries(query)
            } else {
                whitelistDao.getAll()
            }
            _uiState.value = _uiState.value.copy(entries = entries)
        }
    }

    fun add(phone: String, note: String) {
        viewModelScope.launch {
            whitelistDao.insert(WhitelistEntry(phoneNumber = phone, note = note))
            applySearch()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            whitelistDao.deleteById(id)
            applySearch()
        }
    }
}
```

- [ ] **Step 2: Redesign WhitelistScreen with search**

Replace `WhitelistScreen`:

```kotlin
package cc.niaoer.lowcall.ui.whitelist

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.niaoer.lowcall.R
import cc.niaoer.lowcall.data.model.WhitelistEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhitelistScreen(
    onNavigateBack: () -> Unit,
    viewModel: WhitelistViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var addDialogPhone by remember { mutableStateOf("") }
    var addDialogNote by remember { mutableStateOf("") }
    var contactsPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        contactsPermissionGranted = granted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.whitelist_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.whitelist_add))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text("搜索联系人或号码") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            if (!contactsPermissionGranted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.enable_contacts_whitelist),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.enable_contacts_whitelist_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.enable_contacts_whitelist_button))
                        }
                    }
                }
            }

            if (uiState.entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_whitelist),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(uiState.entries, key = { it.id }) { entry ->
                        WhitelistItem(
                            entry = entry,
                            onDelete = { viewModel.delete(entry.id) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.whitelist_add)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = addDialogPhone,
                        onValueChange = { addDialogPhone = it },
                        label = { Text(stringResource(R.string.whitelist_number)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addDialogNote,
                        onValueChange = { addDialogNote = it },
                        label = { Text(stringResource(R.string.whitelist_note)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.add(addDialogPhone, addDialogNote)
                    addDialogPhone = ""
                    addDialogNote = ""
                    showAddDialog = false
                }) { Text(stringResource(R.string.whitelist_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.whitelist_cancel))
                }
            }
        )
    }
}

@Composable
private fun WhitelistItem(entry: WhitelistEntry, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.phoneNumber.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.phoneNumber,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.note.isNotBlank()) {
                    Text(
                        text = entry.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "始终放行",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete)
                )
            }
        }
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/ui/whitelist/
git commit -m "feat: redesign WhitelistScreen with search bar and contact styling"
```

---

## Task 9: RuleTestScreen — Result Card Animation

**Files:**
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/test/RuleTestScreen.kt`

- [ ] **Step 1: Redesign with animated result card**

Replace `RuleTestScreen`:

```kotlin
package cc.niaoer.lowcall.ui.test

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.niaoer.lowcall.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleTestScreen(
    onNavigateBack: () -> Unit,
    viewModel: RuleTestViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.test_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = state.phoneNumber,
                onValueChange = viewModel::updatePhoneNumber,
                label = { Text(stringResource(R.string.test_phone)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = viewModel::test,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.phoneNumber.isNotBlank()
            ) {
                Text(stringResource(R.string.test_button))
            }

            AnimatedVisibility(
                visible = state.tested,
                enter = slideInVertically(initialOffsetY = { it / 2 })
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                ResultCard(matchedRule = state.matchedRule)
            }
        }
    }
}

@Composable
private fun ResultCard(matchedRule: cc.niaoer.lowcall.data.model.BlockRule?) {
    val isBlocked = matchedRule != null
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isBlocked)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(
                    if (isBlocked) Icons.Default.Block else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (isBlocked)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isBlocked) {
                        stringResource(R.string.test_matched, matchedRule!!.pattern)
                    } else {
                        stringResource(R.string.test_no_match)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isBlocked)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isBlocked && matchedRule?.description?.isNotBlank() == true) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = matchedRule.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/ui/test/RuleTestScreen.kt
git commit -m "feat: redesign RuleTestScreen with animated result card"
```

---

## Task 10: SettingsScreen — Grouped Settings + About Card

**Files:**
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Redesign with grouped settings**

Replace `SettingsScreen`:

```kotlin
package cc.niaoer.lowcall.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.niaoer.lowcall.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    val notificationEnabled by viewModel.notificationEnabled.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportRules(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importRules(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // About card
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
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "来电拦截",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Section: Protection
            SectionTitle("防护开关")
            SettingItem(
                icon = Icons.Default.NotificationsActive,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                title = stringResource(R.string.notification_setting),
                description = stringResource(R.string.notification_setting_hint),
                trailing = {
                    Switch(
                        checked = notificationEnabled,
                        onCheckedChange = viewModel::setNotificationEnabled
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Section: Rules and Data
            SectionTitle("规则与数据")
            SettingItem(
                icon = Icons.Default.CloudUpload,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                title = stringResource(R.string.export_rules),
                onClick = { exportLauncher.launch("lowcall_rules.json") }
            )
            SettingItem(
                icon = Icons.Default.CloudDownload,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                title = stringResource(R.string.import_rules),
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
            )
            SettingItem(
                icon = Icons.Default.Delete,
                iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                iconContentColor = MaterialTheme.colorScheme.onErrorContainer,
                title = stringResource(R.string.clear_history),
                onClick = { showClearDialog = true }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_history)) },
            text = { Text(stringResource(R.string.clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory(context)
                    showClearDialog = false
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconContainerColor: androidx.compose.ui.graphics.Color,
    iconContentColor: androidx.compose.ui.graphics.Color,
    title: String,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val modifier = if (onClick != null) {
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    } else {
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    }

    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconContentColor
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/ui/settings/SettingsScreen.kt
git commit -m "feat: redesign SettingsScreen with grouped items and about card"
```

---

## Task 11: Final Verification

- [ ] **Step 1: Run unit tests**

Run: `./gradlew :app:test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 2: Run lint**

Run: `./gradlew :app:lintDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Check git diff**

Run: `git diff --check`
Expected: No trailing whitespace errors

- [ ] **Step 4: Final commit**

```bash
git commit -m "feat: complete UI redesign based on OpenDesign spec"
```

---

## Spec Coverage Check

| Spec Requirement | Task |
|------------------|------|
| Bottom navigation (5 tabs) | Task 5 |
| HomeScreen with hero stats | Task 3, 4 |
| HomeScreen quick cards | Task 4 |
| HomeScreen recent blocked | Task 3, 4 |
| RulesScreen tab filtering | Task 6 |
| RulesScreen search | Task 6 |
| RulesScreen elevated cards | Task 6 |
| History date grouping | Task 7 |
| History avatar styling | Task 7 |
| Whitelist search bar | Task 8 |
| Whitelist contact styling | Task 8 |
| Test result animation | Task 9 |
| Settings about card | Task 10 |
| Settings grouped items | Task 10 |
| CallLogDao count queries | Task 1 |
| BlockRuleDao search | Task 1 |
| WhitelistDao search | Task 1 |

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-06-19-lowcall-ui-redesign.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints for review

**Which approach?**
