import { createHash } from 'node:crypto';
import type { Firestore } from 'firebase-admin/firestore';
import type { ProductType } from './config';

/** One purchase token, bound to one user. Stored under the token's SHA-256; the token itself is never stored. */
export interface PurchaseRecord {
  uid: string;
  type: ProductType;
  productIds: string[];
  entitlements: string[];
  /** Play's state (see `PlayPurchase.state`). */
  state: string;
  expiryMillis: number | null;
  purchaseTimeMillis: number;
  /** Refunded, charged back or revoked (a voided purchase). */
  voided: boolean;
  /** The token hash of the purchase that replaced this one (subscription upgrade or downgrade). */
  replacedBy: string | null;
  updatedMillis: number;
}

export type BindResult = 'bound' | 'other-user' | 'voided';

/** Persistence, behind an interface so the logic is testable without Firestore. */
export interface Store {
  /**
   * In one transaction: binds [tokenHash] to [record.uid] unless another user owns it or it was voided, and marks
   * [linkedTokenHash] (the purchase it replaced) as replaced when the same user owns it. A record that exists keeps
   * its `voided` and `replacedBy`.
   */
  bind(tokenHash: string, record: PurchaseRecord, linkedTokenHash: string | null): Promise<BindResult>;
  get(tokenHash: string): Promise<PurchaseRecord | null>;
  /** Updates an existing record; false when there is none. */
  update(tokenHash: string, patch: Partial<PurchaseRecord>): Promise<boolean>;
  byUser(uid: string): Promise<PurchaseRecord[]>;
  isProcessed(messageId: string): Promise<boolean>;
  markProcessed(messageId: string): Promise<void>;
}

export function tokenHash(token: string): string {
  return createHash('sha256').update(token).digest('hex');
}

const PURCHASES = 'billingPurchases';
const MESSAGES = 'billingRtdnMessages';

export class FirestoreStore implements Store {
  constructor(private readonly db: Firestore) {}

  bind(tokenHash: string, record: PurchaseRecord, linkedTokenHash: string | null): Promise<BindResult> {
    return this.db.runTransaction(async (tx) => {
      const ref = this.db.collection(PURCHASES).doc(tokenHash);
      const linkedRef = linkedTokenHash ? this.db.collection(PURCHASES).doc(linkedTokenHash) : null;
      // Firestore transactions read everything before writing.
      const existing = await tx.get(ref);
      const linked = linkedRef ? await tx.get(linkedRef) : null;
      if (existing.exists && existing.get('uid') !== record.uid) return 'other-user';
      if (existing.exists && existing.get('voided') === true) return 'voided';
      tx.set(ref, existing.exists ? { ...record, replacedBy: existing.get('replacedBy') ?? null } : record);
      if (linkedRef && linked?.exists && linked.get('uid') === record.uid) {
        tx.update(linkedRef, { replacedBy: tokenHash, updatedMillis: record.updatedMillis });
      }
      return 'bound';
    });
  }

  async get(tokenHash: string): Promise<PurchaseRecord | null> {
    const doc = await this.db.collection(PURCHASES).doc(tokenHash).get();
    return doc.exists ? (doc.data() as PurchaseRecord) : null;
  }

  async update(tokenHash: string, patch: Partial<PurchaseRecord>): Promise<boolean> {
    try {
      await this.db.collection(PURCHASES).doc(tokenHash).update(patch);
      return true;
    } catch (e) {
      if ((e as { code?: number }).code === 5) return false; // NOT_FOUND
      throw e;
    }
  }

  async byUser(uid: string): Promise<PurchaseRecord[]> {
    const snapshot = await this.db.collection(PURCHASES).where('uid', '==', uid).get();
    return snapshot.docs.map((doc) => doc.data() as PurchaseRecord);
  }

  async isProcessed(messageId: string): Promise<boolean> {
    return (await this.db.collection(MESSAGES).doc(messageId).get()).exists;
  }

  async markProcessed(messageId: string): Promise<void> {
    await this.db.collection(MESSAGES).doc(messageId).set({ processedMillis: Date.now() });
  }
}
