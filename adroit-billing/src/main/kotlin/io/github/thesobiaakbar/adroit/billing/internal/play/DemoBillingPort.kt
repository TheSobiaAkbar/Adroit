package io.github.thesobiaakbar.adroit.billing.internal.play

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.github.thesobiaakbar.adroit.billing.BillingPeriod
import io.github.thesobiaakbar.adroit.billing.OfferInfo
import io.github.thesobiaakbar.adroit.billing.OneTimeOption
import io.github.thesobiaakbar.adroit.billing.PaywallDefinition
import io.github.thesobiaakbar.adroit.billing.Price
import io.github.thesobiaakbar.adroit.billing.PricingPhase
import io.github.thesobiaakbar.adroit.billing.ProductDefinition
import io.github.thesobiaakbar.adroit.billing.ProductInfo
import io.github.thesobiaakbar.adroit.billing.ProductType
import io.github.thesobiaakbar.adroit.billing.Recurrence
import io.github.thesobiaakbar.adroit.billing.SubscriptionPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * The [PlayBillingPort] of demo mode (`DemoBilling`): made-up products, priced in USD and labelled "(DEMO)", and
 * purchases whose result the user picks in a dialog. Never Play. `AdroitBilling` uses it only in debuggable apps.
 *
 * Subscriptions get base plans `weekly`, `monthly` (with a 3-day trial offer tagged `trial`) and `yearly` (with an
 * intro offer tagged `intro`), plus any other base plan the paywalls' defaults name. Purchases are kept in their own
 * preferences file until consumed, so a demo premium survives a restart.
 */
