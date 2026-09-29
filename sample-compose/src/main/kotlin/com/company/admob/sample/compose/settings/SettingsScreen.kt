package com.company.admob.sample.compose.settings

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.company.admob.CompanyAdMob
import com.company.admob.sample.compose.PremiumStore
import com.company.admob.sample.compose.R

/**
 * - "Privacy options": shown only while UMP requires a privacy options entry point (`isPrivacyOptionsRequired`),
 *   which Google requires apps to offer in that case.
 * - "Ad-free": `setAdFree`, as an app would after a purchase, saved in [PremiumStore] so the next launch's splash
 *   treats the user as premium (no consent form, no ad, a 3-second splash).
 * - "Ad Inspector": Google's debugging tool; opens on test devices only.
 * - "Native styles": every native template, with a live ad.
 */
@Composable
fun SettingsScreen(onOpenNativeStyles: () -> Unit) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val privacyOptionsRequired by CompanyAdMob.privacy.isPrivacyOptionsRequired.collectAsStateWithLifecycle()
    var adFree by remember { mutableStateOf(PremiumStore.isPremium(context)) }
    val toast = { message: String -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (privacyOptionsRequired && activity != null) {
            val error = stringResource(R.string.privacy_options_error)
            Button(
                onClick = { CompanyAdMob.privacy.showPrivacyOptions(activity) { e -> e?.let { toast(error.format(it.message)) } } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.privacy_options)) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ad_free), Modifier.weight(1f))
            Switch(
                checked = adFree,
                onCheckedChange = { checked ->
                    adFree = checked
                    CompanyAdMob.setAdFree(checked)
                    PremiumStore.setPremium(context, checked)
                },
            )
        }
        if (activity != null) {
            val error = stringResource(R.string.ad_inspector_error)
            OutlinedButton(
                onClick = { CompanyAdMob.diagnostics.openAdInspector(activity) { e -> e?.let { toast(error.format(it.message)) } } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.ad_inspector)) }
        }
        OutlinedButton(onClick = onOpenNativeStyles, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.native_styles))
        }
    }
}
