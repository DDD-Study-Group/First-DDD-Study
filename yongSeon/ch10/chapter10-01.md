## 시스템간의 강결합의 문제점
쇼핑몰에서 구매를 취소하면 환불을 처리해야한다.
이때 환불을 처리하는 주체는 주문 도메인 엔티티가 될 수 있다. 아래와 같이 환불 기능을 제공하는 도메인은 도메인 서비스를 통해 실행하게 된다.


- 도메인 객체로 처리
```java
public  class Order {

    public void cancel(RefundService refundService) {
        verifyNotYetShipped();
        this.status = OrderStatus.CANCELED;

        try {
            refundService.refund(this);
            this.status = OrderStatus.COMPLETE_REFUND;
        } catch (Exception e) {
            throw e;
        }
    }
}
```
- 응용 서비스로 환불 처리
```java
public class cancelOrderService {
    private RefundService refundService;
    
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId);
        order.cancel();
        order.refundStarted();
        
        try {
            refundService.refund(order);
            order.refundCompleted();
        } catch (Exception e) {
            order.refundFailed();
            throw e;
        }
    }
}
```
- 결제시스템은 보통 외부에 존재하기 때문에 도메인 서비스를 통해 환불을 요청한다. 이로인해 문제점이 발생하게 되는데 아래와 같다. 

###  문제점
- 외부 서비스가 정상이 아니라면 트랜잭션 처리를 어떻게 해야할지 애매하다. 롤백하는 것이 맞아보이지만 주문취소 상태로 하고 나중에 다시 시도하는
시나리오로 갈 수 있다.
- 성능에 대한 문제점이 있는데 외부 응답 시간이 길어지면 내부 시스템의 응답 시간이 길어지게 된다. 이로인해 외부시스템의 성능의 직접적인 영향을 받게 된다.
- 도메인 객체의 서비스가 전달 되면 설계상 문제가 발생할 수 있다. 주문과 결제로직이 결합되어있기 때문에 결제시스템이 변경되면 주문도메인도 변경되어야 한다.
- 알림시스템도 추가되게 된다면 트랜잭션을 처리하기가 매이 까다로워진다. 

### 해결책
- 주문 바운디드 컨텍스트 간의 강결합으로 인해 결제 바운디드 컨텍스트까지 영향을 사용하는 것이다.
- 이벤트를 활용하면 해결할 수 있다. 비동기 이벤트를 활용하면 특히 결합을 크게 낮출 수 있다.

## 이벤트 개요
- 이벤트란 어떤 것이 바뀌는 것을 의미한다. 도메인 모델에서 이벤트는 이벤트는 도메인 객체의 상태가 바뀌는 것을 의미한다.
- 예를들어 주문을 취소하면 취소 상태로 바뀌고 이벤트를 활용하여 바뀔 수 있다.

### 이벤트 관련 구성 요소
- 도메인 모델에 이벤트를 도입하기 위해서는 아래와 같은 구성 요소가 필요하다.
- 이벤트, 이벤트 생성 주체, 이벤트 디스패처(퍼블리셔), 이벤트 핸들러(구독자)
> 이벤트 생성 주체 --이벤트--> 이벤트 디스패처 --이벤트--> 이벤트 핸들러

- 이벤트 생성 주체: 엔티티, 벨류, 도메인 서비스와 같은 도메인 객체이다. 도메인 로직으로 상태가 바뀌면 이벤트를 발생한다.
- 이벤트 핸들러: 전달된 이벤트에 반응한다. 주문 취소 알림을 전달하거나 결제 취소를 요청하는 등의 기능을 수행한다.
- 이벤트 디스패처: 이벤트 생성 주체와 이벤트 핸들러 사이의 중간자 역할을 한다. 이벤트를 처리할 수 있는 핸들러에게 전파한다. 구현 방식에 따라 동기나 비동기로 실행한다.

### 이벤트 구성
- 이벤트는 발생한 이벤트에 대한 정보를 담는다.
> 이벤트 종류: 클래스 이름으로 이벤트 종류로 표현  
> 이벤트 발생 시간  
> 추가 데이터: 주문번호, 신규 배송지 정보 등
- 예시
```java
public class ShippingInfoChangedEvent {
    private Long orderId;
    private LocalDateTime eventTimeStamp;
    private ShippingInfo newShippingInfo;
}
```
클래스 이름을 보면 과거 시제를 사용했다. 이벤트는 현재 기준으로 과거에 발생했으므로 이벤트이름에 과거시제를 사용한다.
```java
public class Order {
    public void changeShippingInfo(ShippingInfo newShippingInfo) {
        verifyNotYetShipped();
        this.shippingInfo = newShippingInfo;
        Events.raise(new ShippingInfoChangedEvent(this.id, LocalDateTime.now(), newShippingInfo));
    }
}
```
- Order 애그리거트를 통해 이벤트가 발생하고 `Events.raise()`를 통해 이벤트를 발생시킨다. 이벤트는 이벤트디스패처에게 전달한다.

