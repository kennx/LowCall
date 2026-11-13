package cc.niaoer.lowcall.ui.settings

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.niaoer.lowcall.ui.theme.LowCallTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appSummaryUsesAvailableContentWidth() {
        composeTestRule.setContent {
            LowCallTheme {
                SettingsScreen()
            }
        }

        val card = composeTestRule.onNodeWithTag("settings_app_summary")
            .getUnclippedBoundsInRoot()
        val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
        val cardWidth = card.right - card.left
        val rootWidth = root.right - root.left
        assertTrue(cardWidth / rootWidth > 0.9f)
    }
}
