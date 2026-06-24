package cc.niaoer.lowcall

import android.content.Context
import cc.niaoer.lowcall.data.PhoneAttribution
import cc.niaoer.lowcall.data.db.AppDatabase
import cc.niaoer.lowcall.data.db.BlockRuleDao
import cc.niaoer.lowcall.data.db.CallLogDao
import cc.niaoer.lowcall.data.db.WhitelistDao
import cc.niaoer.lowcall.data.prefs.SettingsRepository

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.create(context)
    val blockRuleDao: BlockRuleDao = database.blockRuleDao()
    val callLogDao: CallLogDao = database.callLogDao()
    val whitelistDao: WhitelistDao = database.whitelistDao()
    val settingsRepository: SettingsRepository = SettingsRepository(context.applicationContext)
    val phoneAttribution: PhoneAttribution by lazy {
        val data = context.assets.open("phone.dat").use { it.readBytes() }
        PhoneAttribution(data)
    }
}
