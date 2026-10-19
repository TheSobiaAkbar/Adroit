import { androidpublisher, androidpublisher_v3, auth } from '@googleapis/androidpublisher';

/** A purchase as Google Play reports it, reduced to what the backend needs. */
export interface PlayPurchase {
  productIds: string[];
  /**
   * Subscriptions: the `SubscriptionState` without its prefix (`ACTIVE`, `CANCELED`, `IN_GRACE_PERIOD`, `ON_HOLD`,
   * `PAUSED`, `EXPIRED`, `PENDING`, ...). One-time products: `PURCHASED`, `CANCELLED` or `PENDING`.
   */
  state: string;
  /** Subscriptions: the latest line item expiry (epoch ms); null for one-time products. */
  expiryMillis: number | null;
  purchaseTimeMillis: number;
  acknowledged: boolean;
  /** Subscriptions: the purchase this one replaced (upgrade, downgrade, resubscribe). */
  linkedPurchaseToken: string | null;
}

/** Google Play Developer API, behind an interface so the logic is testable without Google. */
export interface Play {
  subscription(packageName: string, token: string): Promise<PlayPurchase>;
  product(packageName: string, token: string): Promise<PlayPurchase>;
  acknowledgeSubscription(packageName: string, subscriptionId: string, token: string): Promise<void>;
  acknowledgeProduct(packageName: string, productId: string, token: string): Promise<void>;
  /** Whether [token] is in Play's voided purchases since [sinceMillis] (refunds, chargebacks, revocations). */
  isVoided(packageName: string, token: string, sinceMillis: number): Promise<boolean>;
}

/** Play doesn't know the token (invalid, from another app, or too old). */
export class UnknownTokenError extends Error {}

const ACKNOWLEDGED = 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED';
const MAX_VOIDED_PAGES = 5;

export function subscriptionFrom(sub: androidpublisher_v3.Schema$SubscriptionPurchaseV2): PlayPurchase {
  const items = sub.lineItems ?? [];
  const expiries = items.map((item) => Date.parse(item.expiryTime ?? '')).filter((t) => !Number.isNaN(t));
  return {
    productIds: items.map((item) => item.productId ?? '').filter((id) => id !== ''),
    state: (sub.subscriptionState ?? 'SUBSCRIPTION_STATE_UNSPECIFIED').replace(/^SUBSCRIPTION_STATE_/, ''),
    expiryMillis: expiries.length > 0 ? Math.max(...expiries) : null,
    purchaseTimeMillis: Date.parse(sub.startTime ?? '') || 0,
    acknowledged: sub.acknowledgementState === ACKNOWLEDGED,
    linkedPurchaseToken: sub.linkedPurchaseToken ?? null,
  };
}

export function productFrom(purchase: androidpublisher_v3.Schema$ProductPurchaseV2): PlayPurchase {
  return {
    productIds: (purchase.productLineItem ?? []).map((item) => item.productId ?? '').filter((id) => id !== ''),
    state: purchase.purchaseStateContext?.purchaseState ?? 'PURCHASE_STATE_UNSPECIFIED',
    expiryMillis: null,
    purchaseTimeMillis: Date.parse(purchase.purchaseCompletionTime ?? '') || 0,
    acknowledged: purchase.acknowledgementState === ACKNOWLEDGED,
    linkedPurchaseToken: null,
  };
}

/** The real API, with the runtime service account (Application Default Credentials). No key file. */
export class GooglePlay implements Play {
  private readonly api = androidpublisher({
    version: 'v3',
    auth: new auth.GoogleAuth({ scopes: ['https://www.googleapis.com/auth/androidpublisher'] }),
  });

  async subscription(packageName: string, token: string): Promise<PlayPurchase> {
    const response = await known(this.api.purchases.subscriptionsv2.get({ packageName, token }));
    return subscriptionFrom(response.data);
  }

  async product(packageName: string, token: string): Promise<PlayPurchase> {
    const response = await known(this.api.purchases.productsv2.getproductpurchasev2({ packageName, token }));
    return productFrom(response.data);
  }

  async acknowledgeSubscription(packageName: string, subscriptionId: string, token: string): Promise<void> {
    await this.api.purchases.subscriptions.acknowledge({ packageName, subscriptionId, token });
  }

  async acknowledgeProduct(packageName: string, productId: string, token: string): Promise<void> {
    await this.api.purchases.products.acknowledge({ packageName, productId, token });
  }

  async isVoided(packageName: string, token: string, sinceMillis: number): Promise<boolean> {
    let pageToken: string | undefined;
    for (let page = 0; page < MAX_VOIDED_PAGES; page++) {
      const response = await this.api.purchases.voidedpurchases.list({
        packageName,
        startTime: String(sinceMillis),
        type: 1, // Subscriptions too, not only one-time products.
        token: pageToken,
      });
      if ((response.data.voidedPurchases ?? []).some((voided) => voided.purchaseToken === token)) return true;
      pageToken = response.data.tokenPagination?.nextPageToken ?? undefined;
      if (!pageToken) return false;
    }
    return false;
  }
}

/** Maps Play's "no such purchase" answers to [UnknownTokenError]; other errors (quota, outage) pass through. */
async function known<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (e) {
    const status = (e as { status?: number; code?: number }).status ?? (e as { code?: number }).code;
    if (status === 400 || status === 404 || status === 410) throw new UnknownTokenError(`Play answered ${status}`);
    throw e;
  }
}
