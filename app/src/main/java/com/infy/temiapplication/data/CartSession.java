package com.infy.temiapplication.data;

import com.infy.temiapplication.model.CartItem;

import java.util.ArrayList;
import java.util.List;

public class CartSession {
    private static final List<CartItem> cartItems = new ArrayList<>();

    public static synchronized List<CartItem> getCartItems() {
        return cartItems;
    }

    public static synchronized void addItem(CartItem newItem) {
        // Check if matching shoe variant already in cart, increment quantity if so
        for (CartItem item : cartItems) {
            if (item.getShoeId().equals(newItem.getShoeId()) &&
                item.getColor().equalsIgnoreCase(newItem.getColor()) &&
                item.getSize() == newItem.getSize()) {
                item.setQty(item.getQty() + newItem.getQty());
                return;
            }
        }
        cartItems.add(newItem);
    }

    public static synchronized void removeItem(int index) {
        if (index >= 0 && index < cartItems.size()) {
            cartItems.remove(index);
        }
    }

    public static synchronized void clear() {
        cartItems.clear();
    }

    public static synchronized int getTotalCount() {
        int total = 0;
        for (CartItem item : cartItems) {
            total += item.getQty();
        }
        return total;
    }

    public static synchronized double getTotalPrice() {
        double total = 0.0;
        for (CartItem item : cartItems) {
            total += item.getTotalPrice();
        }
        return total;
    }
}