```java
public class OrderEventHandler {
    @EventHandler(ShippingInfoChangedEvent.class)
    public void handle(ShippingInfoChangedEvent event) {
        // 주문 취소 알림을 전달하거나 결제 취소를 요청하는 등의 기능을 수행한다.
    }
}
```
- 핸들러는 디스패처러부터 이벤트를 전달받아 필요한 작업을 수행한다.
- 이벤트는 핸들러가 기능을 수행하기 위한 정보를 담아야 한다. 그렇지 않으면 아래와 같이 API, DB등을 호출하여 읽어와야한다.
- 그렇다고 이벤트에 불필요한 정보를 담을 필요는 없다.
```java
public class OrderEventHandler {
    @EventHandler(ShippingInfoChangedEvent.class)
    public void handle(ShippingInfoChangedEvent event) {
        // 주문 취소 알림을 전달하거나 결제 취소를 요청하는 등의 기능을 수행한다.
        Order order = orderRepository.findById(event.getOrderId());
        shippingInfoSyncronizer.sync(
                order.getId(),
                order.getShippingInfo()
        );
    }
}
```

## 이벤트 용도
### 트리거로 사용하는 경우
- 도메인 상태가 바뀔 때 마다 후처리가 필요하면 트리거로 이벤트를 사용할 수 있다. 주문에서는 주문취소 이벤트를 트리거로 사용 할 수 있다.
> **Order** --주문취소--> **OrderEventHandler** --주문취소 알림--> **OrderEventHandler** --> **RefundService**

### 이벤트 장점
- 구매 취소 로직에 이벤트를 적용함으로써 cancel() 에 있던 환불을 처리하기 위한 파라미터가 사라졌다.
- 주문 도메인에서 결제 도메인간의 의존성을 없앰으로써 결제 도메인이 변경되어도 주문 도메인에 영향을 주지 않는다.
- 주문 취소 알림을 전달하는 기능을 EventDispatcher에게 위임함으로써 주문 도메인에서는 알림 기능에 대한 구현을 알 필요가 없고 기능확장에도 유리해졌다.

![img.png](image%2Fimg.png)

## 이벤트, 핸들러, 디스패처 구현
- 이벤트 클래스: 이벤트 표현
- 디스패처: 스프링이 제공하는 ApplicationEventPublisher를 이용한다.
- Events: 이벤트를 발행한다. 이벤트 발행을 위해 스프링이 제공하는 ApplicationEventPublisher를 사용한다.
- 이벤트 핸들러: 이벤트를 수신해서 처리한다. 

### 이벤트 클래스
- 이벤트 자체에서는 상위 타입이 존재하지 않지만 공통된 부분을 추출하여 상위 타입을 만들 수 있다.
- 이벤트는 과거시제를 사용해야하며 이벤트를 처리하기 위한 최소한의 정보를 담고 있어야 한다.

- 공통 추상 클래스
```java
public abstract class Event {
    private LocalDateTime eventTimeStamp;
}
```
- 이벤트
```java
@Getter
@AllArgsConstructor
public class OrderCanceledEvent extends Event {
    private Long orderId;
   
}
```

### Events 클래스와 ApplicationEventPublisher 
- 이벤트 발생과 출판을 위해 스프링이 제공하는 ApplicationEventPublisher를 사용한다.
- 스프링 컨테이너는 ApplicationEventPublisher도 된다?
````java
public class Events {
    private static ApplicationEventPublisher publisher;

    public static void setPublisher(ApplicationEventPublisher publisher) {
        Events.publisher = publisher;
    }

    public static void raise(Event event) {
        publisher.publishEvent(event);
    }
}
````
- Events 클래스의 raise() 메서드를 통해 이벤트를 발생시킨다.
- Events 클래스가 사용할 setPublisher() 메서드는 스프링이 제공하는 ApplicationEventPublisher를 주입받아 사용한다.

```java 
@Configuration
public class EventConfig {
    @Autowired 
    private ApplicateionContext applicateionContext;
    @Bean
    public InitializerBean eventsInitializer() {
        return () -> Events.setPublisher(applicateionContext);
    }
}
```
- eventsInitializer() 메서드는 Initializer 타입 객체를 빈으로 설정한다.

### 10.3.3 이벤트 발생과 이벤트
![img_1.png](image%2Fimg_1.png)
1. 이벤트 처리에 필요한 이벤트 핸들러를 생성한다.
2. 이벤트 발생 전에 이벤트 핸들러를 Events.handle() 메서드를 이용해 등록한다.
3. 이벤트를 발생하는 도메인 기능을 실행한다.
4. 도메인은 Events.raise()를 이용해서 이벤트를 발생시킨다.
5. Events.raise()는 등록된 핸들러의 canHandle()을 이용해서 이벤트를 처리할 수
   있는지 확인한다.
6. 핸들러가 이벤트를 처리할 수 있다면 handle() 메서드를 이용해서 이벤트를 처리한다.
7. Events.raise() 실행을 끝내고 리턴한다.
8. 도메인 기능 실행을 끝내고 리턴한다.
9. Events.reset()을 이용해서 ThreadLocal을 초기화한다.