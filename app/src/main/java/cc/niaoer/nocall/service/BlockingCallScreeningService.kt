package cc.niaoer.nocall.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.annotation.RequiresApi
import cc.niaoer.nocall.NoCallApplication
import cc.niaoer.nocall.data.isInContacts
import cc.niaoer.nocall.data.match
import cc.niaoer.nocall.data.model.CallAction
import cc.niaoer.nocall.data.model.CallLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

@RequiresApi(Build.VERSION_CODES.N)
class BlockingCallScreeningService : CallScreeningService() {

    companion object {
        private const val TAG = "BlockingCallScreening"
        private const val CONTACT_LOOKUP_TIMEOUT_MS = 500L
    }

    override fun onScreenCall(details: Call.Details) {
        val phoneNumber = details.handle?.schemeSpecificPart
        if (phoneNumber.isNullOrBlank()) {
            return
        }

        val container = (application as NoCallApplication).appContainer

        runBlocking {
            val attribution = container.phoneAttribution.lookup(phoneNumber)
            val location = attribution?.let { "${it.province}${it.city}" }
            val carrier = attribution?.carrier

            val normalized = cc.niaoer.nocall.data.normalizePhone(phoneNumber)

            val inWhitelist = container.whitelistDao.exists(normalized) ||
                withTimeoutOrNull(CONTACT_LOOKUP_TIMEOUT_MS) {
                    isInContacts(this@BlockingCallScreeningService, phoneNumber)
                } ?: false

            if (inWhitelist) {
                container.callLogDao.insert(
                    CallLog(
                        phoneNumber = phoneNumber,
                        location = location,
                        carrier = carrier,
                        action = CallAction.ALLOWED,
                        timestamp = System.currentTimeMillis()
                    )
                )
                respondToCall(details, CallResponse.Builder().build())
                return@runBlocking
            }

            val enabledRules = container.blockRuleDao.getEnabledList()
            val matched = match(phoneNumber, enabledRules)

            if (matched != null) {
                container.callLogDao.insert(
                    CallLog(
                        phoneNumber = phoneNumber,
                        matchedRuleId = matched.id,
                        matchedRulePattern = matched.pattern,
                        location = location,
                        carrier = carrier,
                        action = CallAction.BLOCKED,
                        timestamp = System.currentTimeMillis()
                    )
                )
                val notificationEnabled = container.settingsRepository
                    .notificationEnabled.first()
                if (notificationEnabled) {
                    val ruleDesc = matched.description.ifBlank { matched.pattern }
                    NotificationHelper.showBlockedCallNotification(
                        context = this@BlockingCallScreeningService,
                        phoneNumber = phoneNumber,
                        location = location,
                        carrier = carrier,
                        ruleDescription = ruleDesc
                    )
                }
                val response = CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()
                respondToCall(details, response)
            } else {
                container.callLogDao.insert(
                    CallLog(
                        phoneNumber = phoneNumber,
                        location = location,
                        carrier = carrier,
                        action = CallAction.ALLOWED,
                        timestamp = System.currentTimeMillis()
                    )
                )
                respondToCall(details, CallResponse.Builder().build())
            }
        }
    }
}
