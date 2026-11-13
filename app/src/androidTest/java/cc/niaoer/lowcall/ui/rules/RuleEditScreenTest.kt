package cc.niaoer.lowcall.ui.rules

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.niaoer.lowcall.AppContainer
import cc.niaoer.lowcall.LowCallApplication
import cc.niaoer.lowcall.R
import cc.niaoer.lowcall.data.db.AppDatabase
import cc.niaoer.lowcall.data.model.BlockRule
import cc.niaoer.lowcall.data.model.RuleType
import cc.niaoer.lowcall.ui.test.RuleTestScreen
import cc.niaoer.lowcall.ui.theme.LowCallTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuleEditScreenTest {

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
    fun rulesShowDescriptionPatternAndEnabledControl() {
        runBlocking {
            db.blockRuleDao().insert(
                BlockRule(
                    pattern = "138*",
                    ruleType = RuleType.WILDCARD,
                    description = "营销电话",
                    enabled = true
                )
            )
        }
        composeTestRule.setContent {
            LowCallTheme {
                RulesScreen({}, {}, {}, {}, {}, {})
            }
        }

        composeTestRule.onNodeWithText("营销电话").assertIsDisplayed()
        composeTestRule.onNodeWithText("138*").assertIsDisplayed()
        composeTestRule.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun ruleTypeUsesSelectableSegmentsAndSaveRemainsReachable() {
        composeTestRule.setContent {
            LowCallTheme {
                RuleEditScreen(ruleId = null, onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText("正则").assertIsSelected()
        composeTestRule.onNodeWithText("通配").performClick().assertIsSelected()
        composeTestRule.onNodeWithText(app.getString(R.string.pattern))
            .performTextInput("^(400|800)\\d{7}$|^(95|96)\\d{3,6}$")
        composeTestRule.onNodeWithText(app.getString(R.string.save))
            .assertIsDisplayed()
            .assertExists()
    }

    @Test
    fun ruleTestShowsBlockedAndAllowedResults() {
        runBlocking {
            db.blockRuleDao().insert(
                BlockRule(pattern = "13800138000", ruleType = RuleType.EXACT)
            )
        }
        composeTestRule.setContent {
            LowCallTheme {
                RuleTestScreen(onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText(app.getString(R.string.test_phone))
            .performTextInput("13800138000")
        composeTestRule.onNodeWithText(app.getString(R.string.test_button)).performClick()
        composeTestRule.onNodeWithText(
            app.getString(R.string.test_matched, "13800138000")
        ).assertIsDisplayed()

        composeTestRule.onNodeWithText(app.getString(R.string.test_phone))
            .performTextInput("13900139000")
        composeTestRule.onNodeWithText(app.getString(R.string.test_button)).performClick()
        composeTestRule.onNodeWithText(app.getString(R.string.test_no_match)).assertIsDisplayed()
    }
}
