import type { Config, ProductType } from './config';
import { type Play, type PlayPurchase, UnknownTokenError } from './play';
import { type Store, tokenHash } from './store';

export interface Deps {
  config: Config;
  play: Play;
  store: Store;
  now: () => number;
  log: (message: string) => void;
}

/** An HTTP answer: [status] and the JSON [body]. */
export interface Reply {
  status: number;
  body: Record<string, unknown>;
}

const TYPES: ProductType[] = ['SUBSCRIPTION', 'NON_CONSUMABLE', 'CONSUMABLE'];

/** Subscription states that may be verified: CANCELED only stops renewal, access lasts until expiry. */
const SUBSCRIPTION_OK = new Set(['ACTIVE', 'IN_GRACE_PERIOD', 'CANCELED']);

const rejected = (reason: string): Reply => ({ status: 200, body: { status: 'REJECTED', reason } });

/** Any non-2xx makes the app try again on its next sync (docs/BILLING_BACKEND.md). */
const tryLater = (error: string): Reply => ({ status: 503, body: { error } });

/** `POST /verify` for the signed-in user [uid] (docs/BILLING_BACKEND.md). */
export async function verify(deps: Deps, uid: string, input: unknown): Promise<Reply> {
  const request = parse(input);
  if (typeof request === 'string') return { status: 400, body: { error: request } };
  const { config, play, store } = deps;
  const hash = tokenHash(request.purchaseToken);
  const tag = `purchase ${hash.slice(0, 12)}`;

  if (request.packageName !== config.packageName) return rejected('package mismatch');
  const products = request.productIds.map((id) => config.products[id]);
  if (products.some((product) => product?.type !== request.type)) return rejected('unknown product');

  let purchase: PlayPurchase;
  try {
    purchase = request.type === 'SUBSCRIPTION'
      ? await play.subscription(request.packageName, request.purchaseToken)
      : await play.product(request.packageName, request.purchaseToken);
  } catch (e) {
    if (e instanceof UnknownTokenError) return rejected('unknown purchase token');
    deps.log(`${tag}: Play API failed: ${(e as Error).message}`);
    return tryLater('play unavailable');
  }

  if (!sameIds(purchase.productIds, request.productIds)) return rejected('product mismatch');
  if (purchase.state === 'PENDING') return tryLater('purchase pending');
  if (request.type === 'SUBSCRIPTION') {
    const expired = purchase.expiryMillis !== null && purchase.expiryMillis <= deps.now();
    if (!SUBSCRIPTION_OK.has(purchase.state) || expired) return rejected(`subscription ${purchase.state.toLowerCase()}`);
  } else if (purchase.state !== 'PURCHASED') {
    return rejected(`purchase ${purchase.state.toLowerCase()}`);
  }

  const entitlements = [...new Set(products.flatMap((product) => product!.entitlements))].sort();
  const bound = await store.bind(
    hash,
    {
      uid,
      type: request.type,
      productIds: purchase.productIds,
      entitlements,
      state: purchase.state,
      expiryMillis: purchase.expiryMillis,
      purchaseTimeMillis: purchase.purchaseTimeMillis,
      voided: false,
      replacedBy: null,
      updatedMillis: deps.now(),
    },
    purchase.linkedPurchaseToken ? tokenHash(purchase.linkedPurchaseToken) : null,
  );
  if (bound === 'other-user') {
    deps.log(`${tag}: rejected, bound to another user`);
    return rejected('token bound to another account');
  }
  if (bound === 'voided') return rejected('purchase voided');

  // Consumables are consumed by the app (consuming acknowledges); everything else is acknowledged here.
  let acknowledged = purchase.acknowledged;
  if (!acknowledged && request.type !== 'CONSUMABLE') {
    try {
      if (request.type === 'SUBSCRIPTION') {
        await play.acknowledgeSubscription(request.packageName, purchase.productIds[0], request.purchaseToken);
      } else {
        await play.acknowledgeProduct(request.packageName, purchase.productIds[0], request.purchaseToken);
      }
      acknowledged = true;
    } catch (e) {
      // The app acknowledges instead.
      deps.log(`${tag}: acknowledge failed: ${(e as Error).message}`);
    }
  }
  return { status: 200, body: { status: 'VERIFIED', acknowledged, productIds: purchase.productIds, entitlements } };
}

interface VerifyRequest {
  packageName: string;
  purchaseToken: string;
  productIds: string[];
  type: ProductType;
}

/** The request, or what is wrong with it. Unknown fields (attribution, accountIdHash...) are ignored. */
function parse(input: unknown): VerifyRequest | string {
  if (typeof input !== 'object' || input === null) return 'body must be a JSON object';
  const body = input as Record<string, unknown>;
  if (typeof body.packageName !== 'string') return 'packageName missing';
  if (typeof body.purchaseToken !== 'string' || body.purchaseToken.length === 0 || body.purchaseToken.length > 4096) {
    return 'purchaseToken missing or invalid';
  }
  const ids = body.productIds;
  if (!Array.isArray(ids) || ids.length === 0 || !ids.every((id) => typeof id === 'string')) return 'productIds missing';
  if (!TYPES.includes(body.type as ProductType)) return 'type invalid';
  return { packageName: body.packageName, purchaseToken: body.purchaseToken, productIds: ids, type: body.type as ProductType };
}

function sameIds(a: string[], b: string[]): boolean {
  const left = new Set(a);
  const right = new Set(b);
  return left.size === right.size && [...left].every((id) => right.has(id));
}
