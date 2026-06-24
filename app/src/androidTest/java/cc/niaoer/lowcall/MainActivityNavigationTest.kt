package cc.niaoer.lowcall

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import cc.niaoer.lowcall.ui.theme.LowCallTheme
import org.junit.Rule
import org.junit.Test

class MainActivityNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun homeCardRules_thenHistoryBottomNav_thenHomeBottomNav_returnsToHome() {
        composeTestRule.setContent {
            LowCallTheme {
                LowCallApp(screeningEnabled = true, onSetup = {})
            }
        }

        // Start on Home: assert greeting is shown
        composeTestRule.onNodeWithText(targetContext.getString(R.string.home_greeting)).assertExists()

        // Click the 拦截规则 card on Home (use its hint text to avoid the bottom nav item)
        composeTestRule.onNodeWithText(targetContext.getString(R.string.quick_rules_hint)).performClick()
        composeTestRule.waitForIdle()

        // Assert we are on Rules screen (FAB for adding rules)
        composeTestRule.onNodeWithContentDescription(targetContext.getString(R.string.add_rule)).assertExists()

        // Click 通话记录 in bottom nav
        composeTestRule.onNode(
            hasText(targetContext.getString(R.string.history_title)) and hasClickAction()
        ).performClick()
        composeTestRule.waitForIdle()

        // Assert we are on History screen (top bar title, not bottom nav)
        composeTestRule.onNode(
            hasText(targetContext.getString(R.string.history_title)) and hasNoClickAction()
        ).assertExists()

        // Click 首页 in bottom nav
        composeTestRule.onNode(
            hasText(targetContext.getString(R.string.home_title)) and hasClickAction()
        ).performClick()
        composeTestRule.waitForIdle()

        // Assert we returned to Home screen
        composeTestRule.onNodeWithText(targetContext.getString(R.string.home_greeting)).assertExists()
    }
}
