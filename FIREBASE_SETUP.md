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

## Step 6: Firestore Security Rules

Go to **Firebase Console > Firestore Database > Rules** and replace with:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Users collection - users can only access their own data
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Products collection - anyone can read, authenticated users can write
    match /products/{productId} {
      allow read: if true;
      allow write: if request.auth != null;
    }
    
    // Stores collection - anyone can read, authenticated users can write
    match /stores/{storeId} {
      allow read: if true;
      allow write: if request.auth != null;
    }
    
    // Orders collection - authenticated users can read/write
    match /orders/{orderId} {
      allow read, write: if request.auth != null;
    }
    
    // Reservations collection - users can access their own reservations
    match /reservations/{reservationId} {
      allow read, write: if request.auth != null;
    }
    
    // User events for ML - authenticated users can write
    match /user_events/{eventId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
  }
}
```

**Important:** After updating rules, click **Publish** to apply them.

## Step 7: Google Sign-In Setup (Required)

### 7.1 Enable Google Sign-In in Firebase
1. Go to **Firebase Console > Authentication > Sign-in method**
2. Click **Google** provider
3. Toggle **Enable**
4. Enter your **Project support email**
5. Click **Save**

### 7.2 Add SHA-1 Certificate Fingerprint
1. Generate SHA-1 fingerprint:
   ```bash
   cd /Users/nick/AndroidStudioProjects/QLess
   ./gradlew signingReport
   ```
2. Copy the **SHA1** value from the debug variant
3. Go to **Firebase Console > Project Settings > Your apps**
4. Click **Add fingerprint** and paste the SHA-1

### 7.3 Download Updated google-services.json
1. After adding SHA-1, download the **updated** `google-services.json`
2. Replace the file at `app/google-services.json`
3. The file should now contain `oauth_client` entries

### 7.4 Get Web Client ID
1. Go to **Firebase Console > Authentication > Sign-in method > Google**
2. Expand Google and copy the **Web client ID** (looks like: `xxxxx.apps.googleusercontent.com`)
3. Open `app/src/main/res/values/strings.xml`
4. Replace the placeholder:
   ```xml
   <string name="default_web_client_id">YOUR_WEB_CLIENT_ID_HERE</string>
   ```
   With your actual Web Client ID:
   ```xml
   <string name="default_web_client_id">123456789-abcdefg.apps.googleusercontent.com</string>
   ```

### 7.5 Verify OAuth Consent Screen
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select your Firebase project
3. Go to **APIs & Services > OAuth consent screen**
4. Ensure the app is configured (even in Testing mode)
5. Add test users if in Testing mode

## Data Structure

### Users Collection
```
users/
  {userId}/
    firstName: string
    lastName: string
    email: string
    phone: string
    createdAt: timestamp
```

## Testing

1. Build and run the app
2. Create a new account via Sign Up
3. Check Firebase Console > Authentication for new user
4. Check Firestore > users collection for user data

## Troubleshooting

- **Build fails**: Make sure `google-services.json` is valid
- **Auth fails**: Check Firebase Authentication is enabled
- **Firestore fails**: Check database rules allow write access

