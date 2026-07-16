package com.infy.temiapplication.catalog;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.infy.temiapplication.R;
import com.infy.temiapplication.model.Shoe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ShoeCatalogAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Shoe shoe);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_SHOE = 1;

    private final Context context;
    private final List<Object> itemList = new ArrayList<>();
    private final OnItemClickListener listener;

    public ShoeCatalogAdapter(Context context, List<Shoe> shoeList, OnItemClickListener listener) {
        this.context = context;
        this.listener = listener;
        updateList(shoeList);
    }

    @Override
    public int getItemViewType(int position) {
        if (itemList.get(position) instanceof String) {
            return TYPE_HEADER;
        } else {
            return TYPE_SHOE;
        }
    }

    public boolean isHeader(int position) {
        return getItemViewType(position) == TYPE_HEADER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_catalog_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_shoe_card, parent, false);
            return new ShoeViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_HEADER) {
            HeaderViewHolder hHolder = (HeaderViewHolder) holder;
            String title = (String) itemList.get(position);
            hHolder.textTitle.setText(title);
        } else {
            ShoeViewHolder sHolder = (ShoeViewHolder) holder;
            final Shoe shoe = (Shoe) itemList.get(position);

            sHolder.textBrand.setText(shoe.getBrand().toUpperCase(Locale.ROOT));
            sHolder.textName.setText(shoe.getName());
            sHolder.textPrice.setText(String.format(Locale.US, "$%.2f", shoe.getPrice()));

            // Resolve Vector Drawables based on shapeSet
            int fillResId = R.drawable.shoe_sneaker_low_side_fill;
            int detailsResId = R.drawable.shoe_sneaker_low_side_details;

            String shape = shoe.getShapeSet() != null ? shoe.getShapeSet() : "sneaker_low";
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

            sHolder.imageFill.setImageResource(fillResId);
            sHolder.imageDetails.setImageResource(detailsResId);

            // Apply dynamic color tinting using the first available color in catalog as default preview
            if (shoe.getColors() != null && !shoe.getColors().isEmpty()) {
                String defaultColor = shoe.getColors().get(0);
                if (shoe.getColorHex() != null && shoe.getColorHex().containsKey(defaultColor)) {
                    String hexColor = shoe.getColorHex().get(defaultColor);
                    if (hexColor != null) {
                        try {
                            sHolder.imageFill.setColorFilter(Color.parseColor(hexColor), PorterDuff.Mode.SRC_IN);
                        } catch (Exception e) {
                            sHolder.imageFill.clearColorFilter();
                        }
                    }
                }
            }

            sHolder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(shoe);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    public void updateList(List<Shoe> newList) {
        this.itemList.clear();
        if (newList == null || newList.isEmpty()) {
            notifyDataSetChanged();
            return;
        }

        // Group shoes by category
        List<Shoe> athletic = new ArrayList<>();
        List<Shoe> casual = new ArrayList<>();
        List<Shoe> formal = new ArrayList<>();

        for (Shoe shoe : newList) {
            String cat = shoe.getCategory();
            if (cat == null) cat = "Casual & Sandals";
            if (cat.equalsIgnoreCase("Athletic & Basketball")) {
                athletic.add(shoe);
            } else if (cat.equalsIgnoreCase("Formal Classics")) {
                formal.add(shoe);
            } else {
                casual.add(shoe);
            }
        }

        if (!athletic.isEmpty()) {
            itemList.add("Athletic & Basketball");
            itemList.addAll(athletic);
        }
        if (!casual.isEmpty()) {
            itemList.add("Casual & Sandals");
            itemList.addAll(casual);
        }
        if (!formal.isEmpty()) {
            itemList.add("Formal Classics");
            itemList.addAll(formal);
        }

        notifyDataSetChanged();
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView textTitle;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.text_catalog_header_title);
        }
    }

    public static class ShoeViewHolder extends RecyclerView.ViewHolder {
        TextView textBrand;
        TextView textName;
        TextView textPrice;
        ImageView imageFill;
        ImageView imageDetails;

        public ShoeViewHolder(@NonNull View itemView) {
            super(itemView);
            textBrand = itemView.findViewById(R.id.card_brand);
            textName = itemView.findViewById(R.id.card_name);
            textPrice = itemView.findViewById(R.id.card_price);
            imageFill = itemView.findViewById(R.id.card_shoe_fill);
            imageDetails = itemView.findViewById(R.id.card_shoe_details);
        }
    }
}
