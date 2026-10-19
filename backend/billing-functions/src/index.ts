import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';
import { logger } from 'firebase-functions';
import { onRequest, type Request } from 'firebase-functions/v2/https';
import { onMessagePublished } from 'firebase-functions/v2/pubsub';
import { CONFIG, RTDN_TOPIC } from './config';
import { grants } from './entitlements';
import { GooglePlay } from './play';
import { handleNotification } from './rtdn';
import { FirestoreStore } from './store';
import { type Deps, verify as verifyPurchase } from './verify';

initializeApp();

const deps: Deps = {
  config: CONFIG,
  play: new GooglePlay(),
  store: new FirestoreStore(getFirestore()),
  now: Date.now,
  log: (message) => logger.info(message),
};

/** The Firebase Auth user of `Authorization: Bearer <ID token>`, or null. */
async function userId(request: Request): Promise<string | null> {
  const match = /^Bearer (.+)$/.exec(request.get('Authorization') ?? '');
  if (!match) return null;
  try {
    return (await getAuth().verifyIdToken(match[1])).uid;
  } catch {
    return null;
  }
}

/** `POST /verify` (docs/BILLING_BACKEND.md). */
export const verify = onRequest(async (request, response) => {
  if (request.method !== 'POST') {
    response.status(405).json({ error: 'POST only' });
    return;
  }
  const uid = await userId(request);
  if (uid === null) {
    response.status(401).json({ error: 'sign-in required' });
    return;
  }
  const reply = await verifyPurchase(deps, uid, request.body);
  response.status(reply.status).json(reply.body);
});

/** `GET /entitlements` (docs/BILLING_BACKEND.md): the user comes from the ID token; `account` is ignored. */
export const entitlements = onRequest(async (request, response) => {
  if (request.method !== 'GET') {
    response.status(405).json({ error: 'GET only' });
    return;
  }
  const uid = await userId(request);
  if (uid === null) {
    response.status(401).json({ error: 'sign-in required' });
    return;
  }
  response.json({ grants: grants(await deps.store.byUser(uid), deps.now()) });
});

/** Play real-time developer notifications. Retried by Pub/Sub when it throws. */
export const rtdn = onMessagePublished({ topic: RTDN_TOPIC, retry: true }, async (event) => {
  await handleNotification(deps, event.data.message.messageId, event.data.message.json);
});
