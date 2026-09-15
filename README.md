# Temi Shoe Mart 👟🤖
### Multi-Location Autonomous Retail & Robotic Fulfillment Platform

**Temi Shoe Mart** is an enterprise-grade autonomous in-store footwear retail and robotic fulfillment platform engineered for the **Temi Robot**. It bridges an on-robot Android customer kiosk with a centralized, real-time store management console powered by **Firebase Realtime Database**, featuring **complete multi-location store isolation across 8 showcase centers in India**.

---

## 📑 Table of Contents

1. [Architecture & Cloud Infrastructure](#1-architecture--cloud-infrastructure)
2. [Hardware & Software Specifications](#2-hardware--software-specifications)
3. [Physical Robot Waypoint Setup (On-Site Mapping)](#3-physical-robot-waypoint-setup-on-site-mapping)
4. [Deployment & Installation (Android Studio & ADB)](#4-deployment--installation-android-studio--adb)
5. [Robot Store Assignment & Secret Staff Switcher](#5-robot-store-assignment--secret-staff-switcher)
6. [Autonomous Customer & Delivery Flow](#6-autonomous-customer--delivery-flow)
7. [Store Manager Web Console Guide (`admin.html`)](#7-store-manager-web-console-guide-adminhtml)
8. [Store Manager Security PIN Directory (8 Locations)](#8-store-manager-security-pin-directory-8-locations)
9. [Edge Cases, Error Handling & Recovery Architecture](#9-edge-cases-error-handling--recovery-architecture)
10. [Data Isolation & Concurrency Architecture](#10-data-isolation--concurrency-architecture)
11. [Field Operations & Troubleshooting Runbook](#11-field-operations--troubleshooting-runbook)

---

## 1. Architecture & Cloud Infrastructure

The platform operates on a **centralized single-backend multi-tenant architecture**. All 8 regional showcase stores connect to the same central Firebase cloud instance while maintaining strict, real-time data isolation:

```
                                  ┌──────────────────────────────────────────────┐
                                  │      Centralized Firebase Realtime DB        │
                                  │           (Single Cloud Instance)            │
                                  ├──────────────────────────────────────────────┤
                                  │  • /catalog     (Shared Global Products)     │
                                  │  • /store_pins  (Central Authentication)     │
                                  │  • /locations/  (8 Isolated Store Branches)  │
                                  └──────────────────────────────────────────────┘
                                           ▲                            ▲
                        (Isolated Live Sync)│                            │(Isolated Live Sync)
                                           ▼                            ▼
┌────────────────────────────────────────────────────────┐   ┌──────────────────────────────────────────┐
│                 Temi Robot Kiosk App                   │   │          Store Manager Console           │
│────────────────────────────────────────────────────────│   │──────────────────────────────────────────│
│ • Fullscreen Customer Retail Kiosk                     │   │ • PIN-Protected Store Manager Portal     │
│ • Dynamic Vector Color Layering & Live Catalog         │   │ • Real-Time Robot Telemetry & Waypoints  │
│ • Atomic In-Store Stock Validation & Instant Ordering  │   │ • In-Transit Lockout & Station Cancel    │
│ • Autonomous Waypoint Navigation & TTS Voice Guidance  │   │ • Real-Time Color × Size Inventory Matrix│
│ • Obstacle Detection & Self-Healing Path Recovery      │   │ • Dynamic Variant Creation (+Color/+Size)│
│ • Secret In-App Staff PIN Store Switcher               │   │ • In-Place Reset & Manual Dispatch       │
└────────────────────────────────────────────────────────┘   └──────────────────────────────────────────┘
```

> [!NOTE]
> **Central Cloud Status: Fully Deployed**  
> The Firebase Realtime Database schema and catalog seed are managed centrally. Local store staff and installers **do NOT need to configure Firebase, create accounts, or import database files**. Local teams only need to follow Sections 3, 4, and 5.

### Supported Showcase Centers (8 Active Locations)
| Store Location | Database Node Key | Display Name in App & Admin | Default PIN |
| :--- | :--- | :--- | :--- |
| **Bengaluru** | `bengaluru` | `Bengaluru Store` | `4910` |
| **Mysore** | `mysore` | `Mysore Store` | `5290` |
| **Chennai - Sholinganallur** | `chennai_sholinganallur` | `Chennai - Sholinganallur Store` | `3620` |
| **Chennai - Mcity** | `chennai_mcity` | `Chennai - Mcity Store` | `3621` |
| **Hyd Sez** | `hyd_sez` | `Hyd Sez Store` | `9154` |
| **TVM** | `tvm` | `TVM Store` | `6418` |
| **Pune** | `pune` | `Pune Store` | `7821` |
| **Noida** | `noida` | `Noida Store` | `2013` |

---

## 2. Hardware & Software Specifications

| Parameter | Specification | Notes |
| :--- | :--- | :--- |
| **Target Hardware** | Temi Commercial Robotics Platform | Integrated display, LiDAR navigation, 3D depth sensors, microphone array, delivery tray |
| **Operating System** | Android 7.0+ (API level 24 to 34) | Compatible with all commercial Temi robot OS releases |
| **Language & Toolchain** | Java 11 / JDK 17, Gradle 8.2+ | Automated build via `./gradlew` wrapper |
| **Robot SDK** | Robotemi SDK (`com.robotemi:sdk:1.138.0`) | Autonomous ROS navigation, TTS voice synthesis, battery telemetry |
| **Cloud Synchronization** | Firebase Realtime Database BoM `32.8.0` | Low-latency WebSockets with atomic transactions |
| **Web Console** | HTML5 / Vanilla ES6 / Glassmorphic CSS3 | Zero-dependency, responsive across mobile, tablet, and desktop |

---

## 3. Physical Robot Waypoint Setup (On-Site Mapping)

Before running the app, each regional Temi robot must have its physical retail floor mapped using Temi's native mapping tool.

### Required Waypoint Names

> [!IMPORTANT]
> The automated state machine relies on **exact case-sensitive string matching**. Ensure all 3 waypoints are saved in **exact lowercase**:

1. **`stockroom`** — The inventory staging area where back-office staff place shoes into Temi's tray.
2. **`showroom`** — The customer greeting and ordering area where customers collect their orders.
3. **`home base`** — The docking station where Temi charges when idle or on low battery.

#### On-Site Waypoint Setup Procedure:
1. On Temi's top bar, swipe down ➔ Tap **Locations / Map**.
2. Drive Temi manually to the stockroom ➔ Tap **Add Location** ➔ Name it **`stockroom`**.
3. Drive Temi manually to the customer greeting area ➔ Tap **Add Location** ➔ Name it **`showroom`**.
4. Confirm Temi's charging station is saved as **`home base`**.

---

## 4. Deployment & Installation (Android Studio & ADB)

### Method A: Direct Run via Android Studio (Recommended for Developers)

1. Open the project root folder in **Android Studio**.
2. Connect your laptop to the **same Wi-Fi network** as the Temi robot.
3. Obtain Temi's IP address: On Temi, open **Settings** ➔ **About** (e.g., `192.168.1.105`).
4. In Android Studio's bottom **Terminal** tab, run:
   ```bash
   adb connect 192.168.1.105:5555
   ```
5. Confirm connection: Android Studio's target device dropdown (top toolbar) will show `Temi - Android SDK`.
6. Click the green **Play / Run Button (▶)** (or press `Shift + F10`). Android Studio will automatically compile, install, and launch the kiosk application on Temi.

---

### Method B: Wireless Deployment via Terminal (ADB)

1. Connect your computer to the **same Wi-Fi network** as Temi.
2. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
   *Generated output:* `app/build/outputs/apk/debug/app-debug.apk`
3. Connect and install to Temi over Wi-Fi:
   ```bash
   # Connect to Temi's IP on port 5555
   adb connect 192.168.1.105:5555

   # Install APK with replacement flag (-r)
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 5. Robot Store Assignment & Secret Staff Switcher

### 1. Initial Setup (First Boot)
When the app is opened for the first time on a freshly installed Temi:
1. The **"📍 Initial Setup: Select Store"** modal appears.
2. Select your store location (e.g., **Pune Store**).
3. A security prompt will ask for the **4-digit Security PIN** (e.g., `7821` for Pune).
4. Enter the PIN and tap **Authorize**:
   - The store assignment is permanently saved to Android `SharedPreferences` (`temi_kiosk_prefs`).
   - The robot connects to its isolated database partition (`locations/pune/`).
   - The welcome screen subtitle updates to: `📍 Pune Store • Touch screen to start`.
   - The dialog will **never appear again** on standard reboots or app launches.

### 2. Secret In-App Staff Store Switcher
If a robot is reassigned to another store or the wrong store was selected by accident:
- **No need to uninstall or clear app data!**
- **Action**: **Long-press the "Temi Shoe Mart" brand title** on the welcome screen for 2 seconds.
- The **"🔒 Staff Menu: Switch Store Location"** modal will appear.
- Select the new store, enter that store's 4-digit PIN, and tap **Authorize**.
- The robot immediately rebinds its Firebase listeners and telemetry to the newly selected store.

---

## 6. Autonomous Customer & Delivery Flow

```
┌────────────────────────────────────────────────────────┐
│               Welcome Screen (Idle)                    │
│          (Customer taps "Start Ordering")              │
└──────────────────────────┬─────────────────────────────┘
                           │ 🗣️ "Hi welcome! Please add items into cart and place order."
                           ▼
┌────────────────────────────────────────────────────────┐
│                 Product Catalog Grid                   │
│   (Filter by Brand, Category, & Live Store Stock)      │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│             Shoe Detail & Interactive View             │
│   • 3 Angles: Side / Top / Sole Vector Previews        │
│   • Dynamic Color Swatches & Size Chips                │
│   • Real-Time Stock Warning & Quantity Selector        │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│               Cart & Checkout Screen                   │
│   (Atomic Cloud Stock Check: Deducts Stock at Store)   │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│            Temi Dispatches to Stockroom                │ ──► 🗣️ "Heading to the stock room for loading"
└──────────────────────────┬─────────────────────────────┘
                           │ (Arrives at 'stockroom')
                           ▼
┌────────────────────────────────────────────────────────┐
│            Staff Loading Confirmation                  │ ──► Shows "Items to Load" preview
│     (Staff loads shoes onto tray & taps "Shoes Loaded")│ ──► 🗣️ "Temi has arrived at the stock room.
└──────────────────────────┬─────────────────────────────┘        Please load the shoes and tap Shoes Loaded."
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│            Temi Delivers to Showroom Area              │ ──► 🗣️ "Shoes loaded! Temi is traveling to the showroom"
└──────────────────────────┬─────────────────────────────┘
                           │ (Arrives at 'showroom')
                           ▼
┌────────────────────────────────────────────────────────┐
│            Customer Collection Confirmation            │ ──► Shows "Items to Collect" preview
│     (Customer collects shoes & taps "Collect Shoes")   │ ──► 🗣️ "Your shoes have arrived! Please collect
└──────────────────────────┬─────────────────────────────┘        your order and tap Collect Shoes."
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│               Order Completion & Reset                 │ ──► 🗣️ "Thank you for shopping with us! Have a great day."
│  • 1.8s audio delay ensures TTS is never cut off       │ ──► Battery Check:
│  • Status resets to "idle"                             │     - If ≤ 30% ➔ Navigates to "home base" dock
│  • Returns to Welcome Screen for next customer         │     - If > 30% ➔ Parks at "showroom"
└────────────────────────────────────────────────────────┘
```

---

## 7. Store Manager Web Console Guide (`admin.html`)

The Store Manager Web Console gives staff complete real-time oversight of robot telemetry, inventory matrices, order queues, and manual navigation.

* **Live Deployment URL**: **`https://devanshl.github.io/Temi-shoe-mart/admin.html`**

---

### Key Console Capabilities

#### 1. PIN-Protected Location Login
- Select your store location and enter the 4-digit PIN.
- The console locks into that store's isolated namespace (`locations/{storeLocation}/`), preventing cross-store data leakage.

#### 2. Real-Time Telemetry Bar
- **Temi Status**: Live status (`Idle`, `Heading to Stockroom`, `At Stockroom (Loading)`, `En Route to Showroom`, `Path Blocked`, `Manual Override`).
- **State**: `idle`, `moving`, or `blocked`.
- **Location**: Current waypoint (`Showroom`, `Stockroom`, `Charging Dock`).
- **Active Order**: Displays active Order ID or `-`.

#### 3. Manual Controls & In-Place Reset
- **Manual Dispatch (`Send Temi to [stockroom | showroom | home base] ➔ Go`)**:
  - Commands Temi to drive directly to any mapped station.
  - Upon arrival, Temi announces arrival, sets state to `idle`, and **stays parked at that station** until given the next instruction.
- **`Reset (idle)` Button**:
  - **Emergency Stop & Reset**: Sets robot state to `idle` in place without forcing an unwanted trip.
  - If an active order was in progress, it cancels the order, **automatically refunds all shoe quantities back into stock**, and clears the active order ID.

#### 4. Active Orders & Order History
- **Live Order Card**: Displays customer order details, item names, color variants, sizes, quantities, and status badge (`⚡ Order Placed`, `📦 At Stockroom`, `🚚 En Route`).
- **In-Transit Lockout**: When Temi is actively driving, the Cancel button displays `⏳ In Transit (Moving)` and is disabled to prevent conflicting navigation commands while moving.
- **Station Cancellation**: When Temi is stationary at the stockroom, showroom, or blocked, clicking **Cancel** refunds inventory and resets the order safely.
- **Order History**: Logs past completed (`✅ Completed`) and cancelled (`❌ Cancelled`) orders with timestamps.

#### 5. Dynamic Inventory Matrix & Variant Creation
- **Expandable Color × Size Matrix**: Click any shoe model header to open its stock grid.
- **Direct Stock & Price Editing**: Change stock numbers or shoe prices inline — updates sync to Temi's screen in real time.
- **`🎨 + Add Color` Feature**:
  - Allows staff to add a brand new color variant to an existing shoe model.
  - Supports custom color names, hex codes, automatic CSS color-name resolution, visual color picker, and initial stock quantities.
- **`📏 + Add Size` Feature**:
  - Allows staff to add a new shoe size to an existing model.
  - Automatically sorts sizes numerically and initializes stock across all existing colors.
- **`Delete Model`**: Deletes a shoe model from the store catalog with a custom confirmation modal.
- **`Add New Shoe Model`**: Global form to add a completely new footwear SKU with category, vector shape set, price, colors, and sizes.

---

## 8. Store Manager Security PIN Directory (8 Locations)

| Location Name | Store Key | Console & Kiosk Security PIN |
| :--- | :--- | :--- |
| **Bengaluru Store** | `bengaluru` | **`4910`** |
| **Mysore Store** | `mysore` | **`5290`** |
| **Chennai - Shollinganallur Store** | `chennai_sholinganallur` | **`3620`** |
| **Chennai - Mcity Store** | `chennai_mcity` | **`3621`** |
| **Hyd Sez Store** | `hyd_sez` | **`9154`** |
| **TVM Store** | `tvm` | **`6418`** |
| **Pune Store** | `pune` | **`7821`** |
| **Noida Store** | `noida` | **`2013`** |

---

## 9. Edge Cases, Error Handling & Recovery Architecture

The platform has been hardened against real-world retail edge cases:

### 1. Obstacle Detection & Self-Healing Retry (`status: "blocked"`)
- **What Happens**: When an obstacle is detected in Temi's path, the SDK returns `abort` or `reject`.
- **Robot Action**: Temi halts immediately, announces *"Excuse me, my path is blocked. Please clear the way."*, updates Firebase to `status: "blocked"`, and displays the **"Path Blocked"** screen with a green **"Retry"** button.
- **Recovery**: When the path is cleared and staff/customer taps **"Retry"**:
  - Temi inspects `targetLocationBeforeBlock`.
  - It resumes its exact intended destination (whether delivering an active order, returning to showroom, or navigating under manual dispatch to the stockroom or dock).

### 2. In-Transit Cancellation Protection
- **Problem Prevented**: Cancelling an order while Temi is moving at full speed causes conflicting ROS path recalculations and robot confusion.
- **Solution**: The Admin Console disables the Cancel button while `robot_state === 'moving'`, unlocking it only when Temi halts at a station (`stockroom`, `showroom`, or `blocked`).

### 3. Order Cancelled While at Stockroom
- If an order is cancelled while Temi is at the stockroom, `active_order_id` is wiped clean.
- Temi detects that it is at `stockroom` with no active order, announces *"No active order. Returning to showroom."*, and autonomously drives back to the Showroom.

### 4. Speech Cut-Off & Duplicate Speech Elimination
- **Completion Speech Protection**: When a customer taps "Collect Shoes", a 1.8s delay ensures the speech *"Thank you for shopping with us! Have a great day."* finishes audibly before the activity transitions.
- **Duplicate Speech Elimination**: Static `lastSpokenStatus` guards prevent repetitive TTS triggers caused by rapid Firebase value events.

### 5. Manual Dispatch Parking vs Auto-Return
- When an admin dispatches Temi to the `stockroom` or `home base`, the `isManualOverrideActive` flag ensures Temi stays parked at that station upon arrival, rather than incorrectly assuming a cancelled order and returning to the showroom.

---

## 10. Data Isolation & Concurrency Architecture

```
Firebase Realtime Database
├── catalog/                     <-- Global shoe models, vector shapes, base prices
├── store_pins/                  <-- Central store security PIN registry
│
└── locations/
    ├── pune/                    <-- Isolated Pune store branch
    │   ├── orders/              <-- Pune active & completed orders
    │   ├── stock/               <-- Pune shoe stock counts (shoeId / color_size)
    │   ├── location             <-- Real-time waypoint ("showroom", "stockroom", "home base")
    │   ├── status               <-- Real-time status ("idle", "traveling_storeroom", "blocked", etc.)
    │   ├── robot_state          <-- Real-time robot state ("idle", "moving", "blocked")
    │   └── active_order_id      <-- Active order ID currently being fulfilled
    │
    ├── bengaluru/               <-- Isolated Bengaluru branch
    │   ├── orders/
    │   └── stock/
    │
    └── ...                      <-- Fully isolated branches for all 8 centers
```

- **Atomic Transactions (`runTransaction`)**: Inventory deductions and refunds use atomic Firebase transactions to eliminate race conditions during concurrent checkouts.
- **WebSocket Listener Scoping**: The robot app and admin console bind strictly to `locations/{storeLocationId}/`, eliminating cross-store data leakage.

---

## 11. Field Operations & Troubleshooting Runbook

### Q1: How do I change the store location on Temi if the wrong store was selected?
- **Fast In-App Method**: Long-press the **"Temi Shoe Mart"** brand title on the welcome screen for 2 seconds. Select the correct store and enter its 4-digit PIN.
- **Alternative Method**: Open Android **Settings** ➔ **Apps** ➔ **Temi Shoe Mart** ➔ **Storage** ➔ **Clear Storage**. Reopen the app and select the store.

### Q2: Temi is stopped in the hallway and displays "Path Blocked".
- Check for obstacles, people, or closed doors in Temi's path.
- Clear the hallway and tap **"Retry"** on Temi's screen. Temi will resume navigation to its intended destination.

### Q3: How do I manually send Temi back to the charging dock?
- In [`admin.html`](https://devanshl.github.io/Temi-shoe-mart/admin.html), select **Send Temi to** ➔ **`home base`** ➔ Click **Go**.
- Alternatively, if Temi finishes an order with battery $\le 30\%$, it automatically docks itself.

### Q4: An order was placed by mistake. How do we cancel and restore stock?
- In [`admin.html`](https://devanshl.github.io/Temi-shoe-mart/admin.html), click **Cancel** on the active order card or click **Reset (idle)**.
- The order status will change to `cancelled`, and all shoe quantities will be instantly refunded to the store's inventory matrix.

---

## 👨‍💻 Repository & Resource Links

* **Repository**: [DevanshL/Temi-shoe-mart](https://github.com/DevanshL/Temi-shoe-mart)
* **Active Branch**: `feature/multi-location`
* **Live Manager Console**: [https://devanshl.github.io/Temi-shoe-mart/admin.html](https://devanshl.github.io/Temi-shoe-mart/admin.html)
* **Local APK Path**: `app/build/outputs/apk/debug/app-debug.apk`
