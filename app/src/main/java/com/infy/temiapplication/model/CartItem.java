package com.infy.temiapplication.model;

import java.io.Serializable;

public class CartItem implements Serializable {
    private String shoeId;
    private String shoeName;
    private String brand;
    private String shapeSet;
    private String color;
    private String colorHex;
    private int size;
    private int qty;
    private double price;

    public CartItem() {}

    public CartItem(String shoeId, String shoeName, String brand, String shapeSet,
                    String color, String colorHex, int size, int qty, double price) {
        this.shoeId = shoeId;
        this.shoeName = shoeName;
        this.brand = brand;
        this.shapeSet = shapeSet;
        this.color = color;
        this.colorHex = colorHex;
        this.size = size;
        this.qty = qty;
        this.price = price;
    }

    public String getShoeId() { return shoeId; }
    public void setShoeId(String shoeId) { this.shoeId = shoeId; }

    public String getShoeName() { return shoeName; }
    public void setShoeName(String shoeName) { this.shoeName = shoeName; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getShapeSet() { return shapeSet; }
    public void setShapeSet(String shapeSet) { this.shapeSet = shapeSet; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public double getTotalPrice() {
        return price * qty;
    }
}
