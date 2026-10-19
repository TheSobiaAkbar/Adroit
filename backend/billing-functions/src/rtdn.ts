import { tokenHash } from './store';
import type { Deps } from './verify';

/** Voided purchases are looked up from a day before the notification, to allow for clock skew and delays. */
const VOIDED_LOOKBACK_MILLIS = 24 * 60 * 60 * 1000;

interface DeveloperNotification {
  packageName?: string;
  eventTimeMillis?: string | number;
  subscriptionNotification?: { notificationType?: number; purchaseToken?: string };
  oneTimeProductNotification?: { notificationType?: number; purchaseToken?: string };
  voidedPurchaseNotification?: { purchaseToken?: string; productType?: number; refundType?: number };
  testNotification?: unknown;
}

/**
 * Handles one real-time developer notification ([data] is the decoded Pub/Sub JSON). Idempotent by [messageId]. The
 * payload only says which purchase changed: its state is always re-read from Play. Purchases nobody verified yet are
 * skipped (`/verify` stores them). Throws on a transient failure so Pub/Sub delivers the message again.
 */
export async function handleNotification(deps: Deps, messageId: string, data: unknown): Promise<void> {
  const { store, play, config } = deps;
  if (await store.isProcessed(messageId)) return;
  const notification = (typeof data === 'object' && data !== null ? data : {}) as DeveloperNotification;

  if (notification.packageName !== config.packageName) {
    deps.log(`RTDN ${messageId}: ignored, package ${notification.packageName}`);
  } else if (notification.testNotification) {
    deps.log(`RTDN ${messageId}: test notification`);
  } else if (notification.subscriptionNotification?.purchaseToken) {
    const token = notification.subscriptionNotification.purchaseToken;
    const hash = tokenHash(token);
    if (await store.get(hash)) {
      const purchase = await play.subscription(config.packageName, token);
      await store.update(hash, {
        productIds: purchase.productIds,
        state: purchase.state,
        expiryMillis: purchase.expiryMillis,
        updatedMillis: deps.now(),
      });
      if (purchase.linkedPurchaseToken) {
        await store.update(tokenHash(purchase.linkedPurchaseToken), { replacedBy: hash, updatedMillis: deps.now() });
      }
    }
  } else if (notification.oneTimeProductNotification?.purchaseToken) {
    const token = notification.oneTimeProductNotification.purchaseToken;
    const hash = tokenHash(token);
    if (await store.get(hash)) {
      const purchase = await play.product(config.packageName, token);
      await store.update(hash, { state: purchase.state, updatedMillis: deps.now() });
    }
  } else if (notification.voidedPurchaseNotification?.purchaseToken) {
    const token = notification.voidedPurchaseNotification.purchaseToken;
    const hash = tokenHash(token);
    if (await store.get(hash)) {
      const since = Number(notification.eventTimeMillis ?? deps.now()) - VOIDED_LOOKBACK_MILLIS;
      if (!(await play.isVoided(config.packageName, token, since))) {
        throw new Error(`RTDN ${messageId}: voided purchase not listed by Play yet`);
      }
      await store.update(hash, { voided: true, updatedMillis: deps.now() });
      deps.log(`RTDN ${messageId}: purchase ${hash.slice(0, 12)} voided`);
    }
  }
  await store.markProcessed(messageId);
}
