package com.infy.temiapplication.catalog;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.infy.temiapplication.R;
import com.infy.temiapplication.data.CartSession;
import com.infy.temiapplication.data.FirebaseRepo;
import com.infy.temiapplication.model.CartItem;

import java.util.List;
import java.util.Locale;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.ViewHolder> {

    public interface OnCartChangedListener {
        void onCartUpdated();
    }

    private final Context context;
    private final List<CartItem> cartItems;
    private final OnCartChangedListener listener;

    public CartAdapter(Context context, List<CartItem> cartItems, OnCartChangedListener listener) {
        this.context = context;
        this.cartItems = cartItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_cart_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final CartItem item = cartItems.get(position);

        holder.textName.setText(item.getShoeName());
        holder.textDetails.setText(String.format(Locale.US, "Color: %s | Size: %d | Qty: %d", 
                item.getColor().substring(0, 1).toUpperCase(Locale.ROOT) + item.getColor().substring(1), 
                item.getSize(), 
                item.getQty()));
        holder.textSubtotal.setText(String.format(Locale.US, "$%.2f", item.getTotalPrice()));

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
            case "sneaker_low":
            default:
                fillResId = R.drawable.shoe_sneaker_low_side_fill;
                detailsResId = R.drawable.shoe_sneaker_low_side_details;
                break;
        }

        holder.imageFill.setImageResource(fillResId);
        holder.imageDetails.setImageResource(detailsResId);

        // Apply programmatic color tint
        if (item.getColorHex() != null) {
            try {
                holder.imageFill.setColorFilter(Color.parseColor(item.getColorHex()), PorterDuff.Mode.SRC_IN);
            } catch (Exception e) {
                holder.imageFill.clearColorFilter();
            }
        }

        holder.textQtyValue.setText(String.valueOf(item.getQty()));

        // Setup increment click (checking real-time Firebase stock limits)
        holder.btnQtyPlus.setOnClickListener(v -> {
            com.infy.temiapplication.model.Shoe shoe = FirebaseRepo.getInstance().getCachedShoe(item.getShoeId());
            int stock = shoe != null ? shoe.getStockFor(item.getColor(), item.getSize()) : 999;
            if (item.getQty() < stock) {
                item.setQty(item.getQty() + 1);
                notifyDataSetChanged();
                if (listener != null) {
                    listener.onCartUpdated();
                }
            } else {
                android.widget.Toast.makeText(context, "Cannot add more. Stock limit reached.", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        // Setup decrement click (removes item if quantity drops below 1)
        holder.btnQtyMinus.setOnClickListener(v -> {
            if (item.getQty() > 1) {
                item.setQty(item.getQty() - 1);
                notifyDataSetChanged();
                if (listener != null) {
                    listener.onCartUpdated();
                }
            } else {
                CartSession.removeItem(position);
                notifyDataSetChanged();
                if (listener != null) {
                    listener.onCartUpdated();
                }
            }
        });

        // Setup delete click
        holder.btnDelete.setOnClickListener(v -> {
            CartSession.removeItem(position);
            notifyDataSetChanged();
            if (listener != null) {
                listener.onCartUpdated();
            }
        });
    }

    @Override
    public int getItemCount() {
        return cartItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageFill;
        ImageView imageDetails;
        TextView textName;
        TextView textDetails;
        TextView textSubtotal;
        ImageButton btnQtyMinus;
        ImageButton btnQtyPlus;
        TextView textQtyValue;
        ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageFill = itemView.findViewById(R.id.cart_row_shoe_fill);
            imageDetails = itemView.findViewById(R.id.cart_row_shoe_details);
            textName = itemView.findViewById(R.id.cart_row_name);
            textDetails = itemView.findViewById(R.id.cart_row_details);
            textSubtotal = itemView.findViewById(R.id.cart_row_price);
            btnQtyMinus = itemView.findViewById(R.id.btn_cart_qty_minus);
            btnQtyPlus = itemView.findViewById(R.id.btn_cart_qty_plus);
            textQtyValue = itemView.findViewById(R.id.text_cart_qty_value);
            btnDelete = itemView.findViewById(R.id.btn_delete_cart_row);
        }
    }
}
