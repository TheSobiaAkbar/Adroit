// Edit before deploying: your app's package name, its Play products, and the RTDN topic (README.md).

export type ProductType = 'SUBSCRIPTION' | 'NON_CONSUMABLE' | 'CONSUMABLE';

export interface ProductConfig {
  type: ProductType;
  /** What the product unlocks; the same names as the app's product definitions. Empty for a consumable. */
  entitlements: string[];
}

export interface Config {
  packageName: string;
  /** The allow-list: a purchase of any other product is rejected. */
  products: Record<string, ProductConfig>;
}

export const CONFIG: Config = {
  packageName: 'com.example.app',
  products: {
    premium_subscription: { type: 'SUBSCRIPTION', entitlements: ['premium'] },
    premium_lifetime: { type: 'NON_CONSUMABLE', entitlements: ['premium'] },
    coins_100: { type: 'CONSUMABLE', entitlements: [] },
  },
};

/** The Pub/Sub topic Play Console publishes real-time developer notifications to. */
export const RTDN_TOPIC = 'play-rtdn';
