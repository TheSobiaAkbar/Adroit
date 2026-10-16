package io.github.thesobiaakbar.adroit.billing.internal.attribution

import io.github.thesobiaakbar.adroit.billing.Attribution
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchase
import io.github.thesobiaakbar.adroit.billing.internal.tokenHash

/**
 * Finds the attempt a Play purchase came from (06 §6), in this order:
 * 1. the attempt already matched to this purchase (a later sync of it);
 * 2. the attempt whose ID Play echoed back as the obfuscated profile ID ([AttributionToken.PROFILE_ID]);
 * 3. the newest unmatched attempt of one of the purchase's products;
 * 4. none: [Attribution.unknown]. A placement is never guessed.
 *
 * A match is bound to the purchase's token hash, so no other purchase takes it.
 */
internal class AttributionMatcher(private val store: AttemptStore) {
    fun match(purchase: PlayPurchase): Attribution {
        val hash = tokenHash(purchase.purchaseToken)
        val attempts = store.all()
        attempts.firstOrNull { it.tokenHash == hash }?.let { return it.attribution }
        val profileId = purchase.obfuscatedProfileId
        val attempt = attempts.firstOrNull { it.tokenHash == null && profileId != null && it.id.replace("-", "") == profileId }
            ?: attempts.filter { it.tokenHash == null && it.attribution.productId in purchase.products }.maxByOrNull { it.createdAt }
            ?: return Attribution.unknown(purchase.products.first())
        store.save(attempt.copy(tokenHash = hash))
        return attempt.attribution
    }
}
