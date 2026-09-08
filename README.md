# Temi Shoe Mart 👟🤖

An autonomous footwear retail and delivery assistant built for the **Temi Robot**. The system combines an on-robot Android kiosk interface, real-time multi-angle shoe visualization, atomic inventory management, and a live web administration dashboard powered by **Firebase Realtime Database**.

---

## 📌 Project Overview

**Temi Shoe Mart** transforms the Temi robot into an autonomous in-store retail assistant. Customers interact directly with Temi's touch screen to browse shoes, customize colors and sizes with dynamic layered vector previews, and place orders. Temi autonomously navigates to the stockroom for staff loading and delivers the shoes directly to the showroom pickup area.

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
│               Temi Android Kiosk App               │  │        Admin Web Dashboard         │
│----------------------------------------------------│  │------------------------------------│
│ - Kiosk Navigation & Lifecycle (MainActivity)      │  │ - admin.html                       │
│ - Product Catalog & Filtering (ShoeCatalogActivity)│  │ - Live Robot State & Location      │
│ - Dynamic Multi-Angle Preview (ShoeDetailActivity) │  │ - Incoming Order Queue             │
│ - Cart & Atomic Order Checkout (CartActivity)      │  │ - Inventory Stock Control          │
│ - Temi Hardware SDK (Movement, TTS, Obstacles)     │  │ - Manual Navigation Override       │
+----------------------------------------------------+  +------------------------------------+
```

---

## 📂 Codebase Structure

```
Temi-shoe-mart/
├── admin.html                              # Web-based Admin Dashboard for store managers
├── database/
│   └── catalog-seed.json                   # Initial catalog, stock, and robot state seed data
├── app/
│   ├── build.gradle.kts                    # App-level build configuration and dependencies
│   ├── google-services.json                # Firebase configuration file (optional for mock mode)
│   └── src/main/
│       ├── AndroidManifest.xml             # Android manifest (Kiosk, fullscreen, permissions)
│       ├── java/com/infy/temiapplication/
│       │   ├── MainActivity.java           # Main kiosk screen & Temi robot navigation driver
│       │   ├── catalog/
│       │   │   ├── ShoeCatalogActivity.java# Grid product catalog browser with category filters
│       │   │   ├── ShoeCatalogAdapter.java # Catalog grid RecyclerView adapter
│       │   │   ├── ShoeDetailActivity.java # Multi-angle preview, color/size selector, live stock
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

| Component | Technology / Library | Version | Description |
| :--- | :--- | :--- | :--- |
| **Platform** | Android OS | Android 7.0+ (API 24 to 34) | Target runtime for Temi Robot OS |
| **Language** | Java | Java 11 (JDK 11) | Core application logic |
| **Robot SDK** | Robotemi Android SDK (`com.robotemi:sdk`) | `1.138.0` | Navigation, TTS speech, battery monitoring |
| **Database** | Firebase Realtime Database (via BoM) | `32.8.0` | Real-time live synchronization & atomic transactions |
| **Image & UI** | Glide (`com.github.bumptech.glide`) | `4.16.0` | High-performance image loading |
| **UI Components** | AndroidX Material Components | `1.11.0` | Modern UI chips, buttons, and layouts |
| **Web Dashboard** | HTML5, CSS3 Glassmorphism, Vanilla JS | Firebase JS SDK `10.8.0` (compat) | Browser-based store manager control panel |

---

## 🔄 Delivery Lifecycle & State Machine

```
[ Customer Welcome Screen (MainActivity) ]
                   │
                   ▼ (Customer taps "Start Ordering")
[ Catalog / Detail Screen / Cart Checkout ]
                   │
                   ▼ (Atomic Stock Check & Order Submission)
[ traveling_storeroom ] ──► Temi moves to "stockroom" (TTS Announcement)
                   │
                   ▼ (Temi arrives at stockroom)
[ arrived_storeroom ]   ──► Displays items to load on screen for stockroom staff
                   │
                   ▼ (Staff presses "Shoes Loaded" on robot or Admin Web Dashboard)
[ traveling_pickup ]    ──► Temi moves to "showroom"
                   │
                   ▼ (Temi arrives at showroom)
[ arrived_pickup ]      ──► Displays items to collect for customer
                   │
                   ▼ (Customer taps "Collect Shoes")
[ Check Battery ]
       ├── Battery <= 30% ──► [ returning_home ] (Temi goes to "home base" charger)
       └── Battery > 30%  ──► [ idle ] (Temi stays at "showroom" ready for next user)
```

### Safety & Obstacle Handling
- If Temi encounters an obstacle or aborted path (`OnGoToLocationStatusChangedListener.ABORT`), the status transitions to `blocked`.
- Temi announces: *"Excuse me, my path is blocked. Please clear the way."*
- A **"Retry"** button appears on screen to resume navigation once the path is clear.

---

## 🎨 Key Features Developed

