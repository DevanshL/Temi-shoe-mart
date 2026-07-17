package com.infy.temiapplication.catalog;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.infy.temiapplication.MainActivity;
import com.infy.temiapplication.R;
import com.infy.temiapplication.data.CartSession;
import com.infy.temiapplication.data.FirebaseRepo;
import com.infy.temiapplication.model.CartItem;
import com.infy.temiapplication.model.Shoe;

import java.util.Locale;

public class ShoeDetailActivity extends AppCompatActivity {
    private static final int TIMEOUT_DELAY_MS = 60000; // 60 seconds

    private Shoe shoe;
    private String selectedAngle = "side";
    private String selectedColor = "";
    private int selectedSize = -1;
    private int quantity = 1;
    private FirebaseRepo.CatalogCallback catalogCallback;

    // UI elements
    private ImageView imageShoeFill;
    private ImageView imageShoeDetails;
    private TextView tabSide, tabTop, tabSole;
    private TextView textBrand, textName, textPrice;
    private LinearLayout layoutColorSwatches;
    private LinearLayout layoutSizeChips;
    private LinearLayout layoutStockStatus;
    private TextView textStockStatus;
    private TextView textQtyValue;
    private ImageButton btnQtyMinus;
    private ImageButton btnQtyPlus;
    private MaterialButton btnAddToCart;
    private ImageButton btnBack;
    private ImageButton btnHome;

    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private final Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            CartSession.clear();
            Intent intent = new Intent(ShoeDetailActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Toast.makeText(ShoeDetailActivity.this, "Session reset due to inactivity.", Toast.LENGTH_LONG).show();
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shoe_detail);

        // Bind Views
        imageShoeFill = findViewById(R.id.detail_shoe_fill);
        imageShoeDetails = findViewById(R.id.detail_shoe_details);
        tabSide = findViewById(R.id.tab_side);
        tabTop = findViewById(R.id.tab_top);
        tabSole = findViewById(R.id.tab_sole);
        textBrand = findViewById(R.id.text_detail_brand);
        textName = findViewById(R.id.text_detail_name);
        textPrice = findViewById(R.id.text_detail_price);
        layoutColorSwatches = findViewById(R.id.layout_color_swatches);
        layoutSizeChips = findViewById(R.id.layout_size_chips);
        layoutStockStatus = findViewById(R.id.layout_stock_status);
        textStockStatus = findViewById(R.id.text_stock_status);
        textQtyValue = findViewById(R.id.text_qty_value);
        btnQtyMinus = findViewById(R.id.btn_qty_minus);
        btnQtyPlus = findViewById(R.id.btn_qty_plus);
        btnAddToCart = findViewById(R.id.btn_add_to_cart);
        btnBack = findViewById(R.id.btn_detail_back);
        btnHome = findViewById(R.id.btn_detail_home);

