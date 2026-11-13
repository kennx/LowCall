package cc.niaoer.lowcall.ui.history

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.niaoer.lowcall.AppContainer
import cc.niaoer.lowcall.LowCallApplication
import cc.niaoer.lowcall.R
import cc.niaoer.lowcall.data.db.AppDatabase
import cc.niaoer.lowcall.data.model.BlockRule
import cc.niaoer.lowcall.data.model.CallAction
import cc.niaoer.lowcall.data.model.CallLog
import cc.niaoer.lowcall.data.model.RuleType
import cc.niaoer.lowcall.ui.theme.LowCallTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CallHistoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: LowCallApplication
    private lateinit var db: AppDatabase
    private lateinit var originalContainer: AppContainer

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<LowCallApplication>()
        originalContainer = app.appContainer
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        app.appContainer = AppContainer(app, db)
    }

    @After
    fun tearDown() {
        db.close()
        app.appContainer = originalContainer
    }

    @Test
    fun historyDisplaysFormattedNumbersAndDistinctAllowReasons() {
        runBlocking {
            listOf(
                "whitelist" to "13800138001",
                "contacts" to "13800138002",
                "no_match" to "13800138003"
            ).forEachIndexed { index, (reason, number) ->
                db.callLogDao().insert(
                    CallLog(
                        phoneNumber = number,
                        action = CallAction.ALLOWED,
                        allowReason = reason,
                        timestamp = System.currentTimeMillis() - index * 1000
                    )
                )
            }
            db.callLogDao().insert(
                CallLog(
                    phoneNumber = "13800138004",
                    action = CallAction.ALLOWED,
                    allowReason = null,
                    timestamp = System.currentTimeMillis() - 3000
                )
            )
        }
        setHistoryContent()

        composeTestRule.onNodeWithText(app.getString(R.string.allowed_whitelist)).assertExists()
        composeTestRule.onNodeWithText(app.getString(R.string.allowed_contacts)).assertExists()
        composeTestRule.onNodeWithText(app.getString(R.string.allowed_no_match)).assertExists()
        composeTestRule.onAllNodesWithText(app.getString(R.string.allowed)).assertCountEquals(1)
        composeTestRule.onNodeWithText("138 0013 8001").assertIsDisplayed()
    }

    @Test
    fun allowedCallBlacklistSupportsCancelConfirmAndFeedback() {
        runBlocking {
            db.callLogDao().insert(
                CallLog(phoneNumber = "13800138001", action = CallAction.ALLOWED)
            )
        }
        setHistoryContent()
        val action = app.getString(R.string.add_to_blacklist)

        composeTestRule.onNodeWithText(action).performClick()
        composeTestRule.onAllNodesWithText(app.getString(R.string.blacklist_confirm_title))
            .assertCountEquals(2)
        composeTestRule.onNodeWithText(app.getString(R.string.cancel)).performClick()
        assertNull(runBlocking { db.blockRuleDao().findExact("13800138001") })

        composeTestRule.onNodeWithText(action).performClick()
        composeTestRule.onNodeWithText(app.getString(R.string.confirm)).performClick()
        composeTestRule.waitUntil(3000) {
            composeTestRule.onAllNodesWithText(app.getString(R.string.blacklist_added))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(runBlocking { db.blockRuleDao().findExact("13800138001") != null })
    }

    @Test
    fun existingBlacklistShowsExistingFeedbackWithoutDuplicate() {
        runBlocking {
            db.blockRuleDao().insert(
                BlockRule(pattern = "13800138001", ruleType = RuleType.EXACT)
            )
            db.callLogDao().insert(
                CallLog(phoneNumber = "13800138001", action = CallAction.ALLOWED)
            )
        }
        setHistoryContent()

        composeTestRule.onNodeWithText(app.getString(R.string.add_to_blacklist)).performClick()
        composeTestRule.onNodeWithText(app.getString(R.string.confirm)).performClick()
        composeTestRule.waitUntil(3000) {
            composeTestRule.onAllNodesWithText(app.getString(R.string.blacklist_exists))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(runBlocking { db.blockRuleDao().getAll().first().size == 1 })
    }

    @Test
    fun blockedLogKeepsWhitelistAction() {
        runBlocking {
            db.callLogDao().insert(
                CallLog(phoneNumber = "02169619151", action = CallAction.BLOCKED)
            )
        }
        setHistoryContent()

        composeTestRule.onNodeWithText("021 6961 9151").assertIsDisplayed()
        composeTestRule.onNodeWithText(app.getString(R.string.add_to_whitelist)).assertIsDisplayed()
    }

    @Test
    fun largeFontKeepsBlacklistActionReachable() {
        runBlocking {
            db.callLogDao().insert(
                CallLog(
                    phoneNumber = "+8613800138001",
                    action = CallAction.ALLOWED,
                    allowReason = "contacts",
                    location = "广东省深圳市",
                    carrier = "中国移动"
                )
            )
        }
        setHistoryContent(fontScale = 2f)

        composeTestRule.onNodeWithText(app.getString(R.string.add_to_blacklist)).assertIsDisplayed()
    }

    private fun setHistoryContent(fontScale: Float = 1f) {
        val viewModel = CallHistoryViewModel(app)
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale)
            ) {
                LowCallTheme {
                    CallHistoryScreen(viewModel = viewModel)
                }
            }
        }
    }
}
