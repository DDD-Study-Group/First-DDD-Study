# 애그리거트
### 애그리거트 : 관련 객체를 하나로 묶은 군집
- 상위 수준에서 모델을 정리하면 도메인 모델 관계를 이해하는데 도움이 된다.
- 상위 수준에서 모델의 관계를 알아야, 추가 요구사항 반영이 쉽다.
- 상위 수준에서 모델을 조망할 수 있는방법이 바로 `애그리거트` 이다.
- `애그리거트`에 속한 객체는 동일한 사이프 사이클을 갖는다.
  - 주문 애그리거트는 Order, OrderLines, Orderer 과 함께 객체를 생성해야함.
- `애그리거트`는 경계를 갖는다.
  - 주문 애그리거트에서 배송지를 정하지만, 회원의 비밀번호를 변경하지 않는다.
  - 경계의 기준은 도메인 `규칙`과 `요구사항` 이다. 

### 애그리거트 루트
- 애그리거트에 속한 모든객체가 일관성을 유지해야하며, 이 `애그리거트 전체를 관리`하는 주체이다.
  - ex) 주문 애그리거트에서 주문 상품 갯수가 변경되면 총 금액도 변경이 되어야 한다. 이와 같이 전체 어그리거트의 데이터 일관성을 유지해주는 것이 `애그리거트 루트` 객체이다.

#### 애그리거트 루트 역할
- 도메인 규칙 제공
- 각 애그리거트가 제공해야 할 도메인 기능을 구현한다.
- 각 애그리거트에 속한 객체의 일관성이 깨지지 않도록 구현해야한다.
````JAVA
public class Order {
   private OrderState state;
   private ShippingInfo shippingInfo;
   
    //도메인 규칙 제공
    public void changeShippingInfo(ShippingInfo newShippingInfo){
        verifyNotYetShipped();//각 애그리거트가 제공해야 할 도메인 기능을 구현
        setShippingInfo(newShippingInfo);//애그리거트의 일관성이 깨지지 않도록 한다.
    }
    private void verifyNotYetShipped() {
        if(state != OrderState.PAYMENT_WAITING && state != OrderState.PREPARING)
            throw new IllegalStateException("already shipped");
    }
    private void setShippingInfo(ShippingInfo newShippingInfo) {
        shippingInfo = newShippingInfo;
    }
}
````
- 외부에서 애그리거트에 속한 객체를 직접 변경하면 안된다.

````JAVA
1. 논리적인 일관성이 깨질수 있다.(누락되거나, 잘못된 데이터가 저장될 수 있다.)
2. 불필요한 validation이 추가된다.
public class Order {
  private ShippingInfo shippingInfo;
  ...
}

public class business(){
    public orderPrepare(){
        Order order = new Order(new ShippingInfo("배송정보", new Address("1번지", "2번지", "123-333")));
        ShippingInfo si = order.getShippingInfo();
        if(order.state != OrderState.PAYMENT_WAITING && order.state != OrderState.PREPARING)
            throw new IllegalStateException("already shipped");
        si.setAddress(newAddress);//Shipping가 불변이면, 이 코드는 컴파일 시점에 에러가 발생한다.
    }
}
````
##### 객체 생성시 고려 사항
1. 단순히 필드를 변경하는 set 메서드를 public 으로 만들지 않는다.
2. 밸류 타입은 가능하다면 불변으로 구현한다.
3. 불변 객체를 변경하려면, `애그리거트 루트`를 통해 새로운 밸류 객체를 할당한다.
````JAVA
public class ShippingInfo {
  private final Address address;
  ...
}

1. public 으로 setter을 만들지 않으면 원초적으로 해당 코드 작성을 막을 수 있다.
2. Shipping가 불변이라면, 이 코드는 컴파일 시점에 에러가 발생한다.
public void business(){
    ...
    ShippingInfo si = order.getShippingInfo();
    si.setAddress(newAddress);
}
````
##### 애그리거트 루트의 기능 구현
- 내부의 다른 객체를 조합해서 기능을 완성
  - Order 애그리거트 루트는, 총 주문 금액을 연산하기 위해 OrderLine 을 사용한다.
````JAVA
public class Order {
  ..
  private List<OrderLine> orderLines;
  ..
  private void calculateTotalAmounts() {
    int sum = this.orderLines.stream().mapToInt(x -> x.getAmounts()).sum();
    this.totalAmounts = new Money(sum);
  }
}
````
- 내부 객체에 기능 실행을 위임한다.
````JAVA
public class Order {
  private List<OrderLine> orderLines;
  ...
  
