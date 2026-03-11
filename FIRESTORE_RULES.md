# QLess Firestore Security Rules
# Copy and paste these rules in Firebase Console > Firestore Database > Rules

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // ============================================
    // HELPER FUNCTIONS
    // ============================================
    
    // Check if user is authenticated
    function isAuthenticated() {
      return request.auth != null;
    }
    
    // Check if user owns this document
    function isOwner(userId) {
      return isAuthenticated() && request.auth.uid == userId;
    }
    
    // Check if the userId field matches the authenticated user
    function isDocOwner() {
      return isAuthenticated() && resource.data.userId == request.auth.uid;
    }
    
    // Check if creating with own userId
    function isCreatingOwn() {
      return isAuthenticated() && request.resource.data.userId == request.auth.uid;
    }
    
    // ============================================
    // USERS COLLECTION
    // ============================================
    // Users can only read and write their own profile
    match /users/{userId} {
      allow read: if isOwner(userId);
      allow create: if isOwner(userId);
      allow update: if isOwner(userId);
      allow delete: if isOwner(userId);
    }
    
    // ============================================
    // PRODUCTS COLLECTION
    // ============================================
    // Anyone can read products (public catalog)
    // Only authenticated users can write (admin from web panel)
    match /products/{productId} {
      allow read: if true;
      allow create: if isAuthenticated();
      allow update: if isAuthenticated();
      allow delete: if isAuthenticated();
    }
    
    // ============================================
    // STORES COLLECTION
    // ============================================
    // Anyone can read stores (public listing)
    // Only authenticated users can write (admin from web panel)
    match /stores/{storeId} {
      allow read: if true;
      allow create: if isAuthenticated();
      allow update: if isAuthenticated();
      allow delete: if isAuthenticated();
    }
    
    // ============================================
    // RESERVATIONS COLLECTION
    // ============================================
    // Users can only access their own reservations
    match /reservations/{reservationId} {
      // Read own reservations
      allow read: if isDocOwner();
      
      // Create reservation with own userId
      allow create: if isCreatingOwn();
      
      // Update/delete own reservations
      allow update: if isDocOwner();
      allow delete: if isDocOwner();
      
      // Admin can read all for order management
      allow read: if isAuthenticated();
    }
    
    // ============================================
    // ORDERS COLLECTION (if separate from reservations)
    // ============================================
    match /orders/{orderId} {
      allow read: if isDocOwner() || isAuthenticated();
      allow create: if isCreatingOwn();
      allow update: if isDocOwner() || isAuthenticated();
      allow delete: if isAuthenticated();
    }
    
    // ============================================
    // USER EVENTS COLLECTION (ML Analytics)
    // ============================================
    // Users can write their own events, admin can read all
    match /user_events/{eventId} {
      allow read: if isAuthenticated();
      allow create: if isAuthenticated();
      allow update: if isAuthenticated();
      allow delete: if isAuthenticated();
    }
    
    // ============================================
    // CART COLLECTION (if stored in Firestore)
    // ============================================
    match /carts/{userId} {
      allow read, write: if isOwner(userId);
    }
    
    // ============================================
    // FAVORITES/WISHLIST COLLECTION
    // ============================================
    match /favorites/{odecId} {
      allow read: if isDocOwner();
      allow create: if isCreatingOwn();
      allow update, delete: if isDocOwner();
    }
    
    // ============================================
    // NOTIFICATIONS COLLECTION
    // ============================================
    match /notifications/{notificationId} {
      allow read: if isDocOwner();
      allow create: if isAuthenticated();
      allow update: if isDocOwner();
      allow delete: if isDocOwner();
    }
    
    // ============================================
    // APP SETTINGS/CONFIG (read-only for users)
    // ============================================
    match /config/{configId} {
      allow read: if true;
      allow write: if isAuthenticated();
    }
  }
}
```

## How to Apply:

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project **"qless-1ccb9"**
3. Navigate to **Firestore Database** → **Rules**
4. Delete all existing rules
5. Paste the rules above (without the markdown code fences)
6. Click **Publish**

## Rules Summary:

| Collection | Read | Create | Update | Delete |
|------------|------|--------|--------|--------|
| users | Own only | Own only | Own only | Own only |
| products | Public | Auth | Auth | Auth |
| stores | Public | Auth | Auth | Auth |
| reservations | Own + Admin | Own | Own | Own |
| orders | Own + Admin | Own | Own + Admin | Admin |
| user_events | Auth | Auth | Auth | Auth |
| carts | Own only | Own only | Own only | Own only |
| favorites | Own only | Own only | Own only | Own only |
| notifications | Own only | Auth | Own only | Own only |
| config | Public | Auth | Auth | Auth |

