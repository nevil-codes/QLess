# Firebase Setup Instructions for QLess

## Step 1: Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click **"Add Project"**
3. Enter project name: `QLess`
4. Enable/Disable Google Analytics (optional)
5. Click **"Create Project"**

## Step 2: Add Android App to Firebase

1. In Firebase Console, click the **Android icon** to add an app
2. Enter package name: `com.example.qless`
3. Enter app nickname: `QLess Android`
4. Enter SHA-1 certificate (for Google Sign-In):
   ```bash
   # Get debug SHA-1
   cd /Users/nick/AndroidStudioProjects/QLess
   ./gradlew signingReport
   ```
5. Click **"Register App"**

## Step 3: Download google-services.json

1. Download the `google-services.json` file
2. **Replace** the placeholder file at:
   ```
   app/google-services.json
   ```

## Step 4: Enable Firebase Authentication

1. In Firebase Console, go to **Build > Authentication**
2. Click **"Get Started"**
3. Enable **Email/Password** sign-in method
4. Enable **Google** sign-in method:
   - Add your support email
   - Copy the **Web Client ID** for Google Sign-In

## Step 5: Setup Firestore Database

1. Go to **Build > Firestore Database**
2. Click **"Create Database"**
3. Choose **Start in test mode** (for development)
4. Select your preferred region
5. Click **"Enable"**

## Step 6: Firestore and Storage Security Rules

Rules live in the repo as `firestore.rules` and `storage.rules` (see
`FIRESTORE_RULES.md` for a summary). Deploy them with the Firebase CLI
from the repo root:

```bash
firebase login
firebase deploy --only firestore:rules,storage
```

Don't edit rules in the Console; changes there are overwritten by the
next deploy.

## Data Structure

### Users Collection
```
users/{userId}/
  firstName: string
  lastName: string
  email: string
  phone: string
  photoUrl: string (optional)
  authProvider: string (email/google)
  createdAt: number (timestamp)
```

### Products Collection
```
products/{productId}/
  name: string
  brand: string
  category: string
  description: string
  imageUrl: string
  sku: string
  rating: number
  reviews: number
  prices: [
    {
      storeId: string
      storeName: string
      price: number
      originalPrice: number
      quantity: number
      inStock: boolean
    }
  ]
  createdAt: number
  updatedAt: number
```

### Stores Collection
```
stores/{storeId}/
  name: string
  category: string
  phone: string
  rating: number
  reviews: number
  address: {
    formattedAddress: string
    latitude: number
    longitude: number
  }
```

### Reservations Collection
```
reservations/{reservationId}/
  reservationId: string
  userId: string
  items: [
    {
      productId: string
      productName: string
      storeName: string
      price: number
      quantity: number
    }
  ]
  subtotal: number
  serviceFee: number
  total: number
  storeName: string
  status: string (reserved/picked_up/expired/cancelled)
  createdAt: number
  pickupDeadline: number
```

### User Events Collection (ML)
```
user_events/{eventId}/
  userId: string
  eventType: string (view/cart_add/purchase/search)
  productId: string (optional)
  productName: string (optional)
  category: string (optional)
  brand: string (optional)
  searchQuery: string (optional)
  timestamp: number
```

## Testing

1. Build and run the app
2. Create a new account via Sign Up
3. Check Firebase Console > Authentication for new user
4. Check Firestore > users collection for user data
5. Add products via Admin Panel
6. Test checkout flow and verify reservations

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Build fails | Verify `google-services.json` is valid and up-to-date |
| Auth fails | Enable Email/Password in Firebase Authentication |
| Permission denied | Update Firestore rules and click Publish |
| Reservations not showing | Check Logcat for errors, verify rules |
| Location not working | Grant location permission in device settings |

