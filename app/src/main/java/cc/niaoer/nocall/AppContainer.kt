package cc.niaoer.nocall

import android.content.Context
import cc.niaoer.nocall.data.RuleMatcher
import cc.niaoer.nocall.data.db.AppDatabase
import cc.niaoer.nocall.data.db.BlockRuleDao
import cc.niaoer.nocall.data.db.CallLogDao

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.create(context)
    val blockRuleDao: BlockRuleDao = database.blockRuleDao()
    val callLogDao: CallLogDao = database.callLogDao()
    val ruleMatcher: RuleMatcher = RuleMatcher()
}
