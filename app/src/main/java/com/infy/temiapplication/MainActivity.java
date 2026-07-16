package com.infy.temiapplication;

import android.content.Intent;
import android.os.Bundle;
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
    public static final String LOC_STOREROOM = "store_room";
    public static final String LOC_PICKUP = "pickup_zone";
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

    // Current State
    private String currentActiveOrderId = "";
    private String lastSpokenStatus = "";
    private String currentStatus = "idle";
    private String currentRobotState = "idle";
    private String currentLocation = "none";
    private boolean isTemiAvailable = false;

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
        
        // Setup Order Screen trigger
        btnStartOrdering.setOnClickListener(v -> {
            // Prevent spamming
            btnStartOrdering.setEnabled(false);
            speakTTS("Hi welcome please choose the items and add to cart");
            Toast.makeText(MainActivity.this, "Welcome! Please choose the items and add to cart.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(MainActivity.this, ShoeCatalogActivity.class);
            startActivity(intent);
        });

        // Setup status confirm action button
        btnStatusOk.setOnClickListener(v -> {
            btnStatusOk.setEnabled(false); // Double tap prevention
            handleConfirmOkClick();
        });
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
        if (statusKey.equalsIgnoreCase(lastSpokenStatus)) {
            return;
        }
        lastSpokenStatus = statusKey;
        speakTTS(message);
    }

    private void updateStatusUI() {
        if (currentActiveOrderId == null || currentActiveOrderId.isEmpty()) {
            // Kiosk is Idle: Show welcome screen
            containerWelcome.setVisibility(View.VISIBLE);
            containerTravelStatus.setVisibility(View.GONE);
            lastSpokenStatus = ""; // Reset speech lock for new orders
            return;
        }

        // Delivery Active: Hide start order screen
        containerWelcome.setVisibility(View.GONE);
        containerTravelStatus.setVisibility(View.VISIBLE);

        // Hide order list view by default, only populate in arrived states
        textOrderItemsHeader.setVisibility(View.GONE);
        layoutOrderItemsContainer.setVisibility(View.GONE);

        // Configure layout based on active delivery step
        switch (currentStatus) {
            case "traveling_storeroom":
                progressTravel.setVisibility(View.VISIBLE);
                imgArrived.setVisibility(View.GONE);
                btnStatusOk.setVisibility(View.GONE);
                textStatusTitle.setText(R.string.status_traveling_storeroom);
                textStatusInstructions.setText("Please wait while Temi travels to the store room...");
                speakTTSOnce("Order placed. Heading to the store room.", "traveling_storeroom");
                goToLocation(LOC_STOREROOM);
                break;

            case "arrived_storeroom":
                progressTravel.setVisibility(View.GONE);
                imgArrived.setVisibility(View.VISIBLE);
                btnStatusOk.setVisibility(View.VISIBLE);
                btnStatusOk.setEnabled(true);
                btnStatusOk.setText("Shoes Loaded");
                textStatusTitle.setText(R.string.status_arrived_storeroom);
                textStatusInstructions.setText("Staff: Please load the ordered shoes into Temi's tray, then press 'Shoes Loaded' below.");
                
                // Fetch and display items to load
                displayActiveOrderItemsList();

                // Speak TTS announcement on arrival once
                speakTTSOnce(getString(R.string.tts_arrived_storeroom), "arrived_storeroom");
                break;

            case "traveling_pickup":
                progressTravel.setVisibility(View.VISIBLE);
                imgArrived.setVisibility(View.GONE);
                btnStatusOk.setVisibility(View.GONE);
                textStatusTitle.setText(R.string.status_traveling_pickup);
                textStatusInstructions.setText("Order loaded! Temi is traveling to the pickup zone. Please meet Temi there.");
                speakTTSOnce("Order loaded! Temi is traveling to the pickup zone.", "traveling_pickup");
                goToLocation(LOC_PICKUP);
                break;

            case "arrived_pickup":
                progressTravel.setVisibility(View.GONE);
                imgArrived.setVisibility(View.VISIBLE);
                btnStatusOk.setVisibility(View.VISIBLE);
                btnStatusOk.setEnabled(true);
                btnStatusOk.setText("Collect Shoes");
                textStatusTitle.setText(R.string.status_arrived_pickup);
                textStatusInstructions.setText("Customer: Please take your shoes from the tray, then press 'Collect Shoes' below to complete order.");
                
                // Fetch and display items to collect
                displayActiveOrderItemsList();

                speakTTSOnce(getString(R.string.tts_arrived_pickup), "arrived_pickup");
                break;

            case "returning_home":
                progressTravel.setVisibility(View.VISIBLE);
                imgArrived.setVisibility(View.GONE);
                btnStatusOk.setVisibility(View.GONE);
                textStatusTitle.setText("Returning to Dock");
                textStatusInstructions.setText("Temi is returning to the charging dock...");
                goToLocation(LOC_HOME);
                break;

            case "returning_staging":
                progressTravel.setVisibility(View.VISIBLE);
                imgArrived.setVisibility(View.GONE);
                btnStatusOk.setVisibility(View.GONE);
                textStatusTitle.setText("Returning to Base");
                textStatusInstructions.setText("Temi is returning to the staging area...");
                goToLocation(LOC_PICKUP);
                break;

            case "manual_override_to_store_room":
                progressTravel.setVisibility(View.VISIBLE);
                imgArrived.setVisibility(View.GONE);
                btnStatusOk.setVisibility(View.GONE);
                textStatusTitle.setText("Manual Override");
                textStatusInstructions.setText("Temi is navigating to the Store Room under manual control...");
                goToLocation(LOC_STOREROOM);
                break;

            case "manual_override_to_pickup_zone":
                progressTravel.setVisibility(View.VISIBLE);
                imgArrived.setVisibility(View.GONE);
                btnStatusOk.setVisibility(View.GONE);
                textStatusTitle.setText("Manual Override");
                textStatusInstructions.setText("Temi is navigating to the Pickup Zone under manual control...");
                goToLocation(LOC_PICKUP);
                break;

            case "manual_override_to_home_base":
            case "manual_override_to_home base":
                progressTravel.setVisibility(View.VISIBLE);
                imgArrived.setVisibility(View.GONE);
                btnStatusOk.setVisibility(View.GONE);
                textStatusTitle.setText("Manual Override");
                textStatusInstructions.setText("Temi is navigating to the Charging Dock under manual control...");
                goToLocation(LOC_HOME);
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

                        nameText.setText(item.getShoeName());
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
            repo.updateRobotStateInDatabase(LOC_PICKUP, "traveling_pickup", "moving", currentActiveOrderId);
            
            // Command physical robot to navigate
            goToLocation(LOC_PICKUP);
            
        } else if ("arrived_pickup".equals(currentStatus)) {
            speakTTS(getString(R.string.tts_order_complete));

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
                repo.updateRobotStateInDatabase(LOC_HOME, "returning_home", "moving", "");
                goToLocation(LOC_HOME);
                Toast.makeText(this, "Order completed! Battery low (" + batteryPct + "%), returning to home base.", Toast.LENGTH_LONG).show();
            } else {
                // Battery is fine: Go back to Staging/Pickup area to wait for next order
                repo.updateRobotStateInDatabase(LOC_PICKUP, "returning_staging", "moving", "");
                goToLocation(LOC_PICKUP);
                Toast.makeText(this, "Order completed! Returning to staging area.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void goToLocation(String location) {
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
        if (isTemiAvailable) {
            try {
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
        
        // Handle physical arrival triggers to sync with Firebase
        if ("complete".equalsIgnoreCase(status)) {
            if (LOC_STOREROOM.equalsIgnoreCase(location)) {
                repo.updateRobotStateInDatabase(LOC_STOREROOM, "arrived_storeroom", "arrived_store_room", currentActiveOrderId);
            } else if (LOC_PICKUP.equalsIgnoreCase(location)) {
                if (currentActiveOrderId == null || currentActiveOrderId.isEmpty()) {
                    repo.updateRobotStateInDatabase("none", "idle", "idle", "");
                } else {
                    repo.updateRobotStateInDatabase(LOC_PICKUP, "arrived_pickup", "arrived_pickup_zone", currentActiveOrderId);
                }
            } else if (LOC_HOME.equalsIgnoreCase(location)) {
                repo.updateRobotStateInDatabase("none", "idle", "idle", "");
            }
        } else if ("abort".equalsIgnoreCase(status) || 
                   "reject".equalsIgnoreCase(status)) {
            repo.updateRobotStateInDatabase(location, "blocked", "blocked", currentActiveOrderId);
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
