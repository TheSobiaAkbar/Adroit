package io.github.thesobiaakbar.adroit.sample.compose.settings

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONException
import org.json.JSONObject
import androidx.compose.material3.MaterialTheme
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.billing.RestoreResult
import io.github.thesobiaakbar.adroit.billing.SnapshotStatus
import io.github.thesobiaakbar.adroit.firebase.RemoteConfigSession
import io.github.thesobiaakbar.adroit.sample.compose.BuildConfig
import io.github.thesobiaakbar.adroit.sample.compose.R
import io.github.thesobiaakbar.adroit.sample.compose.billing.DemoMode
import io.github.thesobiaakbar.adroit.sample.compose.billing.PremiumViewModel

/**
 * - "Privacy options": shown only while UMP requires a privacy options entry point (`isPrivacyOptionsRequired`),
 *   which Google requires apps to offer in that case.
 * - Premium: the entitlement and how sure it is (Fresh from Play, Cached while offline, Unknown), "Upgrade" (the
 *   `SettingsUpgrade` paywall), "Restore purchases" and "Manage subscription", through [PremiumViewModel]. Premium
 *   comes only from `AdroitBilling.entitlements`; `SampleApplication` turns it into `Adroit.setAdFree`.
 * - "Demo billing" (debug builds): made-up products instead of Play, from the next launch (`DemoMode`).
 * - Debug builds: the active Firebase `AdSettings.splashAdMode` and `AppUpdate.type` (docs/SPLASH.md, APP_UPDATE.md).
 * - "Ad Inspector": Google's debugging tool; opens on test devices only.
 * - "Native styles": every native template, with a live ad.
 */
@Composable
fun SettingsScreen(onOpenNativeStyles: () -> Unit, onUpgrade: () -> Unit, premium: PremiumViewModel = viewModel()) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val privacyOptionsRequired by Adroit.privacy.isPrivacyOptionsRequired.collectAsStateWithLifecycle()
    val entitlements by premium.entitlements.collectAsStateWithLifecycle()
    var demo by remember { mutableStateOf(DemoMode.isOn(context)) }
    val toast = { message: String -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(premium, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            premium.restoreResults.collect { result ->
                toast(
                    when (result) {
                        is RestoreResult.Restored -> if (result.newlyActive.isEmpty()) resources.getString(R.string.restore_up_to_date)
                        else resources.getString(R.string.restore_done, result.newlyActive.joinToString())
                        RestoreResult.NothingToRestore -> resources.getString(R.string.restore_nothing)
                        is RestoreResult.Failed ->
                            resources.getString(R.string.restore_failed, result.error.debugMessage ?: result.error.toString())
                    },
                )
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (privacyOptionsRequired && activity != null) {
            val error = stringResource(R.string.privacy_options_error)
            Button(
                onClick = { Adroit.privacy.showPrivacyOptions(activity) { e -> e?.let { toast(error.format(it.message)) } } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.privacy_options)) }
        }
        Text(
            stringResource(
                R.string.premium_status,
                stringResource(if (entitlements.has("premium")) R.string.premium_yes else R.string.premium_no),
                when (entitlements.status) {
                    is SnapshotStatus.Fresh -> "Fresh"
                    is SnapshotStatus.Cached -> "Cached"
                    SnapshotStatus.Unknown -> "Unknown"
                },
            ),
        )
        Button(onClick = onUpgrade, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.upgrade)) }
        OutlinedButton(onClick = premium::restore, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.restore_purchases))
        }
        if (activity != null) {
            OutlinedButton(onClick = { premium.manage(activity) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.manage_subscription))
            }
        }
        if (BuildConfig.DEBUG) {
            val restart = stringResource(R.string.demo_billing_restart)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.demo_billing), Modifier.weight(1f))
                Switch(
                    checked = demo,
                    onCheckedChange = { checked ->
                        demo = checked
                        DemoMode.set(context, checked)
                        toast(restart)
                    },
                )
            }
            Text(
                stringResource(
                    R.string.firebase_modes,
                    activeValue("AdSettings", "splashAdMode", "show"),
                    activeValue("AppUpdate", "type", "flexible"),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (activity != null) {
            val error = stringResource(R.string.ad_inspector_error)
            OutlinedButton(
                onClick = { Adroit.diagnostics.openAdInspector(activity) { e -> e?.let { toast(error.format(it.message)) } } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.ad_inspector)) }
        }
        OutlinedButton(onClick = onOpenNativeStyles, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.native_styles))
        }
    }
}

/** [field] of the active Firebase JSON parameter [key], or [default] when it isn't set. */
private fun activeValue(key: String, field: String, default: String): String = try {
    RemoteConfigSession.startDefault()?.string(key)?.let { JSONObject(it).optString(field) }?.ifBlank { null } ?: default
} catch (e: JSONException) {
    "invalid"
}
