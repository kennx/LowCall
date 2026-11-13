package cc.niaoer.lowcall.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cc.niaoer.lowcall.R
import cc.niaoer.lowcall.data.formatPhoneForDisplay

@Composable
fun PhoneNumberText(
    phoneNumber: String,
    modifier: Modifier = Modifier
) {
    val display = formatPhoneForDisplay(phoneNumber)
    Text(
        text = display.extension?.let { extension ->
            stringResource(R.string.phone_with_extension, display.number, extension)
        } ?: display.number,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}
