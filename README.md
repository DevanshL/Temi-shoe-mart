# Temi Shoe Mart 👟🤖

An autonomous footwear retail and delivery assistant built for the **Temi Robot**. The system combines an on-robot Android kiosk interface, real-time multi-angle shoe visualization, atomic inventory management, and a live web administration dashboard powered by **Firebase Realtime Database**.

---

## 📌 Project Overview

Temi Shoe Mart transforms the Temi robot into an autonomous in-store retail assistant. Customers interact directly with Temi's touch screen to browse shoes, customize colors and sizes with dynamic layered vector previews, and place orders. Temi autonomously navigates to the stockroom for staff loading and delivers the shoes directly to the showroom pickup area.

---

## 🏗️ System Architecture

```
                                  +---------------------------------------+
                                  |       Firebase Realtime Database      |
                                  |---------------------------------------|
                                  |  /catalog         /orders             |
                                  |  /location        /status             |
                                  |  /robot_state     /active_order_id    |
                                  +---------------------------------------+
                                           ▲                     ▲
                                           │ (Live Sync)         │ (Live Sync)
                                           ▼                     ▼
+----------------------------------------------------+  +------------------------------------+
|               Temi Android Kiosk App               |  |        Admin Web Dashboard         |
|----------------------------------------------------|  |------------------------------------|
| - Kiosk Navigation & Lifecycle (MainActivity)      |  | - admin.html                       |
| - Product Catalog & Filtering (ShoeCatalogActivity)|  | - Live Robot State & Location      |
| - Dynamic Multi-Angle Preview (ShoeDetailActivity) |  | - Incoming Order Queue             |
| - Cart & Atomic Order Checkout (CartActivity)      |  | - Inventory Stock Control          |
| - Temi Hardware SDK (Movement, TTS, Obstacles)     |  | - Manual Navigation Override       |
+----------------------------------------------------+  +------------------------------------+
```

---

## 📂 Codebase Structure

```
Temi-shoe-mart/
├── admin.html                              # Web-based Admin Dashboard for store managers
├── database/
│   └── catalog-seed.json                   # Initial catalog and inventory seed data
├── app/
│   ├── build.gradle.kts                    # App-level build configuration and dependencies
│   ├── google-services.json                # Firebase configuration file
│   └── src/main/
│       ├── AndroidManifest.xml             # Android manifest (Kiosk & orientation rules)
│       ├── java/com/infy/temiapplication/
│       │   ├── MainActivity.java           # Main kiosk screen & Temi robot navigation driver
│       │   ├── catalog/
│       │   │   ├── ShoeCatalogActivity.java# Grid product catalog browser
│       │   │   ├── ShoeCatalogAdapter.java # Catalog grid RecyclerView adapter
│       │   │   ├── ShoeDetailActivity.java # Multi-angle preview, color/size selector
│       │   │   ├── CartActivity.java       # Cart checkout & transaction submission
│       │   │   └── CartAdapter.java        # Cart item RecyclerView adapter
│       │   ├── data/
│       │   │   ├── FirebaseRepo.java       # Central repository (Firebase sync & mock mode)
│       │   │   └── CartSession.java        # In-memory thread-safe cart session
│       │   └── model/
│       │       ├── Shoe.java               # Shoe product model
│       │       └── CartItem.java           # Cart item & variant model
│       └── res/
│           ├── drawable/                   # Vector assets for multi-angle shoe rendering
│           ├── layout/                     # Activity & item XML layout definitions
│           └── values/                     # Colors, strings, themes, and dimensions
├── build.gradle.kts                        # Root project build configuration
└── settings.gradle.kts                     # Gradle repository & module settings
```

---

## ⚙️ Tech Stack & Versions

| Component | Technology / Library | Version |
| :--- | :--- | :--- |
| **Platform** | Android OS | Android 7.0+ (API 24 to 34) |
| **Language** | Java | Java 11 (JDK 11) |
| **Robot SDK** | Robotemi Android SDK (`com.robotemi:sdk`) | `1.138.0` |
| **Database** | Firebase Realtime Database (via BoM) | `32.8.0` |
| **Image & UI** | Glide (`com.github.bumptech.glide`) | `4.16.0` |
| **UI Components** | AndroidX Material Components | `1.11.0` |
| **Web Dashboard** | HTML5, CSS3 Glassmorphism, Vanilla JS | Firebase JS SDK `10.8.0` (compat) |