        // Retrieve Shoe Data
        String shoeId = getIntent().getStringExtra("shoe_id");
        if (shoeId == null) {
            Toast.makeText(this, "Product loading error", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        catalogCallback = new FirebaseRepo.CatalogCallback() {
            @Override
            public void onCatalogLoaded(java.util.List<Shoe> catalog) {
                for (Shoe s : catalog) {
                    if (s.getId().equals(shoeId)) {
                        shoe = s;
                        break;
                    }
                }
                if (shoe == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(ShoeDetailActivity.this, "Product not found", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                runOnUiThread(() -> initializeProductView());
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(ShoeDetailActivity.this, "Network error loading details.", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        };

        // Fetch catalog details
        FirebaseRepo.getInstance().getCatalog(catalogCallback);

        // Top Toolbar navigation hooks
        btnBack.setOnClickListener(v -> finish());
        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(ShoeDetailActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        // Setup Angle Tabs
        tabSide.setOnClickListener(v -> setAngle("side"));
        tabTop.setOnClickListener(v -> setAngle("top"));
        tabSole.setOnClickListener(v -> setAngle("sole"));

        // Setup Qty adjustments
        btnQtyMinus.setOnClickListener(v -> {
            if (quantity > 1) {
                quantity--;
                updateQtyUI();
            }
        });

        btnQtyPlus.setOnClickListener(v -> {
            int maxStock = shoe.getStockFor(selectedColor, selectedSize);
            if (quantity < maxStock) {
                quantity++;
                updateQtyUI();
            } else {
                Toast.makeText(ShoeDetailActivity.this, R.string.warning_stock_cap, Toast.LENGTH_SHORT).show();
            }
        });

        // Setup Add to Cart click
        btnAddToCart.setOnClickListener(v -> {
            btnAddToCart.setEnabled(false); // Double tap prevention
            addItemToCart();
        });
    }

    private void initializeProductView() {
        textBrand.setText(shoe.getBrand().toUpperCase(Locale.ROOT));
        textName.setText(shoe.getName());
        textPrice.setText(String.format(Locale.US, "$%.2f", shoe.getPrice()));

        // Default Selections
        if (!shoe.getColors().isEmpty()) {
            selectedColor = shoe.getColors().get(0);
        }

        // Find first in-stock size for this default color
        for (int size : shoe.getSizes()) {
            if (shoe.getStockFor(selectedColor, size) > 0) {
                selectedSize = size;
                break;
            }
        }
        // If all sizes are out of stock for this color, find any first available size in any color
        if (selectedSize == -1 && !shoe.getSizes().isEmpty()) {
            for (int size : shoe.getSizes()) {
                if (shoe.isSizeAvailableInAnyColor(size)) {
                    selectedSize = size;
                    break;
                }
            }
            if (selectedSize == -1) {
                selectedSize = shoe.getSizes().get(0); // fallback
            }
        }

        updateAngleViews();
        drawColorSwatches();
        drawSizeChips();
        updateStockWarningBanner();
        updateQtyUI();
    }

    private void setAngle(String angle) {
        selectedAngle = angle;
        updateAngleTabsUI();
        updateAngleViews();
    }

    private void updateAngleTabsUI() {
        tabSide.setBackground(null);
        tabSide.setTextColor(getResources().getColor(R.color.text_secondary));
        tabSide.setTypeface(null, android.graphics.Typeface.NORMAL);

        tabTop.setBackground(null);
        tabTop.setTextColor(getResources().getColor(R.color.text_secondary));
        tabTop.setTypeface(null, android.graphics.Typeface.NORMAL);

        tabSole.setBackground(null);
        tabSole.setTextColor(getResources().getColor(R.color.text_secondary));
        tabSole.setTypeface(null, android.graphics.Typeface.NORMAL);

        TextView selectedTab = tabSide;
        if ("top".equals(selectedAngle)) selectedTab = tabTop;
        else if ("sole".equals(selectedAngle)) selectedTab = tabSole;

        selectedTab.setBackgroundResource(R.drawable.glass_card_bg);
        selectedTab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        selectedTab.setTextColor(getResources().getColor(R.color.text_primary));
        selectedTab.setTypeface(null, android.graphics.Typeface.BOLD);
    }

    private void updateAngleViews() {
        int fillRes = R.drawable.shoe_sneaker_low_side_fill;
        int detailsRes = R.drawable.shoe_sneaker_low_side_details;
        String shape = shoe.getShapeSet() != null ? shoe.getShapeSet() : "sneaker_low";

        switch (shape) {
            case "boot":
                if ("top".equals(selectedAngle)) {
                    fillRes = R.drawable.shoe_boot_top_fill;
                    detailsRes = R.drawable.shoe_boot_top_details;
                } else if ("sole".equals(selectedAngle)) {
                    fillRes = R.drawable.shoe_boot_sole_fill;
                    detailsRes = R.drawable.shoe_boot_sole_details;
                } else {
                    fillRes = R.drawable.shoe_boot_side_fill;
                    detailsRes = R.drawable.shoe_boot_side_details;
                }
                break;
            case "sandal":
                if ("top".equals(selectedAngle)) {
                    fillRes = R.drawable.shoe_sandal_top_fill;
                    detailsRes = R.drawable.shoe_sandal_top_details;
                } else if ("sole".equals(selectedAngle)) {
                    fillRes = R.drawable.shoe_sandal_sole_fill;
                    detailsRes = R.drawable.shoe_sandal_sole_details;
                } else {
                    fillRes = R.drawable.shoe_sandal_side_fill;
                    detailsRes = R.drawable.shoe_sandal_side_details;
                }
                break;
            case "formal":
                if ("top".equals(selectedAngle)) {
                    fillRes = R.drawable.shoe_formal_top_fill;
                    detailsRes = R.drawable.shoe_formal_top_details;
                } else if ("sole".equals(selectedAngle)) {
                    fillRes = R.drawable.shoe_formal_sole_fill;
                    detailsRes = R.drawable.shoe_formal_sole_details;
                } else {
                    fillRes = R.drawable.shoe_formal_side_fill;
                    detailsRes = R.drawable.shoe_formal_side_details;
                }
                break;
            case "sneaker_high":
                if ("top".equals(selectedAngle)) {
                    fillRes = R.drawable.shoe_sneaker_low_top_fill;
                    detailsRes = R.drawable.shoe_sneaker_low_top_details;
                } else if ("sole".equals(selectedAngle)) {
                    fillRes = R.drawable.shoe_sneaker_low_sole_fill;
                    detailsRes = R.drawable.shoe_sneaker_low_sole_details;
                } else {
                    fillRes = R.drawable.shoe_sneaker_high_fill;
                    detailsRes = R.drawable.shoe_sneaker_high_details;
                }
                break;
            case "sneaker_sport":
                if ("top".equals(selectedAngle)) {
                    fillRes = R.drawable.shoe_sneaker_low_top_fill;
                    detailsRes = R.drawable.shoe_sneaker_low_top_details;
                } else if ("sole".equals(selectedAngle)) {
                    fillRes = R.drawable.shoe_sneaker_low_sole_fill;
                    detailsRes = R.drawable.shoe_sneaker_low_sole_details;
                } else {
                    fillRes = R.drawable.shoe_sneaker_sport_fill;
                    detailsRes = R.drawable.shoe_sneaker_sport_details;
                }
                break;
            case "sneaker_low":
            default:
                if ("top".equals(selectedAngle)) {
                    fillRes = R.drawable.shoe_sneaker_low_top_fill;
                    detailsRes = R.drawable.shoe_sneaker_low_top_details;
                } else if ("sole".equals(selectedAngle)) {
                    fillRes = R.drawable.shoe_sneaker_low_sole_fill;
                    detailsRes = R.drawable.shoe_sneaker_low_sole_details;
                } else {
                    fillRes = R.drawable.shoe_sneaker_low_side_fill;
                    detailsRes = R.drawable.shoe_sneaker_low_side_details;
                }
                break;
        }

        imageShoeFill.setImageResource(fillRes);
        imageShoeDetails.setImageResource(detailsRes);

        // Apply selected hex color filter to fill vector
        if (shoe.getColorHex() != null && shoe.getColorHex().containsKey(selectedColor)) {
            String hex = shoe.getColorHex().get(selectedColor);
            if (hex != null) {
                try {
                    imageShoeFill.setColorFilter(Color.parseColor(hex), PorterDuff.Mode.SRC_IN);
                } catch (Exception e) {
                    imageShoeFill.clearColorFilter();
                }
            }
        }
    }

    private void drawColorSwatches() {
        layoutColorSwatches.removeAllViews();
        if (shoe.getColors() == null) return;

        for (final String colorName : shoe.getColors()) {
            final String hex = shoe.getColorHex().get(colorName);
            if (hex == null) continue;

            final boolean isSelected = colorName.equalsIgnoreCase(selectedColor);
            
            // Check availability in selected size (Amazon style)
            int itemStock = shoe.getStockFor(colorName, selectedSize);
            boolean outOfStockInSize = itemStock == 0;

            // Frame layout wrapper
            FrameLayout container = new FrameLayout(this);
            LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(dpToPx(56), dpToPx(56));
            containerParams.setMargins(dpToPx(6), 0, dpToPx(6), 0);
            container.setLayoutParams(containerParams);

            // Ring view (highlights active selection)
            View ring = new View(this);
            FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(dpToPx(52), dpToPx(52));
            ringParams.gravity = Gravity.CENTER;
            ring.setLayoutParams(ringParams);

            if (isSelected) {
                GradientDrawable ringDrawable = new GradientDrawable();
                ringDrawable.setShape(GradientDrawable.OVAL);
                ringDrawable.setStroke(dpToPx(3), getResources().getColor(R.color.coral_primary));
                ring.setBackground(ringDrawable);
            }
            container.addView(ring);

            // Swatch circular color dot
            ImageView dot = new ImageView(this);
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dpToPx(38), dpToPx(38));
            dotParams.gravity = Gravity.CENTER;
            dot.setLayoutParams(dotParams);

            GradientDrawable dotDrawable = new GradientDrawable();
            dotDrawable.setShape(GradientDrawable.OVAL);
            dotDrawable.setColor(Color.parseColor(hex));
            dot.setBackground(dotDrawable);

            // Mute alpha if out of stock in selected size
            if (outOfStockInSize) {
                dot.setAlpha(0.25f);
            } else {
                dot.setAlpha(1.0f);
            }

            container.addView(dot);

            // Click listener
            container.setOnClickListener(v -> {
                selectedColor = colorName;
                updateAngleViews();
                drawColorSwatches();
                drawSizeChips();
                updateStockWarningBanner();
                
                // Keep quantity safe
                int maxStock = shoe.getStockFor(selectedColor, selectedSize);
                if (quantity > maxStock) {
                    quantity = Math.max(1, maxStock);
                }
                updateQtyUI();
            });

            layoutColorSwatches.addView(container);
        }
    }

    private void drawSizeChips() {
        layoutSizeChips.removeAllViews();
        if (shoe.getSizes() == null) return;

        for (final int sizeValue : shoe.getSizes()) {
            final boolean isSelected = sizeValue == selectedSize;
            final int sizeStock = shoe.getStockFor(selectedColor, sizeValue);
            final boolean isOutOfStock = sizeStock == 0;

            // Text View styled as a Chip
            TextView chip = new TextView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dpToPx(60), dpToPx(44));
            params.setMargins(dpToPx(6), 0, dpToPx(6), 0);
            chip.setLayoutParams(params);
            chip.setGravity(Gravity.CENTER);
            chip.setText(String.valueOf(sizeValue));
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            chip.setTypeface(null, android.graphics.Typeface.BOLD);

            // Style state checks
            if (isSelected) {
                // Active Selected Styling
                GradientDrawable background = new GradientDrawable();
                background.setShape(GradientDrawable.RECTANGLE);
                background.setCornerRadius(dpToPx(10));
                background.setColor(getResources().getColor(R.color.coral_primary));
                chip.setBackground(background);
                chip.setTextColor(Color.WHITE);
            } else if (isOutOfStock) {
                // Out of Stock styling: Disabled, greyed-out with Strikethrough
                GradientDrawable background = new GradientDrawable();
                background.setShape(GradientDrawable.RECTANGLE);
                background.setCornerRadius(dpToPx(10));
                background.setColor(Color.parseColor("#EAEAEA"));
                chip.setBackground(background);
                chip.setTextColor(getResources().getColor(R.color.text_disabled));
                chip.setPaintFlags(chip.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                chip.setEnabled(false); // Make unclickable
            } else {
                // Available Unselected Styling
                GradientDrawable background = new GradientDrawable();
                background.setShape(GradientDrawable.RECTANGLE);
                background.setCornerRadius(dpToPx(10));
                background.setColor(Color.WHITE);
                background.setStroke(dpToPx(1.5f), getResources().getColor(R.color.gray_border));
                chip.setBackground(background);
                chip.setTextColor(getResources().getColor(R.color.text_primary));
            }

            // Click listener
            if (!isOutOfStock) {
                chip.setOnClickListener(v -> {
                    selectedSize = sizeValue;
                    drawColorSwatches();
                    drawSizeChips();
                    updateStockWarningBanner();
                    
                    // Cap active qty to new stock limit
                    int maxStock = shoe.getStockFor(selectedColor, selectedSize);
                    if (quantity > maxStock) {
                        quantity = Math.max(1, maxStock);
                    }
                    updateQtyUI();
                });
            }

            layoutSizeChips.addView(chip);
        }
    }

    private void updateStockWarningBanner() {
        if (selectedSize == -1 || selectedColor.isEmpty()) {
            layoutStockStatus.setVisibility(View.GONE);
            btnAddToCart.setEnabled(false);
            btnQtyPlus.setEnabled(false);
            btnQtyMinus.setEnabled(false);
            return;
        }

        int activeStock = shoe.getStockFor(selectedColor, selectedSize);

        if (activeStock == 0) {
            // Completely out of stock in this specific color + size combo
            layoutStockStatus.setVisibility(View.VISIBLE);
            layoutStockStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FDEDED")));
            textStockStatus.setTextColor(Color.parseColor("#C62828"));
            
            // Build suggestion text for other colors in this size
            StringBuilder alternatives = new StringBuilder();
            for (String otherColor : shoe.getColors()) {
                if (shoe.getStockFor(otherColor, selectedSize) > 0) {
                    if (alternatives.length() > 0) alternatives.append(", ");
                    alternatives.append(otherColor.substring(0, 1).toUpperCase(Locale.ROOT) + otherColor.substring(1));
                }
            }

            if (alternatives.length() > 0) {
                textStockStatus.setText(String.format(Locale.US, "Out of stock in %s (Size %d). Other colors available: %s", 
                        selectedColor, selectedSize, alternatives.toString()));
            } else {
                textStockStatus.setText(String.format(Locale.US, "Out of stock in Size %d for all colors.", selectedSize));
            }

            btnAddToCart.setEnabled(false);
            btnAddToCart.setBackgroundColor(getResources().getColor(R.color.gray_disabled));
            btnQtyPlus.setEnabled(false);
            btnQtyMinus.setEnabled(false);
            quantity = 0;
            
        } else {
            // In stock
            layoutStockStatus.setVisibility(View.VISIBLE);
            btnAddToCart.setEnabled(true);
            btnAddToCart.setBackgroundColor(getResources().getColor(R.color.coral_primary));

            if (quantity == 0) {
                quantity = 1;
            }

            if (activeStock <= 3) {
                // Low stock warning (Amazon Style)
                layoutStockStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.coral_light)));
                textStockStatus.setTextColor(getResources().getColor(R.color.coral_dark));
                textStockStatus.setText(String.format(Locale.US, "Only %d left in stock - order soon!", activeStock));
            } else {
                // Plentiful stock
                layoutStockStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#EAF8EA")));
                textStockStatus.setTextColor(Color.parseColor("#2E7D32"));
                textStockStatus.setText(String.format(Locale.US, "In Stock (%d available)", activeStock));
            }
        }
    }

    private void updateQtyUI() {
        textQtyValue.setText(String.valueOf(quantity));
        int activeStock = shoe.getStockFor(selectedColor, selectedSize);

        if (activeStock == 0) {
            btnQtyMinus.setEnabled(false);
            btnQtyPlus.setEnabled(false);
            return;
        }

        btnQtyMinus.setEnabled(quantity > 1);
        btnQtyPlus.setEnabled(quantity < activeStock);
    }

    private void addItemToCart() {
        int activeStock = shoe.getStockFor(selectedColor, selectedSize);
        if (activeStock == 0) {
            Toast.makeText(this, "This variant is out of stock.", Toast.LENGTH_SHORT).show();
            btnAddToCart.setEnabled(true);
            return;
        }

        if (quantity > activeStock) {
            Toast.makeText(this, "Order exceeds available stock.", Toast.LENGTH_SHORT).show();
            quantity = activeStock;
            updateQtyUI();
            btnAddToCart.setEnabled(true);
            return;
        }

        String hex = shoe.getColorHex().get(selectedColor);
        CartItem item = new CartItem(
                shoe.getId(),
                shoe.getName(),
                shoe.getBrand(),
                shoe.getShapeSet(),
                selectedColor,
                hex,
                selectedSize,
                quantity,
                shoe.getPrice()
        );

        CartSession.addItem(item);
        Toast.makeText(this, "Added to cart!", Toast.LENGTH_SHORT).show();
        finish();
    }

    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    @Override
    protected void onResume() {
        super.onResume();
        resetTimeout();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimeout();
        FirebaseRepo.getInstance().removeCatalogCallback(catalogCallback);
    }

    private void resetTimeout() {
        timeoutHandler.removeCallbacks(timeoutRunnable);
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_DELAY_MS);
    }

    private void stopTimeout() {
        timeoutHandler.removeCallbacks(timeoutRunnable);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        resetTimeout();
    }
}
