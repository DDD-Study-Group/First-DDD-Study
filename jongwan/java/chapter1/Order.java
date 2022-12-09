package chapter1;

import java.util.List;
import java.util.Objects;

public class Order {
    String orderNumber;
    private OrderState state;
    private ShippingInfo shippingInfo;

    //상품리스트
    private List<OrderLine> orderLines;
    private Money totalAmounts;

    public Order(List<OrderLine> orderLines, ShippingInfo shippingInfo) {
        setShippingInfo(shippingInfo);
        setOrderLines(orderLines);
    }

    private void setShippingInfo(ShippingInfo shippingInfo) {
        if(shippingInfo == null) throw new IllegalArgumentException("배송 정보가 없어요.");
        this.shippingInfo = shippingInfo;
    }

    private void setOrderLines(List<OrderLine> orderLines) {
        verifyAtLastOneOrMoreOrderLines(orderLines);
        this.orderLines = orderLines;
        calculateTotalAmounts();
    }
    private void verifyAtLastOneOrMoreOrderLines(List<OrderLine> orderLines) {
        if(orderLines == null || orderLines.isEmpty()){
            throw new IllegalArgumentException("구매할 상품이 없어요!!");
        }
    }
    private void calculateTotalAmounts() {
        int sum = this.orderLines.stream().mapToInt(x -> x.getAmounts()).sum();
        this.totalAmounts = new Money(sum);
    }

    public void changeShippingInfo(ShippingInfo shippingInfo){
        verifyNotYetShipped();
        setShippingInfo(shippingInfo);
        if(!isShippingChangeable()){
            throw new IllegalStateException("배송 변경 불가 ::: " + state);
        }
        this.shippingInfo = shippingInfo;
    }

    public void cancel(){
        verifyNotYetShipped();
        this.state = OrderState.CANCELLED;
    }

    private void verifyNotYetShipped() {
        if(this.state != OrderState.PAYMENT_WAITING && this.state != OrderState.PREPARING)
            throw new IllegalStateException("이미 배송이 완료");
    }

    private boolean isShippingChangeable() {
        return state == OrderState.PAYMENT_WAITING ||
                state == OrderState.PREPARING;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(orderNumber, order.orderNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderNumber);
    }
}