---

## 🔄 Delivery Lifecycle & State Machine

```
[ Customer Welcome Screen ]
            │
            ▼ (Customer taps "Start Ordering")
[ Catalog / Detail Screen / Cart Checkout ]
            │
            ▼ (Atomic Stock Check & Order Submission)
[ traveling_storeroom ] ──► Temi moves to "stockroom" (TTS Announcement)
            │
            ▼ (Temi arrives at stockroom)
[ arrived_storeroom ]   ──► Displays items to load on screen
            │
            ▼ (Staff presses "Shoes Loaded" on robot or admin dashboard)
[ traveling_pickup ]    ──► Temi moves to "showroom"
            │
            ▼ (Temi arrives at showroom)
[ arrived_pickup ]      ──► Displays items to collect
            │
            ▼ (Customer taps "Collect Shoes")
[ Check Battery ]
      ├── Battery <= 30% ──► [ returning_home ] (Temi goes to "home base" charger)
      └── Battery > 30%  ──► [ idle ] (Temi stays at "showroom" ready for next user)
```

### Safety & Obstacle Handling
- If Temi encounters an obstacle or aborted path, the status transitions to `blocked`.
- Temi announces: *"Excuse me, my path is blocked. Please clear the way."*
- A **"Retry"** button appears on screen to resume navigation once the path is clear.

---

## 🎨 Key Features Developed

### 1. Dynamic Vector Shoe Rendering
- Shoes are rendered using layered vector drawables (`shoe_*_fill.xml` and `shoe_*_details.xml`).
- Supports multi-angle inspection (**Side View**, **Top View**, **Sole View**).
- Dynamic programmatic color tinting using exact hex codes defined in the catalog.
- Supported silhouettes: `sneaker_low`, `sneaker_high`, `sneaker_sport`, `boot`, `sandal`, `formal`.

### 2. Live Inventory & Atomic Transactions
- Stock is tracked per unique variant (`{color}_{size}`).
- Orders are processed using Firebase Database transactions on `/catalog` to prevent overselling or race conditions.
- If an item is out of stock, the UI disables the variant and shows remaining quantities in real time.

### 3. Session Security & Inactivity Reset
- Automatically resets the active customer session back to the welcome screen after 60 seconds of inactivity to protect customer privacy and prepare the kiosk for the next shopper.

### 4. Admin Web Dashboard (`admin.html`)
- Live connection indicator for the robot.
- Real-time order monitoring with item breakdown.
- Stock adjustment tools to restock individual sizes and colors.
- Manual location override controls (`stockroom`, `showroom`, `home base`).

---

## 🚀 Setup & Deployment Guide

### Prerequisites
1. **Android Studio** (Hedgehog / Iguana / Jellyfish or newer).
2. **JDK 11** configured in Gradle.
3. **Temi Robot** connected to the same local network or registered with Firebase.
4. **Firebase Project** with Realtime Database enabled.

### Database Setup
1. Open the [Firebase Console](https://console.firebase.google.com/).
2. Create a Realtime Database in test mode or with appropriate read/write rules.
3. Import the `database/catalog-seed.json` file to initialize default products and robot state nodes.
4. Place your `google-services.json` file inside the `app/` directory.

### Building & Running the Android App
1. Open the project in Android Studio.
2. Ensure Temi robot developer mode / ADB over TCP is enabled, or connect via USB.
3. Build and run the app on Temi:
   ```bash
   ./gradlew installDebug
   ```
4. On first launch, grant Kiosk and location permissions.

### Running the Admin Dashboard
1. Open `admin.html` in any modern web browser.
2. Ensure your Firebase configuration keys inside `admin.html` match your Firebase project settings.

---

## 🗺️ Temi Location Waypoints Required

Make sure the following location names are saved in the Temi Robot's map settings:
- **`stockroom`**: The inventory staging area where staff loads shoes.
- **`showroom`**: The customer-facing pickup and shopping area.
- **`home base`**: The charging dock.
