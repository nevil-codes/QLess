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

## Step 6: Firestore Security Rules (for Production)

Replace test rules with:
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

## Step 7: Google Sign-In Setup (Optional)

1. Copy your **Web Client ID** from Firebase Console:
   - Go to Authentication > Sign-in method > Google
   - Copy the Web client ID

2. Update `SignupActivity.java` and `LoginActivity.java` with the client ID

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

