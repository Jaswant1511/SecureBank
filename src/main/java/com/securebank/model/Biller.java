package com.securebank.model;

/**
 * Utility Biller entity representing utility, telecom, electricity, or municipal providers.
 */
public class Biller {

    private int billerId;
    private String billerName;
    private String category;
    private String billerCode;

    public Biller() {}

    public Biller(int billerId, String billerName, String category, String billerCode) {
        this.billerId = billerId;
        this.billerName = billerName;
        this.category = category;
        this.billerCode = billerCode;
    }

    public int getBillerId() {
        return billerId;
    }

    public void setBillerId(int billerId) {
        this.billerId = billerId;
    }

    public String getBillerName() {
        return billerName;
    }

    public void setBillerName(String billerName) {
        this.billerName = billerName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getBillerCode() {
        return billerCode;
    }

    public void setBillerCode(String billerCode) {
        this.billerCode = billerCode;
    }
}
