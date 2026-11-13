package cc.niaoer.lowcall.ui.home

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
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun protectionSummaryUsesAvailableContentWidth() {
        composeTestRule.setContent {
            LowCallTheme {
                HomeScreen(onNavigateToTest = {}, onNavigateToAddRule = {})
            }
        }

        val cardBounds = composeTestRule.onNodeWithTag("protection_summary")
            .getUnclippedBoundsInRoot()
        val rootBounds = composeTestRule.onRoot().getUnclippedBoundsInRoot()
        val cardWidth = cardBounds.right - cardBounds.left
        val rootWidth = rootBounds.right - rootBounds.left
        assertTrue(cardWidth / rootWidth > 0.9f)
    }
}
