package com.infy.temiapplication.catalog;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.infy.temiapplication.MainActivity;
import com.infy.temiapplication.R;
import com.infy.temiapplication.data.CartSession;
import com.infy.temiapplication.data.FirebaseRepo;

import java.util.Locale;

public class CartActivity extends AppCompatActivity {
    private static final int TIMEOUT_DELAY_MS = 90000;

    private RecyclerView recyclerView;
    private CartAdapter adapter;
    private ImageButton btnBack;
    private TextView btnClearAll;
    private TextView textTotalPrice;
    private MaterialButton btnPlaceOrder;
    private LinearLayout layoutEmptyCart;
    private ConstraintLayout cardCheckoutSummary;

    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private final Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            CartSession.clear();
            Intent intent = new Intent(CartActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Toast.makeText(CartActivity.this, "Session reset due to inactivity.", Toast.LENGTH_LONG).show();
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        recyclerView = findViewById(R.id.recycler_cart);
        btnBack = findViewById(R.id.btn_cart_back);
        btnClearAll = findViewById(R.id.btn_clear_cart);
        textTotalPrice = findViewById(R.id.text_cart_total_price);
        btnPlaceOrder = findViewById(R.id.btn_place_order);
        layoutEmptyCart = findViewById(R.id.layout_empty_cart);
        cardCheckoutSummary = findViewById(R.id.card_checkout_summary);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CartAdapter(this, CartSession.getCartItems(), this::updateCartUI);
        recyclerView.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());

        btnClearAll.setOnClickListener(v -> {
            CartSession.clear();
            adapter.notifyDataSetChanged();
            updateCartUI();
            Toast.makeText(CartActivity.this, "Cart cleared", Toast.LENGTH_SHORT).show();
        });

        btnPlaceOrder.setOnClickListener(v -> {
            btnPlaceOrder.setEnabled(false); // Disable to prevent double submission
            submitOrderToRepo();
        });

        updateCartUI();
    }

    private void updateCartUI() {
        double totalPrice = CartSession.getTotalPrice();
        textTotalPrice.setText(String.format(Locale.US, "$%.2f", totalPrice));

        if (CartSession.getCartItems().isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            cardCheckoutSummary.setVisibility(View.GONE);
            btnClearAll.setVisibility(View.GONE);
            layoutEmptyCart.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            cardCheckoutSummary.setVisibility(View.VISIBLE);
            btnClearAll.setVisibility(View.VISIBLE);
            layoutEmptyCart.setVisibility(View.GONE);
        }
    }

    private void submitOrderToRepo() {
        FirebaseRepo.getInstance().submitOrder(CartSession.getCartItems(), new FirebaseRepo.OrderCallback() {
            @Override
            public void onOrderSuccess(final String orderId) {
                runOnUiThread(() -> {
                    Toast.makeText(CartActivity.this, "Order placed successfully! Delivery starting.", Toast.LENGTH_LONG).show();
                    CartSession.clear();
                    
                    // Route back to MainActivity which handles the traveling overlay states
                    Intent intent = new Intent(CartActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onOrderFailed(final String reason) {
                runOnUiThread(() -> {
                    Toast.makeText(CartActivity.this, "Order Failed: " + reason, Toast.LENGTH_LONG).show();
                    btnPlaceOrder.setEnabled(true); // Re-enable so they can fix their cart or retry
                });
            }
        });
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
