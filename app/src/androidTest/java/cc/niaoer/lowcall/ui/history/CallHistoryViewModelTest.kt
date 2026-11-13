package cc.niaoer.lowcall.ui.history

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.niaoer.lowcall.AppContainer
import cc.niaoer.lowcall.LowCallApplication
import cc.niaoer.lowcall.data.db.AppDatabase
import cc.niaoer.lowcall.data.model.BlockRule
import cc.niaoer.lowcall.data.model.CallAction
import cc.niaoer.lowcall.data.model.CallLog
import cc.niaoer.lowcall.data.model.RuleType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CallHistoryViewModelTest {

    private lateinit var app: LowCallApplication
    private lateinit var db: AppDatabase
    private lateinit var originalContainer: AppContainer
    private lateinit var viewModel: CallHistoryViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<LowCallApplication>()
        originalContainer = app.appContainer
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        app.appContainer = AppContainer(app, db)
        viewModel = CallHistoryViewModel(app)
    }

    @After
    fun tearDown() {
        db.close()
        app.appContainer = originalContainer
    }

    @Test
    fun addToBlacklistInsertsEnabledExactRuleWithOriginalNumber() = runBlocking {
        val collectJob = launch { viewModel.uiState.collect {} }
        viewModel.addToBlacklist("+86 21 6961 9151")
        awaitFeedback()

        val rule = db.blockRuleDao().findExact("+86 21 6961 9151")
        assertEquals(RuleType.EXACT, rule?.ruleType)
        assertTrue(rule?.enabled == true)
        assertEquals(CallHistoryFeedback.BLACKLIST_ADDED, viewModel.uiState.value.feedback)
        collectJob.cancel()
    }

    @Test
    fun addToBlacklistIgnoresBlankNumber() = runBlocking {
        viewModel.addToBlacklist("  ")
        delay(200)
        assertEquals(0, db.blockRuleDao().getAll().first().size)
        assertNull(viewModel.uiState.value.feedback)
    }

    @Test
    fun addToBlacklistDoesNotDuplicateEnabledRule() = runBlocking {
        val collectJob = launch { viewModel.uiState.collect {} }
        db.blockRuleDao().insert(BlockRule(pattern = "13800138000", ruleType = RuleType.EXACT))
        viewModel.addToBlacklist("13800138000")
        awaitFeedback()
        viewModel.consumeFeedback()
        viewModel.addToBlacklist("13800138000")
        awaitFeedback()

        assertEquals(1, db.blockRuleDao().getAll().first().size)
        assertEquals(CallHistoryFeedback.BLACKLIST_EXISTS, viewModel.uiState.value.feedback)
        collectJob.cancel()
    }

    @Test
    fun addToBlacklistReactivatesDisabledRuleWithoutTouchingOthers() = runBlocking {
        val collectJob = launch { viewModel.uiState.collect {} }
        val disabledId = db.blockRuleDao().insert(
            BlockRule(pattern = "13800138000", ruleType = RuleType.EXACT, enabled = false)
        )
        val otherId = db.blockRuleDao().insert(
            BlockRule(pattern = "13900139000", ruleType = RuleType.EXACT, enabled = false)
        )

        viewModel.addToBlacklist("13800138000")
        awaitFeedback()

        assertTrue(db.blockRuleDao().getById(disabledId)?.enabled == true)
        assertFalse(db.blockRuleDao().getById(otherId)?.enabled == true)
        assertEquals(2, db.blockRuleDao().getAll().first().size)
        assertEquals(CallHistoryFeedback.BLACKLIST_REACTIVATED, viewModel.uiState.value.feedback)
        collectJob.cancel()
    }

    @Test
    fun oldLandlineLogGetsDisplayAttributionWithoutDatabaseRewrite() = runBlocking {
        val id = db.callLogDao().insert(
            CallLog(phoneNumber = "02169619151", action = CallAction.ALLOWED)
        )
        val collectJob = launch { viewModel.uiState.collect {} }
        withTimeout(3000) {
            while (viewModel.uiState.value.logs.isEmpty()) delay(20)
        }

        assertEquals("上海上海", viewModel.uiState.value.logs.first().location)
        assertNull(db.callLogDao().getAllOrdered().first().first().location)
        assertEquals(id, db.callLogDao().getAllOrdered().first().first().id)
        collectJob.cancel()
    }

    private suspend fun awaitFeedback() {
        withTimeout(3000) {
            while (viewModel.uiState.value.feedback == null) delay(20)
        }
    }
}
