import assert from 'node:assert/strict';
import { test } from 'node:test';
import { grants } from '../src/entitlements';
import { handleNotification } from '../src/rtdn';
import { tokenHash } from '../src/store';
import { verify } from '../src/verify';
import { CONFIG, DAY, deps, NOW, productResponse, request, subscriptionResponse } from './fakes';

const subscriptionNotice = (token: string, type: number) => ({
  version: '1.0',
  packageName: CONFIG.packageName,
  eventTimeMillis: String(NOW),
  subscriptionNotification: { version: '1.0', notificationType: type, purchaseToken: token },
});

async function verifiedSubscription() {
  const d = deps();
  d.play.subscriptions.set('tok', subscriptionResponse('ACTIVE'));
  await verify(d, 'alice', request('tok', ['premium_sub'], 'SUBSCRIPTION'));
  return d;
}

const stateOf = async (d: ReturnType<typeof deps>) => grants(await d.store.byUser('alice'), d.now()).map((g) => g.state);

test('the state is re-read from Play: grace, hold, recovered, expired, revoked', async () => {
  const d = await verifiedSubscription();
  const steps: [string, number, string[]][] = [
    ['IN_GRACE_PERIOD', 6, ['GRACE_FROM_BACKEND']],
    ['ON_HOLD', 5, ['SUSPENDED']],
    ['ACTIVE', 1, ['ACTIVE']],
    ['EXPIRED', 13, []],
  ];
  for (const [i, [state, type, expected]] of steps.entries()) {
    d.play.subscriptions.set('tok', subscriptionResponse(state, { expiry: state === 'EXPIRED' ? NOW - 1 : NOW + DAY }));
    await handleNotification(d, `m-${i}`, subscriptionNotice('tok', type));
    assert.deepEqual(await stateOf(d), expected, state);
  }

  // Revoked: Play reports the subscription expired at once; the notification type alone changes nothing.
  const revoked = await verifiedSubscription();
  revoked.play.subscriptions.set('tok', subscriptionResponse('EXPIRED', { expiry: NOW - 1 }));
  await handleNotification(revoked, 'm-r', subscriptionNotice('tok', 12));
  assert.deepEqual(await stateOf(revoked), []);
});

test('the payload is never trusted: Play still says active, so it stays active', async () => {
  const d = await verifiedSubscription();

  await handleNotification(d, 'm-1', subscriptionNotice('tok', 13)); // "EXPIRED", but Play disagrees.

  assert.deepEqual(await stateOf(d), ['ACTIVE']);
});

test('a replayed message is handled once', async () => {
  const d = await verifiedSubscription();
  d.play.subscriptions.set('tok', subscriptionResponse('ON_HOLD'));
  await handleNotification(d, 'm-1', subscriptionNotice('tok', 5));
  const calls = d.play.calls;
  d.play.subscriptions.set('tok', subscriptionResponse('ACTIVE'));

  await handleNotification(d, 'm-1', subscriptionNotice('tok', 5));

  assert.equal(d.play.calls, calls);
  assert.deepEqual(await stateOf(d), ['SUSPENDED']);
});

test('a voided purchase (refund) revokes the grant once Play lists it, and blocks re-verification', async () => {
  const d = deps();
  d.play.products.set('life', productResponse('premium_lifetime', 'PURCHASED'));
  await verify(d, 'alice', request('life', ['premium_lifetime'], 'NON_CONSUMABLE'));
  const voided = {
    packageName: CONFIG.packageName,
    eventTimeMillis: String(NOW),
    voidedPurchaseNotification: { purchaseToken: 'life', orderId: 'GPA.1', productType: 2, refundType: 1 },
  };

  await assert.rejects(handleNotification(d, 'v-1', voided)); // Not listed yet: Pub/Sub retries.
  assert.deepEqual(await stateOf(d), ['ACTIVE']);
  d.play.voided.add('life');
  await handleNotification(d, 'v-1', voided);

  assert.deepEqual(await stateOf(d), []);
  assert.equal(d.store.records.get(tokenHash('life'))?.voided, true);
  const again = await verify(d, 'alice', request('life', ['premium_lifetime'], 'NON_CONSUMABLE'));
  assert.deepEqual(again.body, { status: 'REJECTED', reason: 'purchase voided' });
});

test('other packages, test notifications and unverified purchases are acknowledged without changes', async () => {
  const d = deps();
  await handleNotification(d, 'a', { ...subscriptionNotice('x', 4), packageName: 'com.other' });
  await handleNotification(d, 'b', { packageName: CONFIG.packageName, testNotification: { version: '1.0' } });
  await handleNotification(d, 'c', subscriptionNotice('never-verified', 4));
  await handleNotification(d, 'd', null);

  assert.equal(d.play.calls, 0);
  assert.equal(d.store.records.size, 0);
  assert.deepEqual([...d.store.messages], ['a', 'b', 'c', 'd']);
});
