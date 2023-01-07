package chapter3;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class Ch3Test {

    @Test
    @DisplayName("애그리거트 루트는 내부 밸류 객체를 조합해서 기능을 완성 한다.")
    void getOrderTotalAmount(){
        ArrayList orderLines = new ArrayList<OrderLine>();
        orderLines.add(new OrderLine(new Product("상품1","001"), new Money(100),1,new Money(200)));
        orderLines.add(new OrderLine(new Product("상품2","002"), new Money(100),1,new Money(200)));
        orderLines.add(new OrderLine(new Product("상품3","003"), new Money(100),1,new Money(200)));
        Address address = new Address("1번지","2번지","123-333");
        ShippingInfo shippingInfo = new ShippingInfo("이름","01000000000",address);
        Order order = new Order(OrderState.PAYMENT_WAITING,shippingInfo,orderLines);
        Money money = order.getTotalAmounts();
        Assertions.assertEquals(300,money.getValue());
    }

    @Test
    @DisplayName("애그리거트 루트가 내부 구성요소 객체에게 기능 실행을 위임한다.")
    void test(){
        ArrayList orderLines = new ArrayList<OrderLine>();
        orderLines.add(new OrderLine(new Product("notOrderAbleProduct","003"), new Money(100),1,new Money(200)));
        Address address = new Address("1번지","2번지","123-333");
        ShippingInfo shippingInfo = new ShippingInfo("이름","01000000000",address);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> { Order order = new Order(OrderState.PAYMENT_WAITING,shippingInfo,orderLines); }
        );
        Assertions.assertEquals("못사는 상품이에요", exception.getMessage());
    }
}