// Plain SharedPreferences.Editor: androidx.core-ktx isn't a dependency of this module.
@SuppressLint("UseKtx")
internal class DemoBillingPort(
    context: Context,
    private val products: List<ProductDefinition>,
    paywalls: List<PaywallDefinition>,
    private val pendingMillis: Long = PENDING_MILLIS,
    private val now: () -> Long = System::currentTimeMillis,
) : PlayBillingPort {
    private val appContext = context.applicationContext
    private val prefs by lazy { appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val updates = Channel<PlayPurchasesUpdate>(Channel.UNLIMITED)
    private val lock = Any()

    private val basePlans: Map<ProductDefinition, List<String>> = products.associateWith { product ->
        (STANDARD_PLANS + paywalls.flatMap { it.defaults.plans }.filter { it.product == product }.mapNotNull { it.basePlanId })
            .distinct()
    }

    override val purchasesUpdates: Flow<PlayPurchasesUpdate> = updates.receiveAsFlow()

    override suspend fun connect(onDisconnected: () -> Unit): PlayResult<Unit> = PlayResult.Ok(Unit)

    override suspend fun queryProducts(type: PlayProductType, products: List<ProductDefinition>): PlayResult<PlayProducts> =
        PlayResult.Ok(PlayProducts(products.filter { it.defaults.type.playType == type }.map(::product), emptyMap()))

    override suspend fun queryPurchases(type: PlayProductType, includeSuspended: Boolean): PlayResult<List<PlayPurchase>> =
        PlayResult.Ok(withContext(Dispatchers.IO) { load() }.filter { typeOf(it.products.single()) == type })

    override suspend fun launch(activity: Activity, params: LaunchParams): PlayResult<Unit> = withContext(Dispatchers.Main) {
        var chosen = false
        val dialog = AlertDialog.Builder(activity)
            .setTitle("DEMO purchase: ${params.productId}")
            .setItems(arrayOf("Buy (success)", "Pending payment", "Cancel")) { _, which ->
                chosen = true
                choose(params, which)
            }
            .setOnDismissListener { if (!chosen) updates.trySend(PlayPurchasesUpdate.Cancelled) }
            .create()
        // Like a rotation in Play's sheet ending the purchase: the dialog can't outlive its Activity.
        (activity as? LifecycleOwner)?.lifecycle?.addObserver(
            LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) dialog.dismiss() },
        )
        dialog.show()
        PlayResult.Ok(Unit)
    }

    /** The dialog's choice [which]: 0 bought, 1 pending (bought [pendingMillis] later), anything else cancelled. */
    fun choose(params: LaunchParams, which: Int) {
        if (which !in 0..1) {
            updates.trySend(PlayPurchasesUpdate.Cancelled)
            return
        }
        val purchase = PlayPurchase(
            listOf(params.productId), "demo-token-${UUID.randomUUID()}", "DEMO.${UUID.randomUUID()}", now(),
            if (which == 0) PlayPurchaseState.PURCHASED else PlayPurchaseState.PENDING, 1, isAcknowledged = false,
            isAutoRenewing = typeOf(params.productId) == PlayProductType.SUBS, isSuspended = false,
            params.obfuscatedAccountId, params.obfuscatedProfileId, originalJson = "{}", signature = "",
        )
        save(purchase)
        updates.trySend(PlayPurchasesUpdate.Purchased(listOf(purchase)))
        if (which == 1) {
            scope.launch {
                delay(pendingMillis)
                val paid = update(purchase.purchaseToken) { it.copy(state = PlayPurchaseState.PURCHASED) } ?: return@launch
                updates.trySend(PlayPurchasesUpdate.Purchased(listOf(paid)))
            }
        }
    }

    override suspend fun acknowledge(purchaseToken: String): PlayResult<Unit> {
        update(purchaseToken) { it.copy(isAcknowledged = true) }
        return PlayResult.Ok(Unit)
    }

    override suspend fun consume(purchaseToken: String): PlayResult<Unit> {
        synchronized(lock) { write(load().filter { it.purchaseToken != purchaseToken }) }
        return PlayResult.Ok(Unit)
    }

    override fun isFeatureSupported(feature: PlayFeature): PlayResult<Unit> = PlayResult.Ok(Unit)

    override suspend fun showInAppMessages(activity: Activity): PlayResult<String?> = PlayResult.Ok(null)

    override fun endConnection() {}

    private fun typeOf(productId: String): PlayProductType =
        products.firstOrNull { it.defaults.productId == productId }?.defaults?.type?.playType ?: PlayProductType.INAPP

    private fun product(definition: ProductDefinition): PlayProduct {
        val id = definition.defaults.productId
        val type = definition.defaults.type
        val tokens = mutableMapOf<OfferKey, String>()
        val options = if (type == ProductType.SUBSCRIPTION) emptyList() else {
            tokens[OfferKey(null, null)] = "demo:$id"
            listOf(OneTimeOption(null, null, emptyList(), price(if (type == ProductType.CONSUMABLE) 990_000 else 49_990_000)))
        }
        val plans = if (type != ProductType.SUBSCRIPTION) emptyList() else basePlans.getValue(definition).map { planId ->
            val period = when {
                "week" in planId -> "P1W"
                "year" in planId || "annual" in planId -> "P1Y"
                else -> "P1M"
            }
            val base = phase(period, PLAN_MICROS.getValue(period), 0, Recurrence.INFINITE)
            val offers = buildList {
                if (period == "P1M") add(OfferInfo(planId, "demo-trial", listOf("trial"), listOf(phase("P3D", 0, 1, Recurrence.FINITE), base)))
                if (period == "P1Y") add(OfferInfo(planId, "demo-intro", listOf("intro"), listOf(phase("P1Y", 14_990_000, 1, Recurrence.FINITE), base)))
                add(OfferInfo(planId, null, emptyList(), listOf(base)))
            }
            offers.forEach { tokens[OfferKey(planId, it.offerId)] = "demo:$id:$planId:${it.offerId}" }
            SubscriptionPlan(planId, offers)
        }
        return PlayProduct(ProductInfo(definition, id, type, "$id (DEMO)", "$id (DEMO)", "A demo product: no money moves.", options, plans), tokens)
    }

    private fun phase(period: String, micros: Long, cycles: Int, recurrence: Recurrence) =
        PricingPhase(price(micros), period, BillingPeriod.parse(period), cycles, recurrence)

    private fun price(micros: Long): Price =
        Price(if (micros == 0L) "Free (DEMO)" else String.format(Locale.US, "$%d.%02d (DEMO)", micros / 1_000_000, micros / 10_000 % 100), micros, "USD")

    private fun update(token: String, change: (PlayPurchase) -> PlayPurchase): PlayPurchase? = synchronized(lock) {
        val all = load()
        val found = all.firstOrNull { it.purchaseToken == token } ?: return null
        change(found).also { changed -> write(all.map { if (it === found) changed else it }) }
    }

    private fun save(purchase: PlayPurchase) = synchronized(lock) { write(load() + purchase) }

    private fun load(): List<PlayPurchase> {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            PlayPurchase(
                listOf(o.getString("product")), o.getString("token"), o.getString("order"), o.getLong("time"),
                PlayPurchaseState.valueOf(o.getString("state")), 1, o.getBoolean("acknowledged"),
                o.getBoolean("autoRenewing"), isSuspended = false, o.optString("account").ifEmpty { null },
                o.optString("profile").ifEmpty { null }, originalJson = "{}", signature = "",
            )
        }
    }

    private fun write(purchases: List<PlayPurchase>) {
        val array = JSONArray()
        purchases.forEach {
            array.put(
                JSONObject().put("product", it.products.single()).put("token", it.purchaseToken).put("order", it.orderId)
                    .put("time", it.purchaseTimeMillis).put("state", it.state.name).put("acknowledged", it.isAcknowledged)
                    .put("autoRenewing", it.isAutoRenewing).put("account", it.obfuscatedAccountId.orEmpty())
                    .put("profile", it.obfuscatedProfileId.orEmpty()),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val PREFS = "adroit_billing_demo"
        const val KEY = "purchases"
        const val PENDING_MILLIS = 15_000L
        val STANDARD_PLANS = listOf("weekly", "monthly", "yearly")
        val PLAN_MICROS = mapOf("P1W" to 1_990_000L, "P1M" to 4_990_000L, "P1Y" to 29_990_000L)
    }
}
