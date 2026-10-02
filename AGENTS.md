# AGENTS Guide for QLess

## App Purpose
QLess is a smart shopping app that helps users:
- **Compare prices** across different stores for products (e.g., 5kg rice: Store A €12 vs Store B €9)
- **Reserve & Pay** in-app to hold items at the store
- **Collect within 12 or 48 hours**, depending on how they pay:
  - **Pay at pickup**: 12h hold, no fee; a missed pickup is a strike, and 3 strikes means prepaying
  - **Pay in the app**: 48h hold, €0.99 service fee; if not collected, refunded minus 10%
- **Get ML-powered recommendations** based on viewing/buying behavior

## Current State (COMPLETE)
- Single-module Android app (`:app`) with Firebase backend
- Admin web panel in `/admin` folder
- All core features implemented and functional

## App Screens
| Screen | Activity | Description |
|--------|----------|-------------|
| Welcome | MainActivity | App intro with "Get Started" |
| Login | LoginActivity | Email/password login |
| Signup | SignupActivity | User registration |
| Home | HomeActivity | Categories, deals, stores |
| Search | SearchActivity | Product search |
| Product Detail | ProductDetailActivity | Price comparison |
| Cart | CartActivity | Shopping cart |
| Checkout | CheckoutActivity | Reservation confirmation |
| Orders | OrdersActivity | Order history |
| Reservations | ReservationsActivity | Active/past reservations |
| Profile | ProfileActivity | User profile |
| Settings | SettingsActivity | App settings |
| Help | HelpActivity | FAQ & support |
| Location | LocationPickerActivity | Set location |

## Architecture and Data Flow
- **Auth**: Firebase Authentication (Email/Password)
- **Database**: Cloud Firestore
- **Storage**: Firebase Storage (product images)
- **Location**: Google Play Services + OpenStreetMap Nominatim
- **Reservations**: Cloud Functions in `functions/reservations` own the whole lifecycle; the app only reads reservations
  - `createReservation`: server prices, stock hold, one reservation per store, Stripe PaymentIntent for card
  - `stripeWebhook`: marks card reservations paid (48h window starts at payment)
  - `cancelPendingPayment` / `cancelReservation`: shopper cancels; stock returned, prepaid refunded in full
  - `expireReservations` (every 15 min): expires overdue reservations (pickup = missed-pickup strike, prepaid = 90% refund), cancels abandoned card checkouts, retries failed refunds
- **ML**: Recommendation Cloud Functions in `functions/recs` (europe-west1); the app's `RecommendationEngine` is a thin client. See `docs/recommendations.md`

## Firebase Collections
| Collection | Purpose |
|------------|---------|
| users | User profiles |
| products | Product catalog |
| stores | Store listings |
| reservations | User reservations/orders |
| user_events | Behaviour events (view, search, add_to_cart from the app; purchase from Cloud Functions) |
| user_profiles | Recommendation profiles, written by Cloud Functions |
| co_purchases | Products bought together, per product |
| pickup_log | Marks reservations whose pickup was already recorded |
| users.noShowCount | Missed pickups (server-written); 3 or more means prepay only |

## Build/Test Workflows (Gradle)
```bash
./gradlew assembleDebug      # Build APK
./gradlew testDebugUnitTest  # Unit tests
./gradlew lint               # Android lint
```

## Key Files
- `app/google-services.json` - Firebase config
- `app/build.gradle` - Dependencies
- `gradle/libs.versions.toml` - Version catalog
- `FIREBASE_SETUP.md` - Firebase setup guide
- `firestore.rules` / `storage.rules` - Security rules (deploy with `firebase deploy --only firestore:rules,storage`)
- `functions/` - Cloud Functions (Node 22)
- `FIRESTORE_RULES.md` - Rules summary

## Conventions
- Package: `com.example.qless`
- Strings: `app/src/main/res/values/strings.xml`
- Layout naming: `activity_*.xml`, `item_*.xml`
- Java 11 compatibility
- AndroidX only
