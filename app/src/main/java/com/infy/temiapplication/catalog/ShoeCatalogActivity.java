package com.infy.temiapplication.catalog;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.infy.temiapplication.MainActivity;
import com.infy.temiapplication.R;
import com.infy.temiapplication.data.CartSession;
import com.infy.temiapplication.data.FirebaseRepo;
import com.infy.temiapplication.model.Shoe;

import java.util.ArrayList;
import java.util.List;

public class ShoeCatalogActivity extends AppCompatActivity {
    private static final int TIMEOUT_DELAY_MS = 90000; // 90 seconds

    private RecyclerView recyclerView;
    private ShoeCatalogAdapter adapter;
    private ImageButton btnBack;
    private FrameLayout btnCart;
    private TextView textCartBadge;

    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private final Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            CartSession.clear();
            Intent intent = new Intent(ShoeCatalogActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Toast.makeText(ShoeCatalogActivity.this, "Session reset due to inactivity.", Toast.LENGTH_LONG).show();
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shoe_catalog);

        recyclerView = findViewById(R.id.recycler_catalog);
        btnBack = findViewById(R.id.btn_catalog_back);
        btnCart = findViewById(R.id.btn_catalog_cart);
        textCartBadge = findViewById(R.id.text_cart_badge);

        // Setup Grid Layout with 3 Columns and SpanSizeLookup for Headers
        GridLayoutManager layoutManager = new GridLayoutManager(this, 3);
        adapter = new ShoeCatalogAdapter(this, new ArrayList<>(), shoe -> {
            // Open Shoe Details
            Intent intent = new Intent(ShoeCatalogActivity.this, ShoeDetailActivity.class);
            intent.putExtra("shoe_id", shoe.getId());
            startActivity(intent);
        });
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.isHeader(position) ? 3 : 1;
            }
        });
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        
        btnCart.setOnClickListener(v -> {
            Intent intent = new Intent(ShoeCatalogActivity.this, CartActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        resetTimeout();
        updateCartBadge();
        loadCatalogData();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimeout();
    }

    private void loadCatalogData() {
        FirebaseRepo.getInstance().getCatalog(new FirebaseRepo.CatalogCallback() {
            @Override
            public void onCatalogLoaded(List<Shoe> catalog) {
                runOnUiThread(() -> adapter.updateList(catalog));
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> Toast.makeText(ShoeCatalogActivity.this, "Failed to load catalog.", Toast.LENGTH_LONG).show());
            }
        });
    }

    private void updateCartBadge() {
        int count = CartSession.getTotalCount();
        if (count > 0) {
            textCartBadge.setText(String.valueOf(count));
            textCartBadge.setVisibility(View.VISIBLE);
        } else {
            textCartBadge.setVisibility(View.GONE);
        }
    }

    // Inactivity Timeout Management
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
