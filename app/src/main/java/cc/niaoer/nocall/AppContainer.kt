package cc.niaoer.nocall

import android.content.Context
import cc.niaoer.nocall.data.ContactLookup
import cc.niaoer.nocall.data.RuleMatcher
import cc.niaoer.nocall.data.db.AppDatabase
import cc.niaoer.nocall.data.db.BlockRuleDao
import cc.niaoer.nocall.data.db.CallLogDao
import cc.niaoer.nocall.data.db.WhitelistDao
import cc.niaoer.nocall.data.prefs.SettingsRepository

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.create(context)
    val blockRuleDao: BlockRuleDao = database.blockRuleDao()
    val callLogDao: CallLogDao = database.callLogDao()
    val whitelistDao: WhitelistDao = database.whitelistDao()
    val ruleMatcher: RuleMatcher = RuleMatcher()
    val settingsRepository: SettingsRepository = SettingsRepository(context.applicationContext)
    val contactLookup: ContactLookup = ContactLookup(context.applicationContext)
}
