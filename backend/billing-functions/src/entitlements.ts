import type { PurchaseRecord } from './store';

/** One entry of `GET /entitlements` (docs/BILLING_BACKEND.md). */
export interface Grant {
  productId: string;
  type: string;
  entitlements: string[];
  state: 'ACTIVE' | 'GRACE_FROM_BACKEND' | 'SUSPENDED' | 'PENDING';
  purchaseTimeMillis: number;
}

/** The grants of [records] at [now]. Expired, voided, replaced and consumable purchases grant nothing. */
export function grants(records: PurchaseRecord[], now: number): Grant[] {
  return records.flatMap((record) => {
    const state = grantState(record, now);
    if (state === null) return [];
    return record.productIds.map((productId) => ({
      productId,
      type: record.type,
      entitlements: record.entitlements,
      state,
      purchaseTimeMillis: record.purchaseTimeMillis,
    }));
  });
}

function grantState(record: PurchaseRecord, now: number): Grant['state'] | null {
  if (record.voided || record.replacedBy !== null || record.type === 'CONSUMABLE') return null;
  if (record.type === 'NON_CONSUMABLE') {
    if (record.state === 'PURCHASED') return 'ACTIVE';
    return record.state === 'PENDING' ? 'PENDING' : null;
  }
  const unexpired = record.expiryMillis === null || record.expiryMillis > now;
  switch (record.state) {
    case 'ACTIVE':
    case 'CANCELED': // Renewal off; access until expiry.
      return unexpired ? 'ACTIVE' : null;
    case 'IN_GRACE_PERIOD':
      return 'GRACE_FROM_BACKEND';
    case 'ON_HOLD':
    case 'PAUSED':
      return 'SUSPENDED';
    case 'PENDING':
      return 'PENDING';
    default: // EXPIRED, PENDING_PURCHASE_CANCELED, unknown.
      return null;
  }
}
