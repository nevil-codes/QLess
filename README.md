# QLess 🛒

<p align="center">
  <b>Compare. Reserve. Collect.</b>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#how-it-works">How It Works</a> •
  <a href="#tech-stack">Tech Stack</a> •
  <a href="#getting-started">Getting Started</a> •
  <a href="#admin-panel">Admin Panel</a>
</p>

---

## 🎯 About

**QLess** is a smart shopping app that revolutionizes how you shop. Compare prices across multiple stores, reserve items instantly, and pick them up at your convenience — all from your phone.

No more wandering through crowded stores or missing out on the best deals. QLess brings the best prices to you and holds your items until you're ready to collect.

## ✨ Features

### 📱 Mobile App
| Feature | Description |
|---------|-------------|
| 🔍 **Price Comparison** | Compare prices across multiple stores in real-time |
| 📦 **Reserve & Pay** | Reserve items and pay at pickup, or pay in the app |
| 📍 **Location Picker** | Set your location manually or use GPS |
| 🛒 **Smart Cart** | Add items from different stores |
| 📋 **Order History** | Track all your completed orders |
| ⏰ **Reservations** | Manage active and past reservations |
| 🤖 **ML Recommendations** | Get personalized product suggestions |
| 🔐 **Secure Auth** | Email/password authentication |
| ⚙️ **Settings** | Notifications, language, account management |
| ❓ **Help & Support** | FAQ, email and phone support |

### 💻 Admin Panel
| Feature | Description |
|---------|-------------|
| 📊 **Dashboard** | Overview of products, stores, users, orders |
| 📦 **Product Management** | Add/edit products with images, prices, categories |
| 🏪 **Store Management** | Add/edit stores with address autocomplete |
| 📋 **Order Management** | View and manage reservations |
| 👥 **User Management** | View registered users |
| 📈 **ML Analytics** | Track user behavior and trends |

## 🔄 How It Works

```
1. 🔍 SEARCH    →  Find products you need
2. 📊 COMPARE   →  See prices across all partner stores  
3. 🛒 ADD       →  Add to cart from best-priced store
4. ✅ CHECKOUT  →  Confirm your reservation
5. ⏰ PICK UP   →  Within 12h (pay at pickup) or 48h (paid in the app)
6. 🎉 COLLECT   →  Show reservation, pay, and go!
```

> **Note:** Reserved stock is held for you. Uncollected pay-at-pickup reservations count as a missed pickup, and 3 missed pickups means future orders must be paid in advance. Uncollected prepaid reservations are refunded minus a 10% restocking fee.

## 🛠 Tech Stack

### Mobile App
| Technology | Purpose |
|------------|---------|
| Java 11 | Primary language |
| XML Layouts | UI design |
| Material Design 3 | Modern UI components |
| Firebase Auth | User authentication |
| Cloud Firestore | Database |
| Firebase Storage | Image storage |
| Google Play Services | Location services |
| Glide | Image loading |

### Admin Panel
| Technology | Purpose |
|------------|---------|
| HTML5/CSS3 | Structure & styling |
| JavaScript | Logic |
| Firebase SDK | Backend integration |
| OpenStreetMap Nominatim | Address geocoding |

### Build & Tools
| Tool | Version |
|------|---------|
| Android Studio | Latest |
| Gradle | 9.1.0 |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 |

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog or newer
- JDK 17+
- Android SDK 36

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/niks1107/QLess.git
   cd QLess
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

3. **Build the project**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Run on device/emulator**
   - Connect your Android device or start an emulator
   - Click the "Run" button in Android Studio

## 📁 Project Structure

```
QLess/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/qless/
│   │   │   ├── MainActivity.java       # Welcome screen
│   │   │   ├── LoginActivity.java      # Login
│   │   │   ├── SignupActivity.java     # Registration
│   │   │   ├── HomeActivity.java       # Main home screen
│   │   │   ├── SearchActivity.java     # Product search
│   │   │   ├── ProductDetailActivity.java  # Product details
│   │   │   ├── CartActivity.java       # Shopping cart
│   │   │   ├── CheckoutActivity.java   # Checkout flow
│   │   │   ├── OrdersActivity.java     # Order history
│   │   │   ├── ReservationsActivity.java   # Reservations
│   │   │   ├── ProfileActivity.java    # User profile
│   │   │   ├── SettingsActivity.java   # App settings
│   │   │   ├── HelpActivity.java       # Help & FAQ
│   │   │   ├── LocationPickerActivity.java # Location selector
│   │   │   ├── CartManager.java        # Cart singleton
│   │   │   └── RecommendationEngine.java   # ML recommendations
│   │   ├── res/
│   │   │   ├── drawable/          # Icons & backgrounds
│   │   │   ├── layout/            # XML layouts
│   │   │   ├── values/            # Colors, strings, themes
│   │   │   └── values-night/      # Dark theme
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── admin/                         # Admin web panel
│   ├── index.html
│   ├── app.js
│   └── style.css
├── gradle/
│   └── libs.versions.toml         # Dependency versions
├── FIREBASE_SETUP.md              # Firebase setup guide
├── FIRESTORE_RULES.md             # Firestore security rules
└── AGENTS.md                      # Project documentation
```

## ✅ Completed Features

- [x] Welcome screen with branding
- [x] User authentication (Email/Password)
- [x] User registration with validation
- [x] Password strength indicator
- [x] Password reset via email
- [x] Home screen with categories
- [x] Product browsing & search
- [x] Store listings with location
- [x] Price comparison across stores
- [x] Product detail view
- [x] Shopping cart
- [x] Checkout & reservation system
- [x] Order history
- [x] Reservations management (active/past)
- [x] User profile
- [x] Settings (notifications, language, etc.)
- [x] Help & Support with FAQ
- [x] Location picker (GPS + manual)
- [x] ML-powered recommendations
- [x] Admin panel (products, stores, orders, analytics)
- [x] Responsive admin dashboard

## 🚀 Future Enhancements

- [ ] Push notifications
- [ ] In-app payments integration
- [ ] QR code for pickup verification
- [ ] Store partner mobile app
- [ ] Loyalty/rewards program
- [ ] Social sharing
- [ ] Multi-language support
- [ ] Dark mode

## 💻 Admin Panel

The admin panel is located in the `/admin` folder. To use it:

1. Open `admin/index.html` in a web browser
2. Login with your Firebase credentials
3. Manage products, stores, orders, and view analytics

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the project
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 📞 Contact

Have questions or suggestions? We'd love to hear from you!

- **Email:** hello@qless.app
- **Twitter:** [@QLessApp](https://twitter.com/QLessApp)

---

<p align="center">
  Made with ❤️ for smart shoppers everywhere
</p>


