# QLess 🛒

<p align="center">
  <img src="app/src/main/res/drawable/ic_logo_minimal.xml" width="120" alt="QLess Logo">
</p>

<p align="center">
  <b>Compare. Reserve. Collect.</b>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#how-it-works">How It Works</a> •
  <a href="#screenshots">Screenshots</a> •
  <a href="#tech-stack">Tech Stack</a> •
  <a href="#getting-started">Getting Started</a>
</p>

---

## 🎯 About

**QLess** is a smart shopping app that revolutionizes how you shop. Compare prices across multiple stores, reserve items instantly, and pick them up at your convenience — all from your phone.

No more wandering through crowded stores or missing out on the best deals. QLess brings the best prices to you and holds your items until you're ready to collect.

## ✨ Features

### 🔍 Compare
Find the best prices across different stores instantly. Looking for 5kg rice? See that Store A has it for €12 while Store B offers it at €9 — all in one glance.

### 📱 Reserve & Pay
Found the best deal? Reserve it right from the app. Pay securely and the store will set your item aside — no more "sorry, we just sold out."

### 📦 Collect
Pick up your reserved items within 48 hours at your convenience. Skip the queues, grab your stuff, and go!

### 🤖 Smart Recommendations
Our ML-powered engine learns what you like. Get personalized suggestions based on your browsing and buying patterns.

## 🔄 How It Works

```
1. 🔍 SEARCH    →  Find products you need
2. 📊 COMPARE   →  See prices across all partner stores  
3. 💳 PAY       →  Secure in-app payment
4. ⏰ RESERVE   →  Store holds your item for 48 hours
5. 🚶 COLLECT   →  Pick up at your convenience
```

> **Note:** If items aren't collected within 48 hours, a 10% restocking fee applies and the remainder is refunded.

## 📱 Screenshots

| Welcome Screen |
|:--------------:|
| ![Welcome](docs/screenshots/welcome.png) |

*More screenshots coming soon as we build out the app!*

## 🛠 Tech Stack

- **Language:** Java 11
- **UI:** XML Layouts with Material Design 3
- **Architecture:** Android Jetpack
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 36
- **Build System:** Gradle 9.1 with Version Catalog

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
│   │   │   └── MainActivity.java
│   │   ├── res/
│   │   │   ├── drawable/          # Icons & backgrounds
│   │   │   ├── layout/            # XML layouts
│   │   │   ├── values/            # Colors, strings, themes
│   │   │   └── values-night/      # Dark theme
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── gradle/
│   └── libs.versions.toml         # Dependency versions
└── build.gradle
```

## 🗺 Roadmap

- [x] Welcome screen with branding
- [ ] User authentication (Login/Register)
- [ ] Product browsing & search
- [ ] Store listings with price comparison
- [ ] Shopping cart & checkout
- [ ] Order history & tracking
- [ ] ML-powered recommendations
- [ ] Push notifications
- [ ] Store partner dashboard

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