  private void verifyAtLastOneOrMoreOrderLines(List<OrderLine> orderLines) {
    ...
    for(OrderLine orderLine : orderLines){
      orderLine.verifyValidProduct(orderLine.getProduct());//orderLine에게 유효상품인지 체크로직 위임
    }
  }
}

public class OrderLine {
  private Product product;
  ....
  public void verifyValidProduct(Product product) {
    if(product.name.equals("notOrderAbleProduct")){
      throw new IllegalArgumentException("못사는 상품이에요");
    }
  }
}
````
##### 트랜잭션 범위
````text
1. 트랜잭션 범위는 작을수록 좋다.(테이블 잠금 대상을 최소화 하자)
2. 한 트랙잭션에서는 한 개의 애그리거트만 수정해야 한다.
 > 애그리거트에서 다른 애그리거트를 변경하지 않는것을 의미함
3. 부득이하게 두 개 이상의 애그리거트를 수정해야한다면 서비스 계층에서 두 애그리거트를 수정하도록 구현 한다.
````
````JAVA
＃한 트랙잭션에서는 한 개의 애그리거트만 수정해야 한다.
public class Order {
  private Orderer orderer;

  public void shipTo(ShippingInfo newShippinInfo, boolean changeUserAddress){
    ...
    verifiyNotYetShippined();
    setShipppingInfo(newShippinInfo);
    if(changeUserAddress){
        //사용자의 애그리거트 상태를 변경하면 안된다. bad
      orderer.getMember().changeAddress(newShippinInfo.getAddress());
    }
  }
}

＃부득이하게 두 개 이상의 애그리거트를 수정해야한다면 서비스 계층에서 두 애그리거트를 수정하도록 구현 한다.
public class ChangeOrderService {

  @Transactional
  public void changeShippingInfo(OrderId orderId, ShippingInfo newShippingInfo, boolean changeUserAddress) {
    Order order = orderRepo.findById(orderId);
    order.shipTo(newShippingInfo);
    if (changeUserAddress) {
      Member member = findMember(order.getOrderer());
      member.changeAddress(newShippingInfo.getAddress());
    }
  }
}
````
### 리포지터리와 애그리거트
````text
1. 애그리거트는 개념항 완전한 한 개의 도메인 모델을 표현, 따라서 영속성 처리는 `애그리거트` 단위로 존재한다.
2. 애그리거트는 개념적으로 하나이므로, 리포지터리는 애그리거트 전체를 영속화 해야한다.
````
````JAVA
# 레포지터리에 애그리거트를 저장시, 전체가 영속화 되어야한다.(OrderLine, Orderer, ShippinInfo)
orderRepository.save(order);

# 리포지터리에를 조회시 완전한 order어그리거트를 조회해야한다.(OrderLine, Orderer, ShippinInfo)
Order order = orderRepository.findById(orderId);
````
### ID를 이용한 애그리거트 참조
````JAVA
- 애그리거트는 다른 애그리거트를 참조할 수 있다.
- ORM 기술덕에 쉽게 다른 애그리거트 데이터를 조회할수 있다.
 > 필드를 이용한 애그리거트 참조 문제점
   1) 편한 탐색 오용 : 다른 애그리거트를 수정할수 있다. 
   2) 성능에 대한 고민 : lazy/eager 쿼리 로딩전략 잘 세워야 한다.
   3) 확장성 : 하위 도메인 인프라 계층이 변경될수도 있다.

- 필드 참조(bad)
public class Order{
    private Orderer orderer;
    ...
}
public class Orderer{
    private Member member;
}
public class Member{
  ...
}

- 어그리거트 ID를 통한 간접 참조(good)
 > 결합도를 낮춰주며, 응집도는 높여준다.
 > 참조 애그리거트가 필요하면, 서비스 계층에서 ID를 이용해서 로딩할 수 있다.
 > 다른 어그리거트에서 타 어그리거트를 수정하는 문제를 근본적으로 해결 가능하다.
public class Order{
  private Orderer orderer;
    ...
}
public class Orderer{
  private MembId memberId;
}
public class Member{
  private MemberId id;
}
````
#### ID를 이용한 참조와 조회 성능
- 애그리거트 ID를 통해 참조하면 조회 속도에 문제가 될 수 있다.
  - ex) 주문을 10개 하면 db에 10번 접근을 해야한다.
````JAVA
public class service(){
    public void getOrders(){
      //주문상품이 10개라면, 10번 하위 밸류를 조회하기 위해 db 10번을 타야함.
      Order order = orderRepository.findById(orderId);
      for(OrderLine orderLine : order.getOrderLines()){
        Product product = productRepository.findById(orderLine.product);
      }
    }
}
````
- 조회 전용 쿼리를 사용하여 이를 해결 할 수 있다.
  - 주문상품을 한번에 조회할 수 있도록 처리