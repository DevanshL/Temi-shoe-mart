package com.infy.temiapplication.data;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.infy.temiapplication.model.CartItem;
import com.infy.temiapplication.model.Shoe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirebaseRepo {
    private static final String TAG = "FirebaseRepo";
    private static FirebaseRepo instance;

    // Toggle this to enable actual Firebase Realtime Database
    private boolean useFirebase = false;

    private DatabaseReference dbRef;
    private List<Shoe> localCatalog = new ArrayList<>();
    private final List<RobotStateCallback> robotStateCallbacks = new ArrayList<>();
    
    // In-memory states for offline mocking
    private String robotLocation = "none";
    private String robotStatus = "idle";
    private String robotState = "idle";
    private String activeOrderId = "";
    private List<CartItem> mockActiveOrderItems = new ArrayList<>();
    private final Handler mockHandler = new Handler(Looper.getMainLooper());

    public interface CatalogCallback {
        void onCatalogLoaded(List<Shoe> catalog);
        void onError(Exception e);
    }

    public interface OrderCallback {
        void onOrderSuccess(String orderId);
        void onOrderFailed(String reason);
    }

    public interface RobotStateCallback {
        void onStateChanged(String location, String status, String robotState, String activeOrderId);
    }

    public interface StockCallback {
        void onStockUpdated(boolean success, int newStock);
    }

    public interface OrderDetailsCallback {
        void onOrderDetailsLoaded(List<CartItem> items);
        void onError(Exception e);
    }

    private FirebaseRepo() {
        try {
            dbRef = FirebaseDatabase.getInstance().getReference();
        } catch (Exception e) {
            Log.w(TAG, "Firebase not initialized. Defaulting to Local/Mock mode.");
            useFirebase = false;
        }
        initLocalCatalog();
    }

    public static synchronized FirebaseRepo getInstance() {
        if (instance == null) {
            instance = new FirebaseRepo();
        }
        return instance;
    }

    public void setUseFirebase(boolean useFirebase) {
        this.useFirebase = useFirebase;
        if (useFirebase && dbRef == null) {
            try {
                dbRef = FirebaseDatabase.getInstance().getReference();
            } catch (Exception e) {
                Log.e(TAG, "Could not initialize Firebase Reference. Falling back to local.", e);
                this.useFirebase = false;
            }
        }
    }

    public boolean isUsingFirebase() {
        return useFirebase;
    }

    // --- Catalog Loading ---
    public void getCatalog(final CatalogCallback callback) {
        if (!useFirebase) {
            // Return copy of local in-memory catalog
            callback.onCatalogLoaded(new ArrayList<>(localCatalog));
            return;
        }

        dbRef.child("catalog").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Shoe> list = new ArrayList<>();
                for (DataSnapshot itemSnap : snapshot.getChildren()) {
                    Shoe shoe = itemSnap.getValue(Shoe.class);
                    if (shoe != null) {
                        shoe.setId(itemSnap.getKey());
                        list.add(shoe);
                    }
                }
                callback.onCatalogLoaded(list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.toException());
            }
        });
    }

    // --- Order Placement with Real-Time Stock Verification ---
    public void submitOrder(final List<CartItem> items, final OrderCallback callback) {
        if (items == null || items.isEmpty()) {
            callback.onOrderFailed("Cart is empty");
            return;
        }

        if (!useFirebase) {
            submitOrderLocal(items, callback);
            return;
        }

        // Run multi-item stock check and ordering inside a transaction to prevent race conditions
        final String orderId = "ORD_" + System.currentTimeMillis();
        
        // Dynamic stock checking transaction
        dbRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                // Verify all items are in stock
                for (CartItem item : items) {
                    String stockPath = "catalog/" + item.getShoeId() + "/stock/" + item.getColor().toLowerCase() + "_" + item.getSize();
                    MutableData stockVal = currentData.child(stockPath);
                    
                    Integer currentStock = stockVal.getValue(Integer.class);
                    if (currentStock == null) {
                        return Transaction.abort(); // Catalog item not found
                    }
                    if (currentStock < item.getQty()) {
                        return Transaction.abort(); // Insufficient stock
                    }
                }

                // If check passes, deduct stock
                for (CartItem item : items) {
                    String stockPath = "catalog/" + item.getShoeId() + "/stock/" + item.getColor().toLowerCase() + "_" + item.getSize();
                    MutableData stockVal = currentData.child(stockPath);
                    Integer currentStock = stockVal.getValue(Integer.class);
                    stockVal.setValue(currentStock - item.getQty());
                }

                // Write order entry
                String orderPath = "orders/" + orderId;
                MutableData orderData = currentData.child(orderPath);
                
                Map<String, Object> orderMap = new HashMap<>();
                List<Map<String, Object>> itemMaps = new ArrayList<>();
                for (CartItem item : items) {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("shoeId", item.getShoeId());
                    itemMap.put("name", item.getShoeName());
                    itemMap.put("color", item.getColor());
                    itemMap.put("size", item.getSize());
                    itemMap.put("qty", item.getQty());
                    itemMap.put("price", item.getPrice());
                    itemMaps.add(itemMap);
                }
                orderMap.put("items", itemMaps);
                orderMap.put("status", "pending");
                orderMap.put("createdAt", System.currentTimeMillis());
                orderData.setValue(orderMap);

                // Update active states
                currentData.child("active_order_id").setValue(orderId);
                currentData.child("admin/notification_pending").setValue(true);
                currentData.child("admin/latest_order_id").setValue(orderId);

                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (committed && error == null) {
                    callback.onOrderSuccess(orderId);
                } else {
                    String reason = error != null ? error.getMessage() : "Insufficient stock for one or more items.";
                    callback.onOrderFailed(reason);
                }
            }
        });
    }

    private void submitOrderLocal(List<CartItem> items, OrderCallback callback) {
        // Verify and deduct local stock
        for (CartItem item : items) {
            Shoe matchedShoe = findLocalShoe(item.getShoeId());
            if (matchedShoe == null) {
                callback.onOrderFailed("Shoe not found: " + item.getShoeName());
                return;
            }
            int availableStock = matchedShoe.getStockFor(item.getColor(), item.getSize());
            if (availableStock < item.getQty()) {
                callback.onOrderFailed("Insufficient stock for " + item.getShoeName() + " (" + item.getColor() + ", size " + item.getSize() + "). Only " + availableStock + " left!");
                return;
            }
        }

        // Deduct
        for (CartItem item : items) {
            Shoe matchedShoe = findLocalShoe(item.getShoeId());
            if (matchedShoe != null) {
                String key = item.getColor().toLowerCase() + "_" + item.getSize();
                int currentStock = matchedShoe.getStock().get(key);
                matchedShoe.getStock().put(key, currentStock - item.getQty());
            }
        }

        final String orderId = "MOCK_ORD_" + System.currentTimeMillis();
        activeOrderId = orderId;
        mockActiveOrderItems = new ArrayList<>(items);
        
        boolean isRobot = false;
        try {
            com.robotemi.sdk.Robot.getInstance();
            isRobot = true;
        } catch (Exception ignored) {}

        if (isRobot) {
            // On physical Temi: set status immediately and let physical movements drive transitions
            robotLocation = "stockroom";
            robotStatus = "traveling_storeroom";
            robotState = "moving";
            callback.onOrderSuccess(orderId);
        } else {
            // On emulator: run simulator timers
            callback.onOrderSuccess(orderId);
            triggerMockRobotTrip(orderId);
        }
    }

    public void getActiveOrderItems(String orderId, final OrderDetailsCallback callback) {
        if (!useFirebase) {
            callback.onOrderDetailsLoaded(new ArrayList<>(mockActiveOrderItems));
            return;
        }

        dbRef.child("orders").child(orderId).child("items").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<CartItem> list = new ArrayList<>();
                for (DataSnapshot itemSnap : snapshot.getChildren()) {
                    CartItem item = itemSnap.getValue(CartItem.class);
                    if (item != null) {
                        list.add(item);
                    }
                }
                callback.onOrderDetailsLoaded(list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.toException());
            }
        });
    }

    // --- Robot State Observation ---
    public void observeRobotState(final RobotStateCallback callback) {
        if (callback == null) return;
        robotStateCallbacks.add(callback);
        
        // Initial callback invocation
        callback.onStateChanged(robotLocation, robotStatus, robotState, activeOrderId);

        if (!useFirebase) return;

        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String loc = snapshot.child("location").getValue(String.class);
                String stat = snapshot.child("status").getValue(String.class);
                String rState = snapshot.child("robot_state").getValue(String.class);
                String activeOrd = snapshot.child("active_order_id").getValue(String.class);

                robotLocation = loc != null ? loc : "none";
                robotStatus = stat != null ? stat : "idle";
                robotState = rState != null ? rState : "idle";
                activeOrderId = activeOrd != null ? activeOrd : "";

                notifyRobotCallbacks();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error listening to robot state changes", error.toException());
            }
        });
    }

    public void removeRobotStateCallback(RobotStateCallback callback) {
        robotStateCallbacks.remove(callback);
    }

    public void updateRobotStateInDatabase(String location, String status, String state, String activeOrdId) {
        this.robotLocation = location;
        this.robotStatus = status;
        this.robotState = state;
        this.activeOrderId = activeOrdId;

        notifyRobotCallbacks();

        if (useFirebase) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("location", location);
            updates.put("status", status);
            updates.put("robot_state", state);
            updates.put("active_order_id", activeOrdId);
            dbRef.updateChildren(updates);
        }
    }

    private void notifyRobotCallbacks() {
        for (RobotStateCallback cb : robotStateCallbacks) {
            cb.onStateChanged(robotLocation, robotStatus, robotState, activeOrderId);
        }
    }

    // --- Mock offline robot cycle ---
    private void triggerMockRobotTrip(final String orderId) {
        // Step 1: Heading to Store room
        mockHandler.postDelayed(() -> {
            updateRobotStateInDatabase("store_room", "traveling_storeroom", "moving", orderId);
            
            // Step 2: Arrived at store room (Wait for load)
            mockHandler.postDelayed(() -> {
                updateRobotStateInDatabase("store_room", "arrived_storeroom", "arrived_store_room", orderId);
                
                // Normally admin panel triggers departure, we'll auto-simulate it after 5 seconds of loading
                mockHandler.postDelayed(() -> {
                    updateRobotStateInDatabase("pickup_zone", "traveling_pickup", "moving", orderId);
                    
                    // Step 3: Arrived at pickup zone
                    mockHandler.postDelayed(() -> {
                        updateRobotStateInDatabase("pickup_zone", "arrived_pickup", "arrived_pickup_zone", orderId);
                    }, 5000);
                    
                }, 6000);
                
            }, 5000);
            
        }, 2000);
    }

    private Shoe findLocalShoe(String shoeId) {
        for (Shoe s : localCatalog) {
            if (s.getId().equals(shoeId)) {
                return s;
            }
        }
        return null;
    }

    // Seed local cache matching catalog-seed.json
    private void initLocalCatalog() {
        // Shoe 1: Air Runner 2
        List<String> colors1 = new ArrayList<>();
        colors1.add("black"); colors1.add("white"); colors1.add("coral"); colors1.add("blue");
        Map<String, String> cHex1 = new HashMap<>();
        cHex1.put("black", "#2C2C2A"); cHex1.put("white", "#E8E8E8"); cHex1.put("coral", "#D85A30"); cHex1.put("blue", "#378ADD");
        List<Integer> sizes1 = new ArrayList<>();
        sizes1.add(7); sizes1.add(8); sizes1.add(9); sizes1.add(10); sizes1.add(11);
        Map<String, Integer> stock1 = new HashMap<>();
        stock1.put("black_7", 4); stock1.put("black_8", 0); stock1.put("black_9", 3); stock1.put("black_10", 5); stock1.put("black_11", 2);
        stock1.put("white_7", 0); stock1.put("white_8", 3); stock1.put("white_9", 4); stock1.put("white_10", 0); stock1.put("white_11", 1);
        stock1.put("coral_7", 2); stock1.put("coral_8", 4); stock1.put("coral_9", 0); stock1.put("coral_10", 2); stock1.put("coral_11", 3);
        stock1.put("blue_7", 1); stock1.put("blue_8", 2); stock1.put("blue_9", 6); stock1.put("blue_10", 4); stock1.put("blue_11", 0);
        localCatalog.add(new Shoe("air_runner_2", "Air Runner 2", "Nova", "Athletic & Basketball", "sneaker_low", colors1, cHex1, sizes1, stock1, 89.99));

        // Shoe 2: Rugged Hiker X
        List<String> colors2 = new ArrayList<>();
        colors2.add("wheat"); colors2.add("black"); colors2.add("rust_brown");
        Map<String, String> cHex2 = new HashMap<>();
        cHex2.put("wheat", "#D2B48C"); cHex2.put("black", "#202020"); cHex2.put("rust_brown", "#8B4513");
        List<Integer> sizes2 = new ArrayList<>();
        sizes2.add(8); sizes2.add(9); sizes2.add(10); sizes2.add(11); sizes2.add(12);
        Map<String, Integer> stock2 = new HashMap<>();
        stock2.put("wheat_8", 3); stock2.put("wheat_9", 0); stock2.put("wheat_10", 5); stock2.put("wheat_11", 2); stock2.put("wheat_12", 1);
        stock2.put("black_8", 2); stock2.put("black_9", 4); stock2.put("black_10", 1); stock2.put("black_11", 0); stock2.put("black_12", 3);
        stock2.put("rust_brown_8", 0); stock2.put("rust_brown_9", 3); stock2.put("rust_brown_10", 4); stock2.put("rust_brown_11", 2); stock2.put("rust_brown_12", 0);
        localCatalog.add(new Shoe("rugged_hiker_x", "Rugged Hiker X", "Timber", "Casual & Sandals", "boot", colors2, cHex2, sizes2, stock2, 149.99));

        // Shoe 3: Breeze Slide Z
        List<String> colors3 = new ArrayList<>();
        colors3.add("black"); colors3.add("mocha"); colors3.add("taupe");
        Map<String, String> cHex3 = new HashMap<>();
        cHex3.put("black", "#222222"); cHex3.put("mocha", "#5C4033"); cHex3.put("taupe", "#B38B6D");
        List<Integer> sizes3 = new ArrayList<>();
        sizes3.add(6); sizes3.add(7); sizes3.add(8); sizes3.add(9); sizes3.add(10);
        Map<String, Integer> stock3 = new HashMap<>();
        stock3.put("black_6", 5); stock3.put("black_7", 2); stock3.put("black_8", 4); stock3.put("black_9", 0); stock3.put("black_10", 3);
        stock3.put("mocha_6", 0); stock3.put("mocha_7", 4); stock3.put("mocha_8", 2); stock3.put("mocha_9", 3); stock3.put("mocha_10", 1);
        stock3.put("taupe_6", 2); stock3.put("taupe_7", 0); stock3.put("taupe_8", 0); stock3.put("taupe_9", 4); stock3.put("taupe_10", 2);
        localCatalog.add(new Shoe("breeze_slide_z", "Breeze Slide Z", "Cove", "Casual & Sandals", "sandal", colors3, cHex3, sizes3, stock3, 49.99));

        // Shoe 4: Classic Oxford
        List<String> colors4 = new ArrayList<>();
        colors4.add("black"); colors4.add("mahogany");
        Map<String, String> cHex4 = new HashMap<>();
        cHex4.put("black", "#1A1A1A"); cHex4.put("mahogany", "#4A0E17");
        List<Integer> sizes4 = new ArrayList<>();
        sizes4.add(7); sizes4.add(8); sizes4.add(9); sizes4.add(10); sizes4.add(11);
        Map<String, Integer> stock4 = new HashMap<>();
        stock4.put("black_7", 3); stock4.put("black_8", 4); stock4.put("black_9", 5); stock4.put("black_10", 0); stock4.put("black_11", 2);
        stock4.put("mahogany_7", 2); stock4.put("mahogany_8", 0); stock4.put("mahogany_9", 3); stock4.put("mahogany_10", 4); stock4.put("mahogany_11", 1);
        localCatalog.add(new Shoe("classic_oxford", "Classic Oxford", "Aurelius", "Formal Classics", "formal", colors4, cHex4, sizes4, stock4, 119.99));

        // Shoe 5: Air Jordan 1 Retro
        List<String> colors5 = new ArrayList<>();
        colors5.add("red"); colors5.add("black"); colors5.add("blue");
        Map<String, String> cHex5 = new HashMap<>();
        cHex5.put("red", "#D32F2F"); cHex5.put("black", "#1A1A1A"); cHex5.put("blue", "#1976D2");
        List<Integer> sizes5 = new ArrayList<>();
        sizes5.add(8); sizes5.add(9); sizes5.add(10); sizes5.add(11);
        Map<String, Integer> stock5 = new HashMap<>();
        stock5.put("red_8", 3); stock5.put("red_9", 4); stock5.put("red_10", 0); stock5.put("red_11", 2);
        stock5.put("black_8", 1); stock5.put("black_9", 0); stock5.put("black_10", 3); stock5.put("black_11", 1);
        stock5.put("blue_8", 0); stock5.put("blue_9", 2); stock5.put("blue_10", 4); stock5.put("blue_11", 0);
        localCatalog.add(new Shoe("air_jordan_1", "Air Jordan 1 Retro", "Jordan", "Athletic & Basketball", "boot", colors5, cHex5, sizes5, stock5, 170.00));

        // Shoe 6: RS-X Runner
        List<String> colors6 = new ArrayList<>();
        colors6.add("white"); colors6.add("black"); colors6.add("red");
        Map<String, String> cHex6 = new HashMap<>();
        cHex6.put("white", "#F5F5F5"); cHex6.put("black", "#2C2C2A"); cHex6.put("red", "#D85A30");
        List<Integer> sizes6 = new ArrayList<>();
        sizes6.add(7); sizes6.add(8); sizes6.add(9); sizes6.add(10); sizes6.add(11);
        Map<String, Integer> stock6 = new HashMap<>();
        stock6.put("white_7", 3); stock6.put("white_8", 4); stock6.put("white_9", 0); stock6.put("white_10", 2); stock6.put("white_11", 1);
        stock6.put("black_7", 2); stock6.put("black_8", 0); stock6.put("black_9", 3); stock6.put("black_10", 4); stock6.put("black_11", 0);
        stock6.put("red_7", 0); stock6.put("red_8", 2); stock6.put("red_9", 1); stock6.put("red_10", 0); stock6.put("red_11", 3);
        localCatalog.add(new Shoe("rs_x_runner", "RS-X Runner", "Puma", "Athletic & Basketball", "sneaker_low", colors6, cHex6, sizes6, stock6, 110.00));

        // Shoe 7: Stan Smith Classic
        List<String> colors7 = new ArrayList<>();
        colors7.add("white"); colors7.add("green");
        Map<String, String> cHex7 = new HashMap<>();
        cHex7.put("white", "#EAEAEA"); cHex7.put("green", "#2E7D32");
        List<Integer> sizes7 = new ArrayList<>();
        sizes7.add(6); sizes7.add(7); sizes7.add(8); sizes7.add(9); sizes7.add(10);
        Map<String, Integer> stock7 = new HashMap<>();
        stock7.put("white_6", 4); stock7.put("white_7", 2); stock7.put("white_8", 5); stock7.put("white_9", 3); stock7.put("white_10", 1);
        stock7.put("green_6", 1); stock7.put("green_7", 0); stock7.put("green_8", 3); stock7.put("green_9", 4); stock7.put("green_10", 0);
        localCatalog.add(new Shoe("stan_smith_classic", "Stan Smith Classic", "Adidas", "Casual & Sandals", "sneaker_low", colors7, cHex7, sizes7, stock7, 95.00));
    }
}
