package com.infy.temiapplication;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.button.MaterialButton;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;
import com.robotemi.sdk.BatteryData;
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener;
import com.infy.temiapplication.catalog.ShoeCatalogActivity;
import com.infy.temiapplication.data.FirebaseRepo;
import com.infy.temiapplication.model.CartItem;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements OnGoToLocationStatusChangedListener {
    private static final String TAG = "MainActivity";

    // Temi Location Name Constants (adjust to match robot map configuration)
    public static final String LOC_STOREROOM = "stockroom";
    public static final String LOC_PICKUP = "showroom";
    public static final String LOC_HOME = "home base";

    private Robot robot;
    private FirebaseRepo repo;

    // UI Elements
    private ConstraintLayout containerWelcome;
    private ConstraintLayout containerTravelStatus;
    private TextView textStatusTitle;
    private TextView textStatusInstructions;
    private TextView textOrderItemsHeader;
    private LinearLayout layoutOrderItemsContainer;
    private ProgressBar progressTravel;
    private ImageView imgArrived;
    private MaterialButton btnStatusOk;
    private MaterialButton btnStartOrdering;

    private TextView textWelcomeDesc;

    // Master Store PIN Registry (Synchronized with admin.html)
    private static final java.util.Map<String, String> STORE_PIN_REGISTRY = new java.util.HashMap<>();
    private static final java.util.Map<String, String> STORE_DISPLAY_NAMES = new java.util.HashMap<>();
    static {
        STORE_PIN_REGISTRY.put("bengaluru", "4910");
        STORE_PIN_REGISTRY.put("mysore", "5290");
        STORE_PIN_REGISTRY.put("chennai_sholinganallur", "3620");
        STORE_PIN_REGISTRY.put("chennai_mcity", "3621");
        STORE_PIN_REGISTRY.put("hyd_sez", "9154");
        STORE_PIN_REGISTRY.put("tvm", "6418");
        STORE_PIN_REGISTRY.put("pune", "7821");
        STORE_PIN_REGISTRY.put("noida", "2013");

        STORE_DISPLAY_NAMES.put("bengaluru", "Bengaluru Store");
        STORE_DISPLAY_NAMES.put("mysore", "Mysore Store");
        STORE_DISPLAY_NAMES.put("chennai_sholinganallur", "Chennai - Shollinganallur Store");
        STORE_DISPLAY_NAMES.put("chennai_mcity", "Chennai - Mcity Store");
        STORE_DISPLAY_NAMES.put("hyd_sez", "Hyd Sez Store");
        STORE_DISPLAY_NAMES.put("tvm", "TVM Store");
        STORE_DISPLAY_NAMES.put("pune", "Pune Store");
        STORE_DISPLAY_NAMES.put("noida", "Noida Store");
    }

    // Current State
    private String currentActiveOrderId = "";
    private static String lastSpokenStatus = "";
    private String currentStatus = "idle";
    private String currentRobotState = "idle";
    private String currentLocation = "none";
    private boolean isTemiAvailable = false;
    private String lastNavigatedLocation = "";
    private String targetLocationBeforeBlock = "";
    private boolean isManualOverrideActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI Elements
        containerWelcome = findViewById(R.id.container_welcome);
        containerTravelStatus = findViewById(R.id.container_travel_status);
        textStatusTitle = findViewById(R.id.text_status_title);
        textStatusInstructions = findViewById(R.id.text_status_instructions);
        textOrderItemsHeader = findViewById(R.id.text_order_items_header);
        layoutOrderItemsContainer = findViewById(R.id.layout_order_items_container);
        progressTravel = findViewById(R.id.progress_robot_travel);
        imgArrived = findViewById(R.id.img_status_arrived);
        btnStatusOk = findViewById(R.id.btn_status_ok);
        btnStartOrdering = findViewById(R.id.btn_start_ordering);
        textWelcomeDesc = findViewById(R.id.text_welcome_desc);
        TextView textBrandTitle = findViewById(R.id.text_brand_title);

        // Secret Staff Store Switcher: Long-press brand title to re-assign store with PIN
        if (textBrandTitle != null) {
            textBrandTitle.setOnLongClickListener(v -> {
                showStoreSetupDialog(false);
                return true;
            });
        }

        // Initialize Temi Robot SDK safely
        try {
            robot = Robot.getInstance();
            robot.addOnGoToLocationStatusChangedListener(this);
            isTemiAvailable = true;
            Log.d(TAG, "Temi SDK initialized successfully.");
            
            // Log available map locations for debugging
            List<String> locations = robot.getLocations();
            Log.d(TAG, "Available locations on Temi: " + locations.toString());
        } catch (Exception e) {
            Log.e(TAG, "Temi SDK not available (running on standard device/emulator).", e);
            isTemiAvailable = false;
        }

        // Initialize Local/Firebase Repo
        repo = FirebaseRepo.getInstance();
        
        // One-time setup check: Retrieve saved store location or prompt on very first install
        android.content.SharedPreferences prefs = getSharedPreferences("temi_kiosk_prefs", MODE_PRIVATE);
        String savedLoc = prefs.getString("store_location_id", null);
        if (savedLoc != null && !savedLoc.trim().isEmpty()) {
            repo.setStoreLocationId(savedLoc);
            updateWelcomeStoreBadge(savedLoc);
        } else {
            showStoreSetupDialog(true);
        }

        // Setup Order Screen trigger
        btnStartOrdering.setOnClickListener(v -> {
            // Prevent spamming
            btnStartOrdering.setEnabled(false);
            speakTTS("Hi welcome! Please add items into cart and place order.");
            Toast.makeText(MainActivity.this, "Hi Welcome! Please add items into cart and place order.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(MainActivity.this, ShoeCatalogActivity.class);
            startActivity(intent);
        });

        // Setup status confirm action button
        btnStatusOk.setOnClickListener(v -> {
            btnStatusOk.setEnabled(false); // Double tap prevention
            handleConfirmOkClick();
        });
    }

    private void updateWelcomeStoreBadge(String storeKey) {
        if (textWelcomeDesc != null) {
            String name = STORE_DISPLAY_NAMES.get(storeKey);
            if (name == null) name = "Store Kiosk";
            textWelcomeDesc.setText(String.format("📍 %s • Touch screen to start", name));
        }
    }

    /**
     * Displayed on fresh install or when staff long-presses "Temi Shoe Mart".
     * Requires the 4-digit Security PIN for the chosen store to prevent unauthorized changes.
     */
    private void showStoreSetupDialog(final boolean isFirstTime) {
        final String[] locationKeys = {
            "bengaluru", "mysore", "chennai_sholinganallur", "chennai_mcity",
            "hyd_sez", "tvm", "pune", "noida"
        };
        final String[] locationNames = {
            "Bengaluru Store", "Mysore Store", "Chennai - Shollinganallur Store", "Chennai - Mcity Store",
            "Hyd Sez Store", "TVM Store", "Pune Store", "Noida Store"
        };

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(isFirstTime ? "📍 Initial Setup: Select Store" : "🔒 Staff Menu: Switch Store Location")
            .setCancelable(!isFirstTime)
            .setItems(locationNames, (dialog, which) -> {
                String chosenKey = locationKeys[which];
                String chosenName = locationNames[which];
                promptStoreSecurityPin(chosenKey, chosenName, isFirstTime);
                dialog.dismiss();
            });

        if (!isFirstTime) {
            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        }
        builder.show();
    }

    private void promptStoreSecurityPin(final String chosenKey, final String chosenName, final boolean isFirstTime) {
        android.widget.EditText inputPin = new android.widget.EditText(this);
        inputPin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        inputPin.setHint("4-digit PIN");
        inputPin.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        inputPin.setTextSize(22);
        inputPin.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(4) });

        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = 50;
        params.rightMargin = 50;
        params.topMargin = 20;
        inputPin.setLayoutParams(params);
        container.addView(inputPin);

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🔒 Enter PIN for " + chosenName)
            .setMessage("Please enter the 4-digit Store Security PIN to authorize:")
            .setView(container)
            .setCancelable(!isFirstTime)
            .setPositiveButton("Authorize", (dialog, which) -> {
                String enteredPin = inputPin.getText().toString().trim();
                String expectedPin = STORE_PIN_REGISTRY.get(chosenKey);

                if (expectedPin != null && expectedPin.equals(enteredPin)) {
                    android.content.SharedPreferences prefs = getSharedPreferences("temi_kiosk_prefs", MODE_PRIVATE);
                    prefs.edit().putString("store_location_id", chosenKey).apply();
                    repo.setStoreLocationId(chosenKey);
                    updateWelcomeStoreBadge(chosenKey);
                    Toast.makeText(MainActivity.this, "✅ Authenticated: Robot assigned to " + chosenName + "!", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                } else {
                    Toast.makeText(MainActivity.this, "⛔ Incorrect Security PIN for " + chosenName + ". Access Denied.", Toast.LENGTH_LONG).show();
                    if (isFirstTime) {
                        showStoreSetupDialog(true); // Re-prompt on first boot
                    }
                }
            })
            .setNegativeButton(isFirstTime ? null : "Cancel", (dialog, which) -> {
                dialog.dismiss();
            })
            .show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        btnStartOrdering.setEnabled(true);
        // Start observing robot status in database (real or mock)
        repo.observeRobotState(robotStateListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        repo.removeRobotStateCallback(robotStateListener);
    }

    private final FirebaseRepo.RobotStateCallback robotStateListener = new FirebaseRepo.RobotStateCallback() {
        @Override
        public void onStateChanged(String location, String status, String robotState, String activeOrderId) {
            currentLocation = location;
            currentStatus = status;
            currentRobotState = robotState;
            currentActiveOrderId = activeOrderId;

            runOnUiThread(() -> updateStatusUI());
        }
    };

    /**
     * Updates the Kiosk screen layout based on robot location and order status.
     */
    private void speakTTSOnce(String message, String statusKey) {
        if (statusKey == null || statusKey.equalsIgnoreCase(lastSpokenStatus)) {
            return;
        }
        if ("arrived_pickup".equalsIgnoreCase(statusKey) && "completed".equalsIgnoreCase(lastSpokenStatus)) {
            return; // Order is already completed, do not repeat arrival speech
        }
        lastSpokenStatus = statusKey;
        speakTTS(message);
    }

    private void updateStatusUI() {
        boolean isManualOverride = currentStatus != null && currentStatus.startsWith("manual_override_to_");
        boolean isIdleStatus = "idle".equalsIgnoreCase(currentStatus)
                || "none".equalsIgnoreCase(currentStatus)
                || currentStatus == null
                || currentStatus.isEmpty();

        boolean hasNoActiveOrder = (currentActiveOrderId == null || currentActiveOrderId.trim().isEmpty() || "none".equalsIgnoreCase(currentActiveOrderId));

        if (isIdleStatus) {
            lastNavigatedLocation = "";
            isManualOverrideActive = false;
            if (isTemiAvailable && robot != null) {
                try {
                    robot.stopMovement();
                } catch (Exception e) {
                    Log.e(TAG, "Error stopping movement on idle", e);
                }
            }
            containerWelcome.setVisibility(View.VISIBLE);
            containerTravelStatus.setVisibility(View.GONE);
            return;
        }

        if (!isManualOverride && hasNoActiveOrder) {
            // Check if robot was traveling to stockroom for an order when order got cancelled
            if (!isManualOverrideActive && LOC_STOREROOM.equalsIgnoreCase(lastNavigatedLocation)) {
                // Command Temi to safely turn around and return to Showroom!
                lastNavigatedLocation = "";
                containerWelcome.setVisibility(View.GONE);
                containerTravelStatus.setVisibility(View.VISIBLE);
                progressTravel.setVisibility(View.VISIBLE);
                imgArrived.setVisibility(View.GONE);
                btnStatusOk.setVisibility(View.GONE);
                textStatusTitle.setText("Order Cancelled");
                textStatusInstructions.setText("Order was cancelled. Temi is returning to the showroom...");
                speakTTSOnce("Order was cancelled. Returning to showroom.", "order_cancelled_return");
                goToLocation(LOC_PICKUP);
                return;
            }

            // Kiosk is Idle at Showroom / Base: Show welcome screen
            containerWelcome.setVisibility(View.VISIBLE);
            containerTravelStatus.setVisibility(View.GONE);
            return;
        }

        // Delivery Active or Manual Override: Hide start order screen
        containerWelcome.setVisibility(View.GONE);
        containerTravelStatus.setVisibility(View.VISIBLE);

        // Hide order list view by default, only populate in arrived states
        textOrderItemsHeader.setVisibility(View.GONE);
        layoutOrderItemsContainer.setVisibility(View.GONE);

        // Configure layout based on active delivery step
        switch (currentStatus) {
            case "traveling_storeroom":
                isManualOverrideActive = false;
                progressTravel.setVisibility(View.VISIBLE);
                imgArrived.setVisibility(View.GONE);
                btnStatusOk.setVisibility(View.GONE);
                textStatusTitle.setText(R.string.status_traveling_storeroom);
                textStatusInstructions.setText("Please wait while Temi travels to the store room...");
                speakTTSOnce(getString(R.string.tts_start_trip), "traveling_storeroom");
                goToLocation(LOC_STOREROOM);
                break;

            case "arrived_storeroom":
                isManualOverrideActive = false;
                progressTravel.setVisibility(View.GONE);
                imgArrived.setVisibility(View.VISIBLE);
                btnStatusOk.setVisibility(View.VISIBLE);
                btnStatusOk.setEnabled(true);
                btnStatusOk.setText("Shoes Loaded");
                textStatusTitle.setText(R.string.status_arrived_storeroom);
                textStatusInstructions.setText("Waiting for Staff to load the ordered shoes into Temi's tray, then press 'Shoes Loaded' button.");
                
                // Fetch and display items to load
                displayActiveOrderItemsList();

                // Speak TTS announcement on arrival once
                speakTTSOnce(getString(R.string.tts_arrived_storeroom), "arrived_storeroom");
                break;

            case "traveling_pickup":
                isManualOverrideActive = false;
                progressTravel.setVisibility(View.VISIBLE);
                imgArrived.setVisibility(View.GONE);
                btnStatusOk.setVisibility(View.GONE);
                textStatusTitle.setText(R.string.status_traveling_pickup);
                textStatusInstructions.setText("Order loaded! Temi is traveling to the showroom");
                speakTTSOnce(getString(R.string.tts_heading_to_pickup), "traveling_pickup");
                goToLocation(LOC_PICKUP);
                break;

            case "arrived_pickup":
                isManualOverrideActive = false;
                if ("completed".equalsIgnoreCase(lastSpokenStatus)) {
                    return;
                }
                progressTravel.setVisibility(View.GONE);
                imgArrived.setVisibility(View.VISIBLE);
                btnStatusOk.setVisibility(View.VISIBLE);
                btnStatusOk.setEnabled(true);
                btnStatusOk.setText("Collect Shoes");
                textStatusTitle.setText(R.string.status_arrived_pickup);
                textStatusInstructions.setText("Please take your shoes from the tray, then press 'Collect Shoes' below to complete order.");
                
                // Fetch and display items to collect
                displayActiveOrderItemsList();

                speakTTSOnce(getString(R.string.tts_arrived_pickup), "arrived_pickup");
                break;

            case "returning_home":
                isManualOverrideActive = false;
                progressTravel.setVisibility(View.VISIBLE);
                imgArrived.setVisibility(View.GONE);
                btnStatusOk.setVisibility(View.GONE);
                textStatusTitle.setText("Returning to Dock");
                textStatusInstructions.setText("Temi is returning to the charging dock...");
                goToLocation(LOC_HOME);
                break;

            case "returning_staging":
                isManualOverrideActive = false;
                progressTravel.setVisibility(View.VISIBLE);
                imgArrived.setVisibility(View.GONE);
                btnStatusOk.setVisibility(View.GONE);
                textStatusTitle.setText("Returning to Base");
                textStatusInstructions.setText("Temi is returning to the staging area...");
                goToLocation(LOC_PICKUP);
                break;
            case "blocked":
                progressTravel.setVisibility(View.GONE);
                imgArrived.setVisibility(View.VISIBLE);
                btnStatusOk.setVisibility(View.VISIBLE);
                btnStatusOk.setEnabled(true);
                btnStatusOk.setText("Retry");
                textStatusTitle.setText("Path Blocked");
                textStatusInstructions.setText("Temi is blocked by an obstacle. Please clear the path and tap 'Retry' to continue.");
                speakTTSOnce("Excuse me, my path is blocked. Please clear the way.", "blocked");
                break;
            case "manual_override_to_stockroom":
                isManualOverrideActive = true;
                progressTravel.setVisibility(View.VISIBLE);
                imgArrived.setVisibility(View.GONE);
                btnStatusOk.setVisibility(View.GONE);
                textStatusTitle.setText("Manual Override");
                textStatusInstructions.setText("Temi is navigating to the Stock Room under manual control...");
                goToLocation(LOC_STOREROOM);
                break;

            case "manual_override_to_showroom":
                isManualOverrideActive = true;
                progressTravel.setVisibility(View.VISIBLE);
                imgArrived.setVisibility(View.GONE);
                btnStatusOk.setVisibility(View.GONE);
                textStatusTitle.setText("Manual Override");
                textStatusInstructions.setText("Temi is navigating to the Showroom under manual control...");
                goToLocation(LOC_PICKUP);
                break;

            case "manual_override_to_home_base":
            case "manual_override_to_home base":
                isManualOverrideActive = true;
                progressTravel.setVisibility(View.VISIBLE);
                imgArrived.setVisibility(View.GONE);
                btnStatusOk.setVisibility(View.GONE);
                textStatusTitle.setText("Manual Override");
                textStatusInstructions.setText("Temi is navigating to the Charging Dock under manual control...");
                goToLocation(LOC_HOME);
                break;

            case "idle":
            case "none":
            case "":
                containerWelcome.setVisibility(View.VISIBLE);
                containerTravelStatus.setVisibility(View.GONE);
                break;

            default:
                // Default fallback
                progressTravel.setVisibility(View.VISIBLE);
                imgArrived.setVisibility(View.GONE);
                btnStatusOk.setVisibility(View.GONE);
                textStatusTitle.setText("Processing...");
                textStatusInstructions.setText("");
                break;
        }
    }

    private void displayActiveOrderItemsList() {
        if (currentActiveOrderId == null || currentActiveOrderId.isEmpty()) {
            textOrderItemsHeader.setVisibility(View.GONE);
            layoutOrderItemsContainer.setVisibility(View.GONE);
            return;
        }

        final String headerText = "arrived_storeroom".equals(currentStatus) ? "Items to Load:" : "Items to Collect:";

        repo.getActiveOrderItems(currentActiveOrderId, new FirebaseRepo.OrderDetailsCallback() {
            @Override
            public void onOrderDetailsLoaded(List<CartItem> items) {
                runOnUiThread(() -> {
                    if (items == null || items.isEmpty()) {
                        textOrderItemsHeader.setVisibility(View.GONE);
                        layoutOrderItemsContainer.setVisibility(View.GONE);
                        return;
                    }

                    layoutOrderItemsContainer.removeAllViews();
                    LayoutInflater inflater = LayoutInflater.from(MainActivity.this);

                    for (CartItem item : items) {
                        View itemView = inflater.inflate(R.layout.item_order_preview, layoutOrderItemsContainer, false);

                        TextView nameText = itemView.findViewById(R.id.preview_item_name);
                        TextView variantText = itemView.findViewById(R.id.preview_item_variant);
                        TextView qtyText = itemView.findViewById(R.id.preview_item_qty);
                        ImageView imageFill = itemView.findViewById(R.id.preview_shoe_fill);
                        ImageView imageDetails = itemView.findViewById(R.id.preview_shoe_details);

                        if (item.getBrand() != null && !item.getBrand().isEmpty()) {
                            nameText.setText(String.format("%s - %s", item.getBrand(), item.getShoeName()));
                        } else {
                            nameText.setText(item.getShoeName());
                        }
                        String capitalizedColor = item.getColor().substring(0, 1).toUpperCase(Locale.ROOT) + item.getColor().substring(1);
                        variantText.setText(String.format(Locale.US, "%s | Size %d", capitalizedColor, item.getSize()));
                        qtyText.setText(String.format(Locale.US, "Qty: %d", item.getQty()));

                        // Resolve Vector Drawables based on shapeSet
                        int fillResId = R.drawable.shoe_sneaker_low_side_fill;
                        int detailsResId = R.drawable.shoe_sneaker_low_side_details;

                        String shape = item.getShapeSet() != null ? item.getShapeSet() : "sneaker_low";
                        switch (shape) {
                            case "boot":
                                fillResId = R.drawable.shoe_boot_side_fill;
                                detailsResId = R.drawable.shoe_boot_side_details;
                                break;
                            case "sandal":
                                fillResId = R.drawable.shoe_sandal_side_fill;
                                detailsResId = R.drawable.shoe_sandal_side_details;
                                break;
                            case "formal":
                                fillResId = R.drawable.shoe_formal_side_fill;
                                detailsResId = R.drawable.shoe_formal_side_details;
                                break;
                            case "sneaker_high":
                                fillResId = R.drawable.shoe_sneaker_high_fill;
                                detailsResId = R.drawable.shoe_sneaker_high_details;
                                break;
                            case "sneaker_sport":
                                fillResId = R.drawable.shoe_sneaker_sport_fill;
                                detailsResId = R.drawable.shoe_sneaker_sport_details;
                                break;
                            case "sneaker_low":
                            default:
                                fillResId = R.drawable.shoe_sneaker_low_side_fill;
                                detailsResId = R.drawable.shoe_sneaker_low_side_details;
                                break;
                        }

                        imageFill.setImageResource(fillResId);
                        imageDetails.setImageResource(detailsResId);

                        // Apply programmatic color tint
                        if (item.getColorHex() != null) {
                            try {
                                imageFill.setColorFilter(Color.parseColor(item.getColorHex()), PorterDuff.Mode.SRC_IN);
                            } catch (Exception e) {
                                imageFill.clearColorFilter();
                            }
                        }

                        layoutOrderItemsContainer.addView(itemView);
                    }

                    textOrderItemsHeader.setText(headerText);
                    textOrderItemsHeader.setVisibility(View.VISIBLE);
                    layoutOrderItemsContainer.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    textOrderItemsHeader.setVisibility(View.GONE);
                    layoutOrderItemsContainer.setVisibility(View.GONE);
                });
            }
        });
    }

    /**
     * Handles clicking the "OK" confirmation button (called either by staff or customer).
     */
    private void handleConfirmOkClick() {
        if ("arrived_storeroom".equals(currentStatus)) {
            // Staff loaded shoes, ready to head to pickup
            speakTTS(getString(R.string.tts_heading_to_pickup));
            repo.updateOrderStatus(currentActiveOrderId, "delivering");
            repo.updateRobotStateInDatabase("moving", "traveling_pickup", "moving", currentActiveOrderId);
            
            // Command physical robot to navigate
            goToLocation(LOC_PICKUP);
            
        } else if ("arrived_pickup".equals(currentStatus)) {
            lastSpokenStatus = "completed";
            currentStatus = "idle";
            String completedOrderId = currentActiveOrderId;
            currentActiveOrderId = "";

            speakTTS(getString(R.string.tts_order_complete));
            
            // Mark order completed in database
            repo.updateOrderStatus(completedOrderId, "completed");

            // Check battery level to decide where to go
            int batteryPct = 100;
            if (isTemiAvailable && robot != null) {
                try {
                    BatteryData data = robot.getBatteryData();
                    if (data != null) {
                        batteryPct = data.getBatteryPercentage();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking battery", e);
                }
            }

            if (batteryPct <= 30) {
                // Low battery: Go to Home Base charging dock
                repo.updateRobotStateInDatabase("moving", "returning_home", "moving", "");
                goToLocation(LOC_HOME);
                Toast.makeText(this, "Order completed! Battery low (" + batteryPct + "%), returning to home base.", Toast.LENGTH_LONG).show();
            } else {
                // Battery is fine: Already at Staging/Pickup area (LOC_PICKUP), so transition to idle directly!
                repo.updateRobotStateInDatabase(LOC_PICKUP, "idle", "idle", "");
                Toast.makeText(this, "Order completed!", Toast.LENGTH_LONG).show();
            }

            // Immediately switch UI to idle welcome state
            containerWelcome.setVisibility(View.VISIBLE);
            containerTravelStatus.setVisibility(View.GONE);

            // Delay navigation slightly so Temi finishes speaking "Thank you for shopping!" without being cut off by activity destruction
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Intent intent = new Intent(MainActivity.this, ShoeCatalogActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }, 1800);
        } else if ("blocked".equals(currentStatus)) {
            // Obstacle cleared, retry navigation to the target zone
            String targetLoc = (targetLocationBeforeBlock != null && !targetLocationBeforeBlock.isEmpty()) ? targetLocationBeforeBlock : LOC_PICKUP;
            String nextStatus = "returning_staging";

            if (currentActiveOrderId != null && !currentActiveOrderId.trim().isEmpty()) {
                if (LOC_PICKUP.equalsIgnoreCase(targetLoc) || 
                    "display area".equalsIgnoreCase(targetLoc) || 
                    "pickup_zone".equalsIgnoreCase(targetLoc)) {
                    targetLoc = LOC_PICKUP;
                    nextStatus = "traveling_pickup";
                } else {
                    targetLoc = LOC_STOREROOM;
                    nextStatus = "traveling_storeroom";
                }
                speakTTS("Resuming delivery round.");
            } else {
                if (LOC_STOREROOM.equalsIgnoreCase(targetLoc)) {
                    nextStatus = "manual_override_to_stockroom";
                    isManualOverrideActive = true;
                    speakTTS("Resuming trip to stockroom.");
                } else if (LOC_HOME.equalsIgnoreCase(targetLoc)) {
                    nextStatus = "manual_override_to_home_base";
                    isManualOverrideActive = true;
                    speakTTS("Resuming return to dock.");
                } else {
                    nextStatus = "manual_override_to_showroom";
                    isManualOverrideActive = true;
                    speakTTS("Resuming return to showroom.");
                }
            }

            repo.updateRobotStateInDatabase("moving", nextStatus, "moving", currentActiveOrderId != null ? currentActiveOrderId : "");
            goToLocation(targetLoc);
        }
    }

    private void goToLocation(String location) {
        if (location.equalsIgnoreCase(lastNavigatedLocation)) {
            // Prevent duplicate triggers to Temi SDK while already traveling
            return;
        }
        lastNavigatedLocation = location;
        targetLocationBeforeBlock = location;

        if (isTemiAvailable) {
            try {
                Log.d(TAG, "Commanding Temi to goTo: " + location);
                robot.goTo(location);
            } catch (Exception e) {
                Log.e(TAG, "Error calling robot.goTo", e);
            }
        } else {
            Log.d(TAG, "Mocking robot travel to: " + location);
        }
    }

    private void speakTTS(String message) {
        if (isTemiAvailable && robot != null) {
            try {
                robot.cancelAllTtsRequests(); // Immediately cancel any lingering or ongoing speech
                robot.speak(TtsRequest.create(message, false));
            } catch (Exception e) {
                Log.e(TAG, "Error executing robot.speak", e);
            }
        } else {
            Log.d(TAG, "Mock TTS Speech: " + message);
        }
    }

    @Override
    public void onGoToLocationStatusChanged(String location, String status, int descriptionId, String description) {
        Log.d(TAG, "Temi GoTo Location Status: " + location + ", status: " + status);
        
        // Defensive: Fall back to lastNavigatedLocation if SDK returned empty string
        String resolvedLocation = (location == null || location.isEmpty()) ? lastNavigatedLocation : location;

        // Handle physical arrival triggers to sync with Firebase
        if ("complete".equalsIgnoreCase(status)) {
            lastNavigatedLocation = ""; // Reset navigation cache upon arrival

            if (isManualOverrideActive) {
                isManualOverrideActive = false;
                repo.updateRobotStateInDatabase(resolvedLocation, "idle", "idle", "");
                speakTTSOnce("Arrived at " + resolvedLocation + ".", "manual_arrived_" + resolvedLocation);
                return;
            }

            if (LOC_STOREROOM.equalsIgnoreCase(resolvedLocation)) {
                if (currentActiveOrderId == null || currentActiveOrderId.isEmpty()) {
                    // Arrived at stockroom but order was cancelled: immediately return to showroom!
                    speakTTSOnce("No active order. Returning to showroom.", "return_showroom_no_order");
                    repo.updateRobotStateInDatabase(LOC_STOREROOM, "returning_staging", "moving", "");
                    goToLocation(LOC_PICKUP);
                } else {
                    repo.updateRobotStateInDatabase(LOC_STOREROOM, "arrived_storeroom", "arrived_store_room", currentActiveOrderId);
                }
            } else if (LOC_PICKUP.equalsIgnoreCase(resolvedLocation)) {
                if (currentActiveOrderId == null || currentActiveOrderId.isEmpty()) {
                    repo.updateRobotStateInDatabase(LOC_PICKUP, "idle", "idle", "");
                } else {
                    repo.updateRobotStateInDatabase(LOC_PICKUP, "arrived_pickup", "arrived_pickup_zone", currentActiveOrderId);
                }
            } else if (LOC_HOME.equalsIgnoreCase(resolvedLocation)) {
                repo.updateRobotStateInDatabase("home_base", "idle", "idle", "");
            }
        } else if ("abort".equalsIgnoreCase(status) || 
                   "reject".equalsIgnoreCase(status)) {
            lastNavigatedLocation = ""; // Reset navigation cache on abort to allow retries
            repo.updateRobotStateInDatabase("moving", "blocked", "blocked", currentActiveOrderId);
            speakTTS("Excuse me, my path is blocked. Please clear the way.");
        }
    }

    @Override
    protected void onDestroy() {
        if (isTemiAvailable && robot != null) {
            robot.removeOnGoToLocationStatusChangedListener(this);
        }
        super.onDestroy();
    }
}
