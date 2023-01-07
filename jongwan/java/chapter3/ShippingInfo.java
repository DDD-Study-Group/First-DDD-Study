package chapter3;

import java.util.Objects;

public class ShippingInfo {
    private String receiverName;
    private String receiverPhoneNumber;
    private final Address address;

    public ShippingInfo(String receiverName, String receiverPhoneNumber, Address address) {
        this.receiverName = receiverName;
        this.receiverPhoneNumber = receiverPhoneNumber;
        this.address = address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShippingInfo that = (ShippingInfo) o;
        return Objects.equals(receiverName, that.receiverName) && Objects.equals(receiverPhoneNumber, that.receiverPhoneNumber);
    }
    @Override
    public int hashCode() {
        return Objects.hash(receiverName, receiverPhoneNumber);
    }
}
