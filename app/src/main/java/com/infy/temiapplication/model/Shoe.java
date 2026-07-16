package com.infy.temiapplication.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Shoe implements Serializable {
    private String id;
    private String name;
    private String brand;
    private String category;
    private String shapeSet;
    private List<String> colors = new ArrayList<>();
    private Map<String, String> colorHex = new HashMap<>();
    private List<Integer> sizes = new ArrayList<>();
    private Map<String, Integer> stock = new HashMap<>(); // Keyed "color_size" -> e.g. "black_9"
    private double price;

    // Default constructor for Firebase deserialization
    public Shoe() {}

    public Shoe(String id, String name, String brand, String category, String shapeSet,
                List<String> colors, Map<String, String> colorHex, List<Integer> sizes,
                Map<String, Integer> stock, double price) {
        this.id = id;
        this.name = name;
        this.brand = brand;
        this.category = category;
        this.shapeSet = shapeSet;
        this.colors = colors;
        this.colorHex = colorHex;
        this.sizes = sizes;
        this.stock = stock;
        this.price = price;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getShapeSet() { return shapeSet; }
    public void setShapeSet(String shapeSet) { this.shapeSet = shapeSet; }

    public List<String> getColors() { return colors; }
    public void setColors(List<String> colors) { this.colors = colors; }

    public Map<String, String> getColorHex() { return colorHex; }
    public void setColorHex(Map<String, String> colorHex) { this.colorHex = colorHex; }

    public List<Integer> getSizes() { return sizes; }
    public void setSizes(List<Integer> sizes) { this.sizes = sizes; }

    public Map<String, Integer> getStock() { return stock; }
    public void setStock(Map<String, Integer> stock) { this.stock = stock; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    // Helper: Get stock for a specific color + size combination
    public int getStockFor(String color, int size) {
        String key = color.toLowerCase() + "_" + size;
        if (stock != null && stock.containsKey(key)) {
            Integer s = stock.get(key);
            return s != null ? s : 0;
        }
        return 0;
    }

    // Helper: Is size in stock in ANY color?
    public boolean isSizeAvailableInAnyColor(int size) {
        for (String color : colors) {
            if (getStockFor(color, size) > 0) {
                return true;
            }
        }
        return false;
    }

    // Helper: Is color in stock in ANY size?
    public boolean isColorAvailableInAnySize(String color) {
        for (int size : sizes) {
            if (getStockFor(color, size) > 0) {
                return true;
            }
        }
        return false;
    }

    // Helper: Find total stock for a given color
    public int getTotalStockForColor(String color) {
        int total = 0;
        for (int size : sizes) {
            total += getStockFor(color, size);
        }
        return total;
    }
}
