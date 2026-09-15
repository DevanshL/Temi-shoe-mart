# Temi Shoe Mart 👟🤖
### Multi-Location Autonomous Retail & Delivery Platform

**Temi Shoe Mart** is an autonomous in-store footwear retail and robotic fulfillment platform engineered for the **Temi Robot**. It couples an on-robot Android customer kiosk with a centralized, real-time store management console powered by **Firebase Realtime Database**, featuring **complete multi-location store isolation across 8 showcase centers in India**.

---

## 📑 Table of Contents

1. [Architecture & Cloud Infrastructure Overview](#1-architecture--cloud-infrastructure-overview)
2. [Hardware & Software Specifications](#2-hardware--software-specifications)
3. [Physical Robot Waypoint Setup (On-Site Mapping)](#3-physical-robot-waypoint-setup-on-site-mapping)
4. [Temi Robot Application Deployment (ADB over Wi-Fi)](#4-temi-robot-application-deployment-adb-over-wi-fi)
5. [First-Boot Store Assignment](#5-first-boot-store-assignment)
6. [Autonomous In-Store Customer & Delivery Flow](#6-autonomous-in-store-customer--delivery-flow)
7. [Store Manager Web Console Guide (`admin.html`)](#7-store-manager-web-console-guide-adminhtml)
8. [Store Manager PIN Directory (8 Locations)](#8-store-manager-pin-directory-8-locations)
9. [Data Isolation & Concurrency Architecture](#9-data-isolation--concurrency-architecture)
10. [Field Operations & Troubleshooting Runbook](#10-field-operations--troubleshooting-runbook)

---

## 1. Architecture & Cloud Infrastructure Overview

The system runs on a **centralized single-backend multi-tenant architecture**. All 8 regional showcase stores connect to the same central Firebase cloud instance, while maintaining strict, automated data isolation:

```
                                  ┌──────────────────────────────────────────────┐
                                  │      Centralized Firebase Realtime DB        │
                                  │           (Single Cloud Instance)            │
                                  ├──────────────────────────────────────────────┤
                                  │  • /catalog     (Shared Global Products)     │
                                  │  • /store_pins  (Central Authentication)     │
                                  │  • /locations/  (11 Isolated Store Branches) │
                                  └──────────────────────────────────────────────┘
                                           ▲                            ▲
                        (Isolated Live Sync)│                            │(Isolated Live Sync)
                                           ▼                            ▼
┌────────────────────────────────────────────────────────┐   ┌──────────────────────────────────────────┐
│                 Temi Robot Kiosk App                   │   │          Store Manager Console           │
│────────────────────────────────────────────────────────│   │──────────────────────────────────────────│
│ • Fullscreen Customer Retail Kiosk                     │   │ • Secure Store Manager Portal            │
│ • Dynamic Vector Color Layering & Live Catalog         │   │ • Real-Time Robot Telemetry & Waypoints  │
│ • Atomic In-Store Stock Validation & Instant Ordering  │   │ • Live Order Queue & Dispatching         │
│ • Autonomous Waypoint Navigation & TTS Voice Guidance  │   │ • Real-Time Color × Size Inventory Matrix│
│ • Real-Time Obstacle Avoidance & Path Recovery         │   │ • One-Click Recovery & Emergency Stop    │
└────────────────────────────────────────────────────────┘   └──────────────────────────────────────────┘
```

> [!NOTE]
> **Cloud Setup Status: Completed Centrally**  
> The Firebase Realtime Database and seed data (`database/multi-location-seed.json`) are **already deployed and managed centrally**.  
> **Regional store installers and field staff do NOT need to configure Firebase, create accounts, or import database files.** Local teams only need to follow Sections 3, 4, and 5 below.

### Supported Showcase Centers (8 Active Locations):
| City ID | Location Name | City ID | Location Name |
| :--- | :--- | :--- | :--- |
| `bengaluru` | Bengaluru Store (Working Hub) | `hyd_sez` | Hyd Sez Store |
| `mysore` | Mysore Store | `tvm` | TVM Store |
| `chennai_sholinganallur` | Chennai - Shollinganallur Store | `pune` | Pune Store |
| `chennai_mcity` | Chennai - Mcity Store | `noida` | Noida Store |

---

## 2. Hardware & Software Specifications

| Parameter | Specification | Notes |
| :--- | :--- | :--- |
| **Target Hardware** | Temi Autonomous Robot | Standard display, lidar, 3D depth sensors, and delivery tray |
| **Operating System** | Android 7.0+ (API level 24 to 34) | Compatible with all commercial Temi platform releases |
| **Language & Toolchain** | Java 11 / JDK 17, Gradle 8.2+ | Automated build via `./gradlew` wrapper |
| **Robot SDK** | Robotemi SDK (`com.robotemi:sdk:1.138.0`) | Autonomous navigation, TTS announcements, battery telemetry |
| **Cloud Synchronization** | Firebase Android BoM `32.8.0` | Real-time WebSocket connection with offline queuing |
| **Web Console** | Modern HTML5 / Vanilla ES6 / CSS3 | Zero-dependency, responsive glassmorphism UI |

---

## 3. Physical Robot Waypoint Setup (On-Site Mapping)

Before running the application, each regional Temi robot must have its physical retail floor mapped using Temi's native mapping system.

### Required Waypoint Names:

> [!IMPORTANT]
> The robot's automated state machine relies on **exact case-sensitive string matching**. Ensure all 3 waypoints are saved in **exact lowercase**:

1. **`stockroom`** — The back-office / inventory staging area where staff place shoes into Temi's tray.
2. **`showroom`** — The front customer greeting area where customers browse the catalog and collect deliveries.
3. **`home base`** — The docking station where Temi charges when idle.

#### On-Site Waypoint Setup Procedure:
1. On Temi's display, pull down the top menu ➔ Tap **Locations / Map**.
2. Drive Temi manually to the stockroom ➔ Tap **Add Location** ➔ Name it **`stockroom`**.
3. Drive Temi manually to the customer greeting area ➔ Tap **Add Location** ➔ Name it **`showroom`**.
4. Confirm Temi's charging station is registered as **`home base`**.

---

## 4. Temi Robot Application Deployment (ADB over Wi-Fi)

Deployment to the robot is performed wirelessly over the local network using the Android Debug Bridge (ADB).

### Step 1: Compile the APK
From the project root directory, run:
```bash
./gradlew assembleDebug
```
The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

### Step 2: Wireless Installation to Temi

1. Connect your computer to the **same Wi-Fi network** as the Temi robot.
2. On Temi, open **Settings** ➔ **About** ➔ Note the robot's **IP Address** (e.g., `192.168.1.105`).
3. In your terminal, run the following commands to connect and install:
   ```bash
   # Connect to Temi wirelessly
   adb connect 192.168.1.105:5555

   # Install the application (with automatic replace/re-install)
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 5. First-Boot Store Assignment

1. On the Temi robot, open the **Temi Shoe Mart** app from the app launcher.
2. On the initial launch, the one-time configuration modal will be presented:
   > **`📍 Setup Robot Location (One-Time)`**  
   > *Please select the showcase location for this Temi robot:*
3. Tap your physical store location (e.g., **Hyderabad Store**, **Bengaluru Store**, or **Pune Store**).
4. **Setup is complete.**
   * The app permanently saves the store ID in Android secure `SharedPreferences` (`temi_kiosk_prefs`).
   * This dialog will **never appear again** on subsequent app launches, customer interactions, or robot reboots.
   * The robot is now permanently bound to its local store inventory, orders queue, and telemetry.

---

## 6. Autonomous In-Store Customer & Delivery Flow

```
┌───────────────────────────────────────┐
│        Welcome Screen (Idle)          │
│   (Customer taps "Start Ordering")    │
└──────────────────┬────────────────────┘
                   │ 🗣️ "Hi welcome! Please add items into cart and place order."
                   ▼
┌───────────────────────────────────────┐
│         Product Catalog Grid          │
│  (Filter by Brand & Category)         │
└──────────────────┬────────────────────┘
                   │
                   ▼
┌───────────────────────────────────────┐
│     Shoe Detail & Vector Preview      │
│  (Select Color, Size, & Quantity)     │
└──────────────────┬────────────────────┘
                   │
                   ▼
┌───────────────────────────────────────┐
│       Cart & Order Submission         │
│  (Atomic Stock Validation in Cloud)   │
└──────────────────┬────────────────────┘
                   │
                   ▼
┌───────────────────────────────────────┐
│    Temi Dispatches to Stockroom       │ ──► 🗣️ "Heading to the stock room for loading"
└──────────────────┬────────────────────┘
                   │ (Arrives at 'stockroom')
                   ▼
┌───────────────────────────────────────┐
│    Staff Loading Confirmation         │ ──► Staff places shoes on tray & taps "Shoes Loaded"
└──────────────────┬────────────────────┘
                   │
                   ▼
┌───────────────────────────────────────┐
│    Temi Delivers to Showroom Area     │ ──► 🗣️ "Shoes have arrived! Please collect your order."
└──────────────────┬────────────────────┘
                   │ (Customer collects shoes & taps "Collect Shoes")
                   ▼
┌───────────────────────────────────────┐
│   Order Completed & Auto-Reset        │ ──► Robot returns to Welcome Screen ready for next customer
└───────────────────────────────────────┘
```

---

## 7. Store Manager Web Console Guide (`admin.html`)

Store managers monitor robot movement, dispatch orders, and manage inventory via the live web console.

### Web Console URL:
* **Live Production Link**: **`https://devanshl.github.io/Temi-shoe-mart/`**

---

### Key Operational Modules:

#### 1. Secure Store Login
* Select your store location from the dropdown.
* Enter your location's **4-digit numeric PIN**.
* Once authenticated, the dashboard locks into your specific store view (**`👟 Temi Shoe Store ({City})`**). Cross-store dropdowns are omitted to prevent accidental cross-store actions.

#### 2. Live Orders Queue
* **Incoming Stream**: Orders placed by customers on Temi appear in real time with model names, selected colors, sizes, and quantities.
* **Start Round**: Dispatches Temi from the showroom to the stockroom.
* **Complete Delivery**: Finalizes the order after customer pickup.
* **Cancel & Refund**: Cancels the order and **automatically executes an atomic stock refund back into this store's inventory matrix**.

#### 3. Real-Time Inventory & Price Matrix
* Click any shoe model to expand its **Color × Size matrix**.
* **Edit Stock**: Update stock counts directly in any size box ➔ synchronizes to the Temi robot screen in milliseconds.
* **Edit Price**: Update shoe pricing ➔ reflects in real time across customer screens.

#### 4. Add New Shoe Model
* Add new footwear models globally (Brand, SKU, Model Name, Base Price, Category, 3D Vector SVG Shape, Colors, and Sizes).

#### 5. Robot Telemetry & Emergency Controls
* **Live Telemetry Bar**: Displays real-time waypoint (`showroom`, `stockroom`, `none`), robot state (`idle`, `moving`), battery level, and active order ID.
* **Manual Dispatch**: Dispatch Temi directly to any mapped waypoint (`stockroom`, `showroom`, `home base`).
* **Reset Robot (Idle)**: Emergency reset that unblocks stuck navigation states and automatically restores inventory if an order was active.

---

## 8. Store Manager PIN Directory (8 Locations)

| Location Name | Login Selection | 4-Digit Security PIN |
| :--- | :--- | :--- |
| **Bengaluru** | `Bengaluru Store (Working Hub)` | **`4910`** |
| **Mysore** | `Mysore Store` | **`5290`** |
| **Chennai - Shollinganallur** | `Chennai - Shollinganallur Store` | **`3620`** |
| **Chennai - Mcity** | `Chennai - Mcity Store` | **`3621`** |
| **Hyd Sez** | `Hyd Sez Store` | **`9154`** |
| **TVM** | `TVM Store` | **`6418`** |
| **Pune** | `Pune Store` | **`7821`** |
| **Noida** | `Noida Store` | **`2013`** |

---

## 9. Data Isolation & Concurrency Architecture

```
Firebase Realtime Database
├── catalog/                     <-- Global catalog (models, prices, vector assets)
├── store_pins/                  <-- Central security PIN registry
│
└── locations/
    ├── pune/                    <-- Isolated Pune partition
    │   ├── orders/              <-- Pune store orders
    │   ├── stock/               <-- Pune inventory counts
    │   ├── location             <-- Real-time Pune Temi waypoint
    │   └── status               <-- Real-time Pune Temi state
    │
    ├── bengaluru/               <-- Isolated Bengaluru partition
    │   ├── orders/
    │   └── stock/
    │
    └── hyderabad/...            <-- Fully independent partitions for all 11 cities
```

* **Zero Cross-Store Data Bleed**: All Firebase operations (`ValueEventListener`, queries, transactions, writes) are strictly scoped under `locations/{storeLocationId}/...`.
* **Atomic Concurrency**: Inventory decrements and order cancellations use atomic Firebase transactions (`runTransaction`) to prevent race conditions during concurrent customer checkouts.
* **Listener Lifecycle Management**: Logging out or switching stores tears down active WebSocket listeners to prevent background memory overhead and lingering connections.

---

## 10. Field Operations & Troubleshooting Runbook

### Q1: The robot announces "Path Blocked" and halts.
* **Root Cause**: An obstacle (person, cart, or object) is detected in Temi's safety LiDAR/3D sensor envelope.
* **Resolution**: Clear the obstruction in front of Temi and tap the green **"Retry"** button on the screen. Temi will re-plan its trajectory and continue autonomously.

### Q2: How do I reassign a robot to a different store if the wrong city was selected on first boot?
* **Resolution**: Clear application data on the Temi tablet:
  1. Open Android **Settings** ➔ **Apps & Notifications** ➔ **Temi Shoe Mart**.
  2. Tap **Storage & Cache** ➔ **Clear Storage / Clear Data**.
  3. Reopen **Temi Shoe Mart** ➔ The one-time store selection dialog will reappear.

### Q3: What happens to inventory if an order is cancelled or the robot is reset?
* **Resolution**: The system executes an automatic transactional stock refund. The exact quantities of each size and color ordered are restored to the store's inventory matrix in real time.

---

## 👨‍💻 Repository & System Links

* **Repository**: [DevanshL/Temi-shoe-mart](https://github.com/DevanshL/Temi-shoe-mart)
* **Branch**: `feature/multi-location`
* **Live Manager Console**: [https://devanshl.github.io/Temi-shoe-mart/](https://devanshl.github.io/Temi-shoe-mart/)

