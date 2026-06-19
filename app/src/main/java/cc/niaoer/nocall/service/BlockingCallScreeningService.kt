package cc.niaoer.nocall.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.annotation.RequiresApi
import cc.niaoer.nocall.NoCallApplication
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

        // CallScreeningService callbacks run on a binder thread and must complete
        // before the system can respond to the call. runBlocking is used here
        // because the screening decision must be synchronous; the work inside
        // is bounded by a timeout on the contacts lookup to avoid ANR.
        runBlocking {
            val normalized = cc.niaoer.nocall.data.normalizePhone(phoneNumber)

            // Whitelist has absolute priority: table entry or live contact match.
            // Contact lookup is wrapped with a timeout to avoid blocking the
            // binder thread if the ContactsProvider is slow or unresponsive.
            val inWhitelist = container.whitelistDao.exists(normalized) ||
                withTimeoutOrNull(CONTACT_LOOKUP_TIMEOUT_MS) {
                    container.contactLookup.isInContacts(phoneNumber)
                } ?: false

            if (inWhitelist) {
                container.callLogDao.insert(
                    CallLog(
                        phoneNumber = phoneNumber,
                        action = CallAction.ALLOWED,
                        timestamp = System.currentTimeMillis()
                    )
                )
                respondToCall(details, CallResponse.Builder().build())
                return@runBlocking
            }

            val enabledRules = container.blockRuleDao.getEnabledList()
            val matched = container.ruleMatcher.match(phoneNumber, enabledRules)

            if (matched != null) {
                container.callLogDao.insert(
                    CallLog(
                        phoneNumber = phoneNumber,
                        matchedRuleId = matched.id,
                        matchedRulePattern = matched.pattern,
                        action = CallAction.BLOCKED,
                        timestamp = System.currentTimeMillis()
                    )
                )
                val notificationEnabled = container.settingsRepository
                    .notificationEnabled.first()
                if (notificationEnabled) {
                    val ruleDesc = matched.description.ifBlank { matched.pattern }
                    NotificationHelper.showBlockedCallNotification(
                        this@BlockingCallScreeningService,
                        phoneNumber,
                        ruleDesc
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
                        action = CallAction.ALLOWED,
                        timestamp = System.currentTimeMillis()
                    )
                )
                respondToCall(details, CallResponse.Builder().build())
            }
        }
    }
}
