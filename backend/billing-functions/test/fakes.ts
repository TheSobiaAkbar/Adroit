import type { Config } from '../src/config';
import { type Play, type PlayPurchase, productFrom, subscriptionFrom, UnknownTokenError } from '../src/play';
import type { BindResult, PurchaseRecord, Store } from '../src/store';
import type { Deps } from '../src/verify';
import type { androidpublisher_v3 } from '@googleapis/androidpublisher';

export const NOW = Date.parse('2026-10-19T12:00:00Z');
export const DAY = 24 * 60 * 60 * 1000;

export const CONFIG: Config = {
  packageName: 'com.example.app',
  products: {
    premium_sub: { type: 'SUBSCRIPTION', entitlements: ['premium'] },
    premium_lifetime: { type: 'NON_CONSUMABLE', entitlements: ['premium'] },
    coins_100: { type: 'CONSUMABLE', entitlements: [] },
  },
};

/** A `purchases.subscriptionsv2.get` response, as Play's JSON. */
export function subscriptionResponse(
  state: string,
  options: { expiry?: number; acknowledged?: boolean; linked?: string; productId?: string } = {},
): androidpublisher_v3.Schema$SubscriptionPurchaseV2 {
  return {
    kind: 'androidpublisher#subscriptionPurchaseV2',
    subscriptionState: `SUBSCRIPTION_STATE_${state}`,
    startTime: new Date(NOW - 10 * DAY).toISOString(),
    acknowledgementState: options.acknowledged ? 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED' : 'ACKNOWLEDGEMENT_STATE_PENDING',
    linkedPurchaseToken: options.linked,
    lineItems: [{ productId: options.productId ?? 'premium_sub', expiryTime: new Date(options.expiry ?? NOW + 20 * DAY).toISOString() }],
  };
}

/** A `purchases.productsv2.getproductpurchasev2` response, as Play's JSON. */
export function productResponse(productId: string, state: string): androidpublisher_v3.Schema$ProductPurchaseV2 {
  return {
    kind: 'androidpublisher#productPurchaseV2',
    productLineItem: [{ productId, productOfferDetails: { quantity: 1 } }],
    purchaseStateContext: { purchaseState: state },
    purchaseCompletionTime: new Date(NOW - DAY).toISOString(),
    acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING',
  };
}

/** Answers from [subscriptions] and [products] by token, through the same mapping as the real API. */
export class FakePlay implements Play {
  subscriptions = new Map<string, androidpublisher_v3.Schema$SubscriptionPurchaseV2>();
  products = new Map<string, androidpublisher_v3.Schema$ProductPurchaseV2>();
  voided = new Set<string>();
  acknowledged: string[] = [];
  calls = 0;
  failWith: Error | null = null;

  async subscription(_packageName: string, token: string): Promise<PlayPurchase> {
    this.calls++;
    if (this.failWith) throw this.failWith;
    const sub = this.subscriptions.get(token);
    if (!sub) throw new UnknownTokenError('404');
    return subscriptionFrom(sub);
  }

  async product(_packageName: string, token: string): Promise<PlayPurchase> {
    this.calls++;
    if (this.failWith) throw this.failWith;
    const product = this.products.get(token);
    if (!product) throw new UnknownTokenError('404');
    return productFrom(product);
  }

  async acknowledgeSubscription(_p: string, id: string, token: string): Promise<void> {
    this.acknowledged.push(`${id}:${token}`);
  }

  async acknowledgeProduct(_p: string, id: string, token: string): Promise<void> {
    this.acknowledged.push(`${id}:${token}`);
  }

  async isVoided(_p: string, token: string): Promise<boolean> {
    this.calls++;
    return this.voided.has(token);
  }
}

/** The same rules as `FirestoreStore`, in memory (one transaction is one synchronous step here). */
export class MemoryStore implements Store {
  records = new Map<string, PurchaseRecord>();
  messages = new Set<string>();

  async bind(hash: string, record: PurchaseRecord, linked: string | null): Promise<BindResult> {
    const existing = this.records.get(hash);
    if (existing && existing.uid !== record.uid) return 'other-user';
    if (existing?.voided) return 'voided';
    this.records.set(hash, existing ? { ...record, replacedBy: existing.replacedBy } : record);
    const old = linked ? this.records.get(linked) : undefined;
    if (linked && old && old.uid === record.uid) this.records.set(linked, { ...old, replacedBy: hash });
    return 'bound';
  }

  async get(hash: string): Promise<PurchaseRecord | null> {
    return this.records.get(hash) ?? null;
  }

  async update(hash: string, patch: Partial<PurchaseRecord>): Promise<boolean> {
    const existing = this.records.get(hash);
    if (!existing) return false;
    this.records.set(hash, { ...existing, ...patch });
    return true;
  }

  async byUser(uid: string): Promise<PurchaseRecord[]> {
    return [...this.records.values()].filter((record) => record.uid === uid);
  }

  async isProcessed(messageId: string): Promise<boolean> {
    return this.messages.has(messageId);
  }

  async markProcessed(messageId: string): Promise<void> {
    this.messages.add(messageId);
  }
}

export function deps(): Deps & { play: FakePlay; store: MemoryStore } {
  return { config: CONFIG, play: new FakePlay(), store: new MemoryStore(), now: () => NOW, log: () => {} };
}

export function request(token: string, productIds: string[], type: string): Record<string, unknown> {
  return {
    packageName: CONFIG.packageName,
    purchaseToken: token,
    productIds,
    type,
    accountIdHash: 'abc',
    attribution: { placement: 'home', productId: productIds[0], recovered: false },
  };
}
