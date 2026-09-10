# Temi Shoe Mart 👟🤖

An autonomous footwear retail and delivery system built for the **Temi Robot**. It connects an on-robot Android customer kiosk with a live store management dashboard powered by **Firebase Realtime Database**, featuring **100% isolated multi-location support across 11 showcase centers in India**.

---

## 📑 Table of Contents

1. [System Architecture & Multi-Location Overview](#1-system-architecture--multi-location-overview)
2. [Prerequisites & Supported Versions](#2-prerequisites--supported-versions)
3. [Temi Robot Setup & Waypoint Names](#3-temi-robot-setup--waypoint-names)
4. [Firebase Realtime Database Setup](#4-firebase-realtime-database-setup)
5. [Building & Installing the APK on Temi Robots](#5-building--installing-the-apk-on-temi-robots)
6. [First-Time Robot Setup (One-Time Location Assignment)](#6-first-time-robot-setup-one-time-location-assignment)
7. [Customer Kiosk Flow (On the Robot)](#7-customer-kiosk-flow-on-the-robot)
8. [Store Admin Monitoring Dashboard (`admin.html`)](#8-store-admin-monitoring-dashboard-adminhtml)
9. [Store Manager PIN Directory](#9-store-manager-pin-directory)
10. [Troubleshooting & FAQs](#10-troubleshooting--faqs)

---

## 1. System Architecture & Multi-Location Overview

The ecosystem consists of two main applications connected in real-time through Firebase:

```
                                  ┌─────────────────────────────────────────┐
                                  │       Firebase Realtime Database        │
                                  ├─────────────────────────────────────────┤
                                  │  /catalog        (Global Shoe Models)   │
                                  │  /store_pins     (Security Passcodes)   │
                                  │  /locations/     (11 Isolated Stores)   │
                                  └─────────────────────────────────────────┘
                                           ▲                       ▲
                        (Isolated Live Sync)│                       │(Isolated Live Sync)
                                           ▼                       ▼
┌──────────────────────────────────────────────────┐      ┌──────────────────────────────────────────┐
│              Temi Android Kiosk App              │      │           Admin Web Dashboard            │
│──────────────────────────────────────────────────│      │──────────────────────────────────────────│
│ • Fullscreen Customer Kiosk                      │      │ • Store Manager Login Portal             │
│ • Real-time Shoe Catalog & Dynamic Vector Colors │      │ • Live Robot Telemetry & Location        │
│ • In-Memory Cart & Atomic Stock Decrement        │      │ • Live Incoming Orders Queue             │
│ • Autonomous Navigation & TTS Voice Guidance     │      │ • Real-Time Inventory & Price Matrix     │
│ • Physical Obstacle Detection & Auto-Recovery    │      │ • Manual Override & Auto-Refund Resets   │
└──────────────────────────────────────────────────┘      └──────────────────────────────────────────┘
```

### 11 Supported Indian Showcase Locations:
* **Bengaluru** (`bengaluru`)
* **Pune** (`pune`)
* **Hyderabad** (`hyderabad`)
* **Chennai** (`chennai`)
* **Chandigarh** (`chandigarh`)
* **Mysuru** (`mysuru`)
* **Thiruvananthapuram** (`trivandrum`)
* **Bhubaneswar** (`bhubaneswar`)
* **Mangalore** (`mangalore`)
* **Indore** (`indore`)
* **Nagpur** (`nagpur`)

---

## 2. Prerequisites & Supported Versions

| Component | Required Version | Description |
| :--- | :--- | :--- |
| **Java Development Kit** | **JDK 11** or **JDK 17** | Required to compile Android Java code |
| **Android Gradle Plugin** | Gradle 8.2+ | Managed automatically via `./gradlew` |
| **Android OS Target** | Android 7.0+ (API 24 to 34) | Standard operating system on Temi Robot |
| **Robotemi SDK** | `com.robotemi:sdk:1.138.0` | Temi hardware communication (Navigation, TTS, Obstacles) |
| **Firebase SDK** | Firebase Android BoM `32.8.0` | Real-time database sync |
| **Web Browser** | Chrome, Edge, Safari, Firefox | For the Admin Dashboard |

---

## 3. Temi Robot Setup & Waypoint Names

Before running the application on the physical robot, Temi must map the store area and have **3 specific waypoint locations** saved in the Temi Settings menu.

### Required Location Names on Temi Map:

> [!IMPORTANT]
> Name these locations **exactly as shown in lowercase** on your Temi map so the robot can navigate automatically:

1. **`stockroom`** — The back-office / shoe storeroom where store staff load ordered shoes into Temi's tray.
2. **`showroom`** — The front retail showroom / pickup area where the customer interacts with Temi and collects their shoes.
3. **`home base`** — The charging dock / staging station where Temi returns when idle.

#### How to Save Waypoints on Temi:
1. Open the Temi top menu ➔ **Locations / Map**.
2. Drive Temi to the stockroom ➔ Tap **Add Location** ➔ Name it **`stockroom`**.
3. Drive Temi to the customer area ➔ Tap **Add Location** ➔ Name it **`showroom`**.
4. Set Temi's charging station as **`home base`**.

---

## 4. Firebase Realtime Database Setup

### Step 1: Import the Seed Database
1. Open the [Firebase Console](https://console.firebase.google.com/) and navigate to your project (**`temi-shoe-mart`**).
2. Go to **Build** ➔ **Realtime Database** ➔ **Data** tab.
3. Click the **three vertical dots (⋮)** in the top-right corner of the data pane.
4. Click **Import JSON**.
5. Select the file from this repository:
   `database/multi-location-seed.json`
6. Click **Import**.

### Step 2: Set Database Rules
In the **Realtime Database** ➔ **Rules** tab, ensure read/write access is active:
```json
{
  "rules": {
    ".read": true,
    ".write": true
  }
}
```

---

## 5. Building & Installing the APK on Temi Robots

### Step 1: Build the APK
Open your terminal in the root project folder and run:
```bash
./gradlew assembleDebug
```
The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

### Step 2: Install onto the Temi Robot

#### Option A: Over Wi-Fi (ADB) — *Recommended*
1. On Temi, open **Settings** ➔ **About** ➔ Find Temi's **IP Address** (e.g. `192.168.1.50`).
2. Ensure your computer and Temi are connected to the same Wi-Fi network.
3. In your computer's terminal, connect and install:
   ```bash
   adb connect 192.168.1.50:5555
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

#### Option B: Via USB Flash Drive
1. Copy `app/build/outputs/apk/debug/app-debug.apk` to a USB drive.
2. Plug the USB drive into Temi's USB port (under the screen / back tray).
3. On Temi, open the **Files** app ➔ Tap `app-debug.apk` ➔ Tap **Install**.

---

## 6. First-Time Robot Setup (One-Time Location Assignment)

1. Launch the **Temi Shoe Mart** app on the robot.
2. Because it is the first launch on a fresh robot, an assignment dialog will appear:
   > **`📍 Setup Robot Location (One-Time)`**  
   > *Please select the showcase location for this Temi robot:*
3. Tap the location where this robot is physically placed (e.g. **Pune Store**, **Bengaluru Store**, or **Hyderabad Store**).
4. **Done!** The robot permanently saves this selection in its internal memory.
   * On all future launches and robot reboots, **this dialog will never show again**.
   * The robot will strictly communicate with that city's isolated database partition.

---

## 7. Customer Kiosk Flow (On the Robot)

```
┌───────────────────────────────┐
│     Welcome Screen (Idle)     │
│   (Customer taps "Start")     │
└──────────────┬────────────────┘
               │ 🗣️ "Hi welcome! Please add items into cart and place order."
               ▼
┌───────────────────────────────┐
│     Product Catalog Grid      │
│   (Filter by Category/Brand)  │
└──────────────┬────────────────┘
               │
               ▼
┌───────────────────────────────┐
│    Shoe Detail & 3D Vector    │
│  (Select Color, Size, & Qty)  │
└──────────────┬────────────────┘
               │
               ▼
┌───────────────────────────────┐
│     Cart & Checkout Screen    │
│  (Tap "Place Order & Deliver")│
└──────────────┬────────────────┘
               │
               ▼
┌───────────────────────────────┐
│ Temi Navigates to Stockroom   │ ──► 🗣️ "Heading to the stock room for loading"
└──────────────┬────────────────┘
               │ (Arrival at Stockroom)
               ▼
┌───────────────────────────────┐
│ Staff Loads Shoes into Tray   │ ──► Staff loads shoes & taps "Shoes Loaded"
└──────────────┬────────────────┘
               │
               ▼
┌───────────────────────────────┐
│ Temi Delivers to Showroom     │ ──► 🗣️ "Shoes have arrived! Please collect your order."
└──────────────┬────────────────┘
               │ (Customer takes shoes & taps "Collect Shoes")
               ▼
┌───────────────────────────────┐
│   Order Complete / Returns    │ ──► Temi resets to Welcome Screen
└───────────────────────────────┘
```

---

## 8. Store Admin Monitoring Dashboard (`admin.html`)

Store managers use the web dashboard to monitor their store's Temi robot in real-time, manage customer orders, and update shoe inventory.

### How to Open the Dashboard:
* **Live Web URL**: **`https://devanshl.github.io/Temi-shoe-mart/`**
* **Local File**: Open `admin.html` directly in any web browser.

---

### Dashboard Features & Operations:

#### 1. Store Authentication
* Select your store location from the dropdown.
* Enter your assigned **4-Digit Store Security PIN** (see directory below).
* Click **Log In to Store Console**.
* The console locks into your store: **`👟 Temi Shoe Store ({City})`**.

#### 2. Orders Queue Tab
* **Live Orders**: Customer orders placed on the in-store robot appear instantly with exact shoe models, colors, sizes, and quantities.
* **Start Round**: Dispatches the robot to the stockroom for loading.
* **Complete Delivery**: Marks the order completed after customer pickup.
* **Cancel & Refund**: Cancels the order and **automatically restores inventory stock counts back to that store's stock matrix**.

#### 3. Inventory Matrix Tab
* Click any shoe model to expand its **Color × Size Stock Grid**.
* **Edit Stock**: Type any number directly into a size box ➔ updates live on the robot's catalog within milliseconds.
* **Edit Price**: Type a new model price ➔ updates live on the robot screen.

#### 4. Add New Shoe Tab
* Add brand new footwear models (Brand, SKU, Model Name, Price, Category, Vector Shape, Colors, and Sizes).

#### 5. Robot Control & Emergency Recovery
* **Real-Time Telemetry Bar**: Shows current Robot Waypoint (`showroom`, `stockroom`), Activity Status (`traveling_storeroom`, `arrived_pickup`), and Active Order ID.
* **Manual Override**: Send Temi to any waypoint manually from the top dropdown.
* **Reset Robot (Idle)**: One-click emergency recovery that clears stuck navigation states and **automatically refunds inventory stock** if an order was cancelled mid-trip.

---

## 9. Store Manager PIN Directory

| Store Location | Login Dropdown Name | 4-Digit Security PIN |
| :--- | :--- | :--- |
| **Pune** | `Pune Store` | **`7821`** |
| **Bengaluru** | `Bengaluru Store` | **`4910`** |
| **Hyderabad** | `Hyderabad Store` | **`9154`** |
| **Chennai** | `Chennai Store` | **`3620`** |
| **Chandigarh** | `Chandigarh Store` | **`8147`** |
| **Mysuru** | `Mysuru Store` | **`5290`** |
| **Thiruvananthapuram** | `Thiruvananthapuram Store` | **`6418`** |
| **Bhubaneswar** | `Bhubaneswar Store` | **`7302`** |
| **Mangalore** | `Mangalore Store` | **`1945`** |
| **Indore** | `Indore Store` | **`8526`** |
| **Nagpur** | `Nagpur Store` | **`4073`** |

> [!NOTE]
> Entering a wrong PIN triggers a red error banner and shakes the card. The PIN input accepts **only 4 numeric digits**.

---

## 10. Troubleshooting & FAQs

### Q1: The robot says "Path Blocked". What should I do?
* **Answer**: An obstacle (person, box, or chair) is in Temi's path. Clear the obstacle and tap the green **"Retry"** button on Temi's screen. Temi will recalculate its route and continue moving.

### Q2: How do I change the store location of a robot after it has already been set up?
* **Answer**: Clear the app's cache and data on the robot:
  1. Open Android **Settings** on Temi ➔ **Apps** ➔ **Temi Shoe Mart**.
  2. Tap **Storage** ➔ **Clear Storage / Clear Data**.
  3. Reopen the app ➔ The one-time location setup dialog will appear again.

### Q3: Does changing stock in Pune affect Bengaluru or Hyderabad?
* **Answer**: No. All 11 locations are **100% isolated**. Modifying stock, placing orders, or moving the robot in Pune only affects Pune's robot and Pune's dashboard.

### Q4: An order was cancelled midway. Did we lose our stock count?
* **Answer**: No. Pressing **Cancel & Refund** or **Reset Robot (Idle)** performs an automatic transaction that refunds the exact ordered quantities back to your store's inventory matrix.

---

## 👨‍💻 Maintainers & Support

* **Repository**: [DevanshL/Temi-shoe-mart](https://github.com/DevanshL/Temi-shoe-mart)
* **Active Feature Branch**: `feature/multi-location`
* **Live Dashboard**: [https://devanshl.github.io/Temi-shoe-mart/](https://devanshl.github.io/Temi-shoe-mart/)
