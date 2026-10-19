import assert from 'node:assert/strict';
import { test } from 'node:test';
import { grants } from '../src/entitlements';
import { tokenHash } from '../src/store';
import { verify } from '../src/verify';
import { DAY, deps, NOW, productResponse, request, subscriptionResponse } from './fakes';

test('an active subscription is verified, bound and acknowledged', async () => {
  const d = deps();
  d.play.subscriptions.set('tok-1', subscriptionResponse('ACTIVE'));

  const reply = await verify(d, 'alice', request('tok-1', ['premium_sub'], 'SUBSCRIPTION'));

  assert.deepEqual(reply, {
    status: 200,
    body: { status: 'VERIFIED', acknowledged: true, productIds: ['premium_sub'], entitlements: ['premium'] },
  });
  assert.deepEqual(d.play.acknowledged, ['premium_sub:tok-1']);
  const record = d.store.records.get(tokenHash('tok-1'));
  assert.equal(record?.uid, 'alice');
  assert.equal(JSON.stringify([...d.store.records.entries()]).includes('tok-1'), false, 'the token is never stored');
});

test('a subscription in grace is verified; already acknowledged is not acknowledged again', async () => {
  const d = deps();
  d.play.subscriptions.set('tok-1', subscriptionResponse('IN_GRACE_PERIOD', { acknowledged: true }));

  const reply = await verify(d, 'alice', request('tok-1', ['premium_sub'], 'SUBSCRIPTION'));

  assert.equal(reply.body.status, 'VERIFIED');
  assert.equal(reply.body.acknowledged, true);
  assert.deepEqual(d.play.acknowledged, []);
});

test('on hold, expired and pending-cancelled subscriptions are rejected', async () => {
  for (const [state, expiry] of [['ON_HOLD', NOW + DAY], ['EXPIRED', NOW - DAY], ['PENDING_PURCHASE_CANCELED', NOW]] as const) {
    const d = deps();
    d.play.subscriptions.set('tok', subscriptionResponse(state, { expiry }));
    const reply = await verify(d, 'alice', request('tok', ['premium_sub'], 'SUBSCRIPTION'));
    assert.equal(reply.body.status, 'REJECTED', state);
    assert.equal(d.store.records.size, 0);
  }
});

test('a cancelled subscription is verified until it expires', async () => {
  const d = deps();
  d.play.subscriptions.set('live', subscriptionResponse('CANCELED', { expiry: NOW + DAY }));
  d.play.subscriptions.set('over', subscriptionResponse('CANCELED', { expiry: NOW - 1 }));

  assert.equal((await verify(d, 'alice', request('live', ['premium_sub'], 'SUBSCRIPTION'))).body.status, 'VERIFIED');
  assert.equal((await verify(d, 'alice', request('over', ['premium_sub'], 'SUBSCRIPTION'))).body.status, 'REJECTED');
});

test('a token bound to another user is rejected; the same user may verify again', async () => {
  const d = deps();
  d.play.subscriptions.set('tok-1', subscriptionResponse('ACTIVE'));
  await verify(d, 'alice', request('tok-1', ['premium_sub'], 'SUBSCRIPTION'));

  const other = await verify(d, 'mallory', request('tok-1', ['premium_sub'], 'SUBSCRIPTION'));
  const again = await verify(d, 'alice', request('tok-1', ['premium_sub'], 'SUBSCRIPTION'));

  assert.deepEqual(other.body, { status: 'REJECTED', reason: 'token bound to another account' });
  assert.equal(again.body.status, 'VERIFIED');
  assert.equal(d.store.records.get(tokenHash('tok-1'))?.uid, 'alice');
});

test('package, product and token checks', async () => {
  const d = deps();
  d.play.subscriptions.set('tok-1', subscriptionResponse('ACTIVE', { productId: 'other_sub' }));

  const wrongPackage = await verify(d, 'alice', { ...request('tok-1', ['premium_sub'], 'SUBSCRIPTION'), packageName: 'com.evil' });
  const notAllowed = await verify(d, 'alice', request('tok-1', ['other_sub'], 'SUBSCRIPTION'));
  const wrongType = await verify(d, 'alice', request('tok-1', ['premium_sub'], 'NON_CONSUMABLE'));
  const mismatch = await verify(d, 'alice', request('tok-1', ['premium_sub'], 'SUBSCRIPTION'));
  const unknown = await verify(d, 'alice', request('nope', ['premium_sub'], 'SUBSCRIPTION'));

  assert.deepEqual(wrongPackage.body, { status: 'REJECTED', reason: 'package mismatch' });
  assert.deepEqual(notAllowed.body, { status: 'REJECTED', reason: 'unknown product' });
  assert.deepEqual(wrongType.body, { status: 'REJECTED', reason: 'unknown product' });
  assert.deepEqual(mismatch.body, { status: 'REJECTED', reason: 'product mismatch' });
  assert.deepEqual(unknown.body, { status: 'REJECTED', reason: 'unknown purchase token' });
});

test('one-time products: purchased is verified, pending is try-later, cancelled is rejected; consumables are not acknowledged', async () => {
  const d = deps();
  d.play.products.set('life', productResponse('premium_lifetime', 'PURCHASED'));
  d.play.products.set('coins', productResponse('coins_100', 'PURCHASED'));
  d.play.products.set('wait', productResponse('premium_lifetime', 'PENDING'));
  d.play.products.set('gone', productResponse('premium_lifetime', 'CANCELLED'));

  const life = await verify(d, 'alice', request('life', ['premium_lifetime'], 'NON_CONSUMABLE'));
  const coins = await verify(d, 'alice', request('coins', ['coins_100'], 'CONSUMABLE'));
  const wait = await verify(d, 'alice', request('wait', ['premium_lifetime'], 'NON_CONSUMABLE'));
  const gone = await verify(d, 'alice', request('gone', ['premium_lifetime'], 'NON_CONSUMABLE'));

  assert.deepEqual(life.body, { status: 'VERIFIED', acknowledged: true, productIds: ['premium_lifetime'], entitlements: ['premium'] });
  assert.deepEqual(coins.body, { status: 'VERIFIED', acknowledged: false, productIds: ['coins_100'], entitlements: [] });
  assert.equal(wait.status, 503);
  assert.deepEqual(gone.body, { status: 'REJECTED', reason: 'purchase cancelled' });
  assert.deepEqual(d.play.acknowledged, ['premium_lifetime:life']);
});

test('a Play outage is try-later (non-2xx) and a bad request is 400', async () => {
  const d = deps();
  d.play.failWith = Object.assign(new Error('quota'), { status: 503 });

  assert.equal((await verify(d, 'alice', request('tok', ['premium_sub'], 'SUBSCRIPTION'))).status, 503);
  assert.equal((await verify(d, 'alice', { purchaseToken: 'x' })).status, 400);
  assert.equal((await verify(d, 'alice', 'nonsense')).status, 400);
});

test('an upgrade replaces the old purchase', async () => {
  const d = deps();
  d.play.subscriptions.set('old', subscriptionResponse('ACTIVE'));
  d.play.subscriptions.set('new', subscriptionResponse('ACTIVE', { linked: 'old' }));
  await verify(d, 'alice', request('old', ['premium_sub'], 'SUBSCRIPTION'));
  await verify(d, 'alice', request('new', ['premium_sub'], 'SUBSCRIPTION'));

  assert.equal(d.store.records.get(tokenHash('old'))?.replacedBy, tokenHash('new'));
  assert.equal(grants(await d.store.byUser('alice'), NOW).length, 1);
});