### 1. Dynamic Vector Shoe Rendering
- Shoes are rendered using layered vector drawables (`shoe_{silhouette}_{angle}_fill.xml` and `shoe_{silhouette}_{angle}_details.xml`).
- **3 Multi-Angle Views**:
  - **Side View** (`side`): Full profile view of the shoe.
  - **Top View** (`top`): Overhead view showing laces, tongue, and collar.
  - **Sole View** (`sole`): Underside tread and grip pattern.
- **Dynamic Programmatic Tinting**: The fill vector layer is dynamically tinted using `PorterDuff.Mode.SRC_IN` with exact hex colors defined in the catalog, while the detail layer maintains line work and shading.
- **Supported Silhouettes**: `sneaker_low`, `sneaker_high`, `sneaker_sport`, `boot`, `sandal`, `formal`.

### 2. Live Inventory & Atomic Transactions
- Stock is tracked per unique variant key (`{color}_{size}`).
- Orders are processed using Firebase Database transactions on `/catalog` to prevent overselling or race conditions.
- Real-time stock counts update automatically across the catalog, detail view, and admin dashboard.
- Out-of-stock sizes and colors are automatically disabled with visual cues.

### 3. Session Security & Inactivity Reset
- Automatically resets the active customer session back to the welcome screen after **60 seconds of inactivity** in `ShoeDetailActivity`, `ShoeCatalogActivity`, and `CartActivity` to protect customer privacy and prepare the kiosk for the next shopper.

### 4. Admin Web Dashboard (`admin.html`)
- **Robot Telemetry**: Live connection status, current location, movement status, and active order ID.
- **Live Order Feed**: Real-time incoming order queue with itemized breakdown.
- **Inventory Control**: Live stock adjustments per size and color with instant synchronization.
- **Manual Navigation Overrides**: Remotely dispatch Temi to `stockroom`, `showroom`, or `home base`.

### 5. Offline & Emulator Mock Mode
- `FirebaseRepo` features built-in fallback simulation.
- If `google-services.json` is missing or when running on an emulator without a physical robot, the app automatically runs in mock simulation mode with automatic step-by-step trip progression.

---

## 📊 Firebase Realtime Database Schema

```json
{
  "location": "showroom",
  "status": "idle",
  "robot_state": "idle",
  "active_order_id": "ord_1741454000000",
  "admin": {
    "notification_pending": false,
    "latest_order_id": "ord_1741454000000"
  },
  "catalog": {
    "air_runner_2": {
      "name": "Air Runner 2",
      "brand": "Nova",
      "category": "Athletic & Basketball",
      "shapeSet": "sneaker_low",
      "price": 89.99,
      "colors": ["black", "white", "coral", "blue"],
      "colorHex": {
        "black": "#2C2C2A",
        "white": "#E8E8E8",
        "coral": "#D85A30",
        "blue": "#378ADD"
      },
      "sizes": [7, 8, 9, 10, 11],
      "stock": {
        "black_7": 4,
        "black_8": 0,
        "black_9": 3
      }
    }
  },
  "orders": {
    "ord_1741454000000": {
      "order_id": "ord_1741454000000",
      "timestamp": 1741454000000,
      "status": "traveling_storeroom",
      "total_price": 89.99,
      "items": [
        {
          "shoe_id": "air_runner_2",
          "name": "Air Runner 2",
          "brand": "Nova",
          "shape_set": "sneaker_low",
          "color": "coral",
          "color_hex": "#D85A30",
          "size": 9,
          "quantity": 1,
          "price": 89.99
        }
      ]
    }
  }
}
```

---

## 🚀 Setup & Deployment Guide

### Prerequisites
1. **Android Studio** (Hedgehog, Iguana, Jellyfish, Ladybug, or newer).
2. **JDK 11** configured in Gradle.
3. **Temi Robot** (or standard Android device / emulator for mock mode).
4. **Firebase Project** with Realtime Database enabled.

### 1. Database Setup
1. Open the [Firebase Console](https://console.firebase.google.com/).
2. Create a Realtime Database in test mode or with appropriate security rules.
3. Import the `database/catalog-seed.json` file to initialize default products and robot state nodes.
4. Download your `google-services.json` file and place it inside the `app/` directory.

### 2. Building & Running the Android App
1. Open the project in Android Studio.
2. Connect to the Temi robot via USB or ADB over Wi-Fi:
   ```bash
   adb connect <TEMI_IP_ADDRESS>:5555
   ```
3. Build and install the app on Temi:
   ```bash
   ./gradlew installDebug
   ```
4. On first launch, grant Kiosk and location permissions.

### 3. Running the Admin Dashboard
1. Open `admin.html` in any modern web browser.
2. Ensure your Firebase configuration keys inside `admin.html` match your Firebase project credentials.

---

## 🗺️ Temi Location Waypoints Required

Make sure the following location names are saved in the Temi Robot's map settings:
- **`stockroom`**: The inventory staging area where staff loads shoes.
- **`showroom`**: The customer-facing pickup and shopping area.
- **`home base`**: The charging dock.

---

## 🔒 Firebase Security Rules (Recommended)

```json
{
  "rules": {
    ".read": true,
    ".write": true
  }
}
```
*(For production deployments, restrict write rules to authenticated users or robot service accounts).*
