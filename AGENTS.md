# AGENTS Guide for QLess

## App Purpose
QLess is a smart shopping app that helps users:
- **Compare prices** across different stores for products (e.g., 5kg rice: Store A €12 vs Store B €9)
- **Reserve & Pay** in-app to hold items at the store
- **Collect within 48 hours** (10% penalty if not picked up, rest refunded)
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
