package org.tensorflow.lite.examples.detection;

public class ContactModel {
    private String name;
    private String phoneNumber;

    public ContactModel(String name, String phoneNumber) {
        this.name = name;
        this.phoneNumber = phoneNumber;
    }

    public String getName() {
        return name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }
}

