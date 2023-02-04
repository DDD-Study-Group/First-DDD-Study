# 도메인 주도 개발 시작하기 : DDD 핵심 개념 정리부터 구현까지

- 정리 범위
  - Chapter 3 애그리거트 : 3.5 ~ 3.6
  - Chapter 6 애그리거트 : 6.5 ~ 6.7

## 3.5 애그리거트 간 집합 연관

### 애그리거트 간 1-N과 M-N 연관

- 두 연관은 컬렉션을 이용한 연관
- 예를 들면, 카테고리와 상품 간의 연관이 대표적
  - 카테고리 입장에서 한 카테고리에 한 개 이상의 상품이 속할 수 있음 -> 카테고리와 상품은 1-N 관계
  - 한 상품이 한 카테고리에만 속할 수 있음 -> 상품과 카테고리 관계는 N-1 관계

### 애그리거트 간 1-N 관계

- Set과 같은 컬렉션을 이용해서 표현
- 예를 들면, Category가 연관된 Product를 값으로 갖는 컬렉션을 필드로 정의 가능

#### 목록 관련 요구사항 처리 방법

- 한 번에 전체 상품을 보여주기보다는 페이징을 이용해 제품을 나눠서 보여준다. 
- 이 기능을 카테고리 입장에서 1-N 연관을 이용해 구현하면 다음과 같은 방식으로 코드를 작성해야 한다.

```java
public class Category {
    private Set<Product> products;

    public List<Product> getProducts(int page, int size) {
        List<Product> sortedProducts = sortById(products);
        return sortedProducts.subList((page - 1) * size, page * size);
    }
}
```

- 이 코드를 실제 DBMS와 연동해서 구현하면 Category에 속한 모든 Product를 조회하게 된다.
- Product 개수가 수만 개 정도로 많다면 이 코드를 실행할 때마다 실행 속도가 급격히 느려져 성능에 심각한 문제를 일으킬 것이다.

### 애그리거트 간 N-1 관계

카테고리에 속한 상품을 구할 필요가 있다면 상품 입장에서 자신이 속한 카테고리를 N-1로 연관 지어 구하면 된다.

> 참고
> 
> 목록이나 상세 화면과 같은 조회 기능을 조회 전용 모델을 이용해서 구현하는 것이 좋다.

## 3.6 애그리거트를 팩토리로 사용하기

```java
public class RegisterProductService {
    
    public ProductId registerNewProduct(NewProductRequest reuqest) {
        Store stor = storeRepository.findById(request.getStoreId());
        checkNull(store);
        if (store.isBlocked()) {
            throw new StoreBlockedException();
        }
        ProductId id = productRepository.nextId();
        Product product = new Product(id, store.getId(), ...생략);
        productRepository.save(product);
        return id;
    }
}   
```

- 이 코드는 Product를 생성 가능한지 판단하는 코드와 Product를 생성하는 코드가 분리되어 있다.
- 코드가 나빠 보이지는 않지만 중요한 도메인 로직 처리가 응용 서비스에 노출되었다.
- Store가 Product를 생성할 수 있는지를 판단하고 Product를 생성하는 것은 논리적으로 하나의 도메인 기능인데 이 도메인 기능을 응용 서비스에서 구현하고 있는 것이다.

```java
public class Store {

    public Product createProduct(ProductId newProductId, ...생략) {
        if (isBlocked()) throw new StoreBlockedException();
        return new Product(newProductId, getId(), ...생략);
    }
}
```

- Store 애그리거트의 createProduct() 는 Product 애그리거트를 생성하는 팩토리 역할을 한다.
- 팩토리 역할을 하면서도 중요한 도메인 로직을 구현하고 있다. 

팩토리 기능을 구현했으므로 이제 응용 서비스는 팩토리 기능을 이용해서 Product를 생성하면 된다.

```java
public class RegisterProductService {
    
    public ProductId registerNewProduct(NewProductRequest reuqest) {
        Store stor = storeRepository.findById(request.getStoreId());
        checkNull(store);
        ProductId id = productRepository.nextId();
        Product product = store.createProduct(id, ...생략);s
        productRepository.save(product);
        return id;
    }
}   
```

- 응용 서비스에서 더 이상 Store의 상태를 확인하지 않음
- 이제 Product 생성 가능 여부를 확인하는 도메인 로직을 변경해도 도메인 영역의 Store만 변경하면 되고 응용 서비스는 영향을 받지 않음

### 애그리거트에 팩토리 메서드 구현

- 애그리거트가 갖고 있는 데이터를 이용해서 다른 애그리거트를 생성해야 할 때
  - Product의 경우 제품을 생성한 Store의 식별자를 필요
  - Store의 데이터를 이용해서 Product를 생성
  - Product를 생성할 수 있는 조건을 판단할 때 Store의 상태를 이용

- 다른 팩토리에 위임해야 할 때
  - Store 애그리거트가 Product 애그리거트를 생성할 때 많은 정보를 알아야 한다면 Store 애그리거트에서 Product 애그리거트를 직접 생성하지 않음

`ProductFactory` 를 활용해서 `Product` 를 생성한다.

```java
public class Store {

    public Product createProduct(ProductId newProductId, ProductInfo pi) {
        if (isBlocked()) throw new StoreBlockedException();
        return ProductFactory.create(newProductId, getId(), pi);
    }
}
```

### 정리

- 중요한 도메인 로직 처리가 응용 서비스에 노출되면 안된다. 
- 팩토리 메소드를 통한 애그리거트 객체를 생성할 수 있다.

## 6. 응용 서비스와 표현 영역

## 6.1 표현 영역과 응용 영역

### 매개체 역할

- 도메인이 제 기능을하기 위해서 필요한 부분
- 응용 영역과 표현 영역이 사용자와 도메인을 연결
- 사용자에게 기능을 제공하려면 도메인과 사용자를 연결해 줄 표현 영역과 응용 영역이 필요

### 표현 영역

- 사용자의 요청을 해석
- 사용자가 웹 브라우저에서 폼에 ID와 암호를 입력한 뒤에 전송 버튼을 클릭하면 요청 파라미터를 포함한 HTTP 요청을 표현 영역에 전달
- 요청을 받은 표현 영역은 URL, 요청 파라미터, 쿠키, 헤더 등을 이용해서 사용자가 실행하고 싶은 기능을 판별하고 그 기능을 제공하는 응용 서비스를 실행

### 응용 영역

> 복잡한 로직은 어플리케이션 서비스에 속하면 안된다. 애플리케이션 서비스의 역할은 복잡도나 도메인 유의성의 로직이 아니라 오케스트레이션만 해당한다. - Unit Testsing - 

- 실제 사용자가 원하는 기능을 제공하는 서비스
  - 사용자가 회원 가입을 요청했다면 실제 그 요청을 위한 기능을 제공하는 주체는 응용 서비스에 위치
- 기능을 실행하는 데 필요한 입력값을 메서드 인자로 받고 실행 결과를 리턴
- 응용 서비스의 메서드가 요구하는 파라미터와 표현 영역이 사용자로부터 전달받은 데이터는 형식이 일치하지 않기 때문에 표현 영역은 응용 서비스가 요구하는 형식으로 사용자 요청을 변환
- 표현 영역의 폼에 입력한 요청 파라미터 값을 사용해서 응용 서비스가 요구하는 객체를 생성한 뒤, 응용 서비스의 메서드를 호출

### 정리 

- 표현 영역
  - 사용자와 상호작용 처리
- 응용 영역
  - 표현 영역에 의존하지 않음
  - 기능 실행에 필요한 입력 값을 받고 실행 결과만 응답 처리

## 6.2 응용 서비스의 역할

- 도메인 객체를 사용해서 사용자의 요청을 처리
- 표현 영역 입장에서 보았을 때 응용 서비스는 도메인 영역과 표현 영역을 연결해 주는 창구 역할
- 도메인 객체 간의 흐름을 제어

```java
public Result doSomeFunc(SomeReq req) {
    // 1. 리포지터리에서 애그리거트를 구한다.
    SomeAgg agg = someAggRepository.findById(req.getId());
    checkNull(agg);

    // 2. 애그리거트의 도메인 기능을 실행한다.
    agg.doFunc(req.getValue());

    // 3. 결과를 리턴한다.
    return createSuccessResult(agg);
}
```

- 새로운 애그리거트를 생성하는 서비스

```java
public Result doSomeFunc(SomeReq req) {
    // 1. 데이터 중복 등 데이터가 유효한지 검사한다.
    validate(req);

    // 2. 애그리거트를 생성한다.
    SomeAgg newAgg = createSome(req);

    // 3. 리포지터리에 애그리거트를 저장한다.
    someAggRepository.save(newAgg);

    // 4. 결과를 리턴한다.
    return createSuccessResult(agg);
}
```

응용 서비스가 복잡하다면?

- 응용 서비스에서 도메인 로직의 일부를 구현하고 있을 가능성 높음
- 응용 서비스가 도메인 로직을 일부 구현하면 코드 중복, 로직 분산 등 코드 품질에 안 좋은 영향을 줄 수 있음

응용 서비스는 트랜잭션 처리도 담당

- 한 번에 다수 회원을 차단 상태로 변경하는 응용 서비스를 생각해보자.
- blockMembers() 메서드가 트랜잭션 범위에서 실행되지 않는다고 가정해볼때
  - Member 객체의 block() 메서드를 실행해서 상태를 변경했는데 DB에 반영하는 도중 문제가 발생하면 일부 Member만 차단 상태가 되어 데이터 일관성이 깨지게 된다.

### 정리

- 트랜잭션 범위에서 응용 서비스를 실행해야 한다.

### 6.2.1 도메인 로직 넣기 않기

- 도메인 로직
  - 도메인 영역에 위치
- 응용 서비스
  - 도메인 로직을 구현하지 않음

```java
public class ChangePasswordService() {
    public void changePassword(String memberId, String oldPw, String newPw) {
        Member member = memberRepository.findById(memberId);
        checkMemberExists(member);
        member.changePassword(oldPw, newPw);
    }
}
```

- Member 애그리거트는 암호를 변경하기 전에 기존 암호를 올바르게 입력했는지 확인하는 로직을 구현한다.

```java
public class Member {
    
    public void changePassword(String oldPw, String newPw) {
        if (!matchPassword(oldPw)) throw new BadPasswordException();
        setPassword(newPw);
    }

    // 현재 암호가 일치하는지 검사하는 도메인 로직
    public boolean matchPassword(String pwd) {
        return passwordEncoder.matches(pwd);
    }

    private void setPassword(String newPw) {
        if (isEmpty(newPw)) throw new IllegalArgumentException("no new password");
        this.password = newPw;
    }
}
```

- 기존 암호를 올바르게 입력했는지를 확인하는 것은 도메인의 핵심 로직
  
다음 코드처럼 응용 서비스에서 이 로직을 구현하면 안 된다.

```java
public class ChangePasswordService() {
    
    public void changePassword(String memberId, String oldPw, String newPw) {
        Member member = memberRepository.findById(memberId);
        checkMemberExists(member);

        if (!passwordEncoder.matches(oldPw, member.getPassword())) {
            throw new BadPasswordException();
        }
        member.setPassword(newPw);
    }
}
```

도메인 로직을 도메인 영역과 응용 서비스에 분산해서 구현하면 코드 품질에 문제가 발생한다.

- 첫 번째 문제는 코드의 응집성이 떨어진다는 것
  - 도메인 데이터와 그 데이터를 조작하는 도메인 로직이 한 영역에 위치하지 않고 서로 다른 영역에 위치할 경우
  - 도메인 로직을 파악하기 위해 여러 영역을 분석해야 한다는 것을 의미
- 두 번째 문제는 여러 응용 서비스에 동일한 로직을 구현할 가능성이 높아진다는 것

```java
public class DeactivationService {

    public void deactivate(String memberId, String pwd) {
        Member member = memberRepository.findById(memberId);
        checkMemberExists(member);

        if (!passwordEncoder.matches(oldPw, member.getPassword())) {
            throw new BadPasswordException();
        }
        member.deactivate();
    }
}
```

- 암호 데이터를 가진 Member 객체 도메인은 암호 확인 기능을 구현
- 응용 서비스에서 도메인이 제공하는 기능을 사용한다면 ?
  - 응용 서비스가 도메인 로직을 구현하면서 발생하는 코드 중복 문제는 발생하지 않는다.

```java
public class DeactivationService {

    public void deactivate(String memberId, String pwd) {
        Member member = memberRepository.findById(memberId);
        checkMemberExists(member);
        if (!member.matchePassword(pwd)) {
            throw new BadPasswordException();
        }
        member.deactivate();
    }
}
```

#### 정리

- 소프트웨어가 가져야 할 중요한 경쟁 요소 중 하나는 변경 용이성
  - 변경이 어렵다는 것은 그만큼 소프트웨어의 가치가 떨어진다는 것을 의미한다.
- 소프트웨어의 가치를 높이려면 도메인 로직을 도메인 영역에 모아서 코드 중복을 줄이고 응집도를 높여야 한다.

## 6.3 응용 서비스의 구현

- 응용 서비스는 표현 영역과 도메인 영역을 연결하는 매개체 역할
- 디자인 패턴에 파사드와 같은역할
- 응용 서비스 자체는 복잡한 로직을 수행하지 않기 때문에 응용 서비스의 구현은 어렵지 않음

### 6.3.1 응용 서비스의 크기

#### 응용 서비스의 크기

- 회원 도메인
  - 응용 서비스는 회원 가입하기, 회원 탈퇴하기, 회원 암호 변경하기, 비밀번호 초기화하기와 같은 기능을 구현하기 위해 도메인 모델 사용

#### 응용 서비스의 구현 방식

구현 방식은 2가지가 존재

- 한 응용 서비스 클래스에 회원 도메인의 모든 기능 구현하기
- 구분되는 기능별로 응용 서비스를 클래스를 따로 구현하기

##### 하나의 응용 서비스 클래스에 도메인의 모든 기능 구현하기

```java
public class MemberService {

    // 각 기능을구현하는 데 필요한 리포지터리, 도메인 서비스 필드 추가
    private MemberRepository memberRepository;

    public void join(MemberJoinRequest joinRequest) { ... }
    public void changePassword(String memberId, String curPw, String newPw) { ... }
    public void initializePassword(String memberId) { ... }
    public void leave(String memberId, String curPw) { ... }
}
```

- 한 도메인과 관련된 기능을 구현한 코드가 한 클래스에 위치하므로 각 기능에서 동일 로직에 대한 코드 중복을 제거할 수 있다는 장점이 존재
  - 예를 들어, changePassword(), initializePassword(), leave()는 회원이 존재하지 않으면 NoMemberException()을 발생시켜야 한다고 해보자.
- 이 경우 다음과 같이 중복된 로직을 구현한 private 메소드를 구현하고 이를 호출하는 방법으로 중복 로직을 쉽게 제거할 수 있다.

```java
public class MemberService {

    private MemberRepository memberRepository;
    private Notifier notifier;

    public void changePassword(String memberId, String curPw, String newPw) {
        Member member = findExistingMember(memberId);
        member.changePassword(currentPw, newPw);
    }

    public void initializePassword(String memberId) {
        Member member = findExistingMember(memberId);
        String newPassword = member.initializePassword();
        notifier.notifyNewPassword(member, newPassword);
    }

    public void leave(String memberId, String curPw) {
        Member member = findExistingMember(memberId);
        member.leave();
    }

    // 각 기능의 동일 로직에 대한 구현 코드 중복을 쉽게 제거
    private Member findExistingMember(String memberId) {
        Member member = memberRepository.findById(memberId);
        if (member == null) {
            throw new NoMemberException(memberId);
        }
        return member;
    }
}
```

- 각 기능에서 동일한 로직을 위한 코드 중복을 제거하기 쉽다는 것이 장점이라면 한 서비스 클래스의 크기(코드 줄 수)가 커진다는 것은 이 방식의 단점
- 한 클래스에 코드가 모이기 시작하면 엄연히 분리하는 것이 좋은 상황임에도 습관적으로 기존에 존재하는 클래스에 억지로 끼워 넣게 됨
- 이것은 코드를 점점 얽히게 만들어 코드 품질을 낮추는 결과를 초래

##### 구분되는 기능별로 응용 서비스 클래스를 따로 구현하기

구분되는 기능별로 서비스 클래스를 구현하는 방식은 한 응용 서비스 클래스에서 한 개 내지 2~3개의 기능을 구현

```java
public class ChangePasswordService() {
    private MemberRepository memberRepository;
    
    public void changePassword(String memberId, String oldPw, String newPw) {
        Member member = memberRepository.findById(memberId);
        if (member == null) throw new NoMemberException(memberId);
        member.changePassword(curPw, newPw);
    }
}
```

- 이 방식을 사용하면 클래스 개수는 많아지지만 한 클래스에 관련 기능을 모두 구현하는 것과 비교해서 코드 품질을 일정 수준으로 유지하는 데 도움
- 각 클래스별로 필요한 의존 객체만 포함하므로 다른 기능을 구현한 코드에 영향을 받지 않음

##### 별도 클래스에 로직 구현(`HelperClass`)

- 각 기능마다 동일한 로직을 구현할 경우 여러 클래스에 중복해서 동일한 코드를 구현할 가능성이 있음 
- 이 경우 다음과 같이 별로 클래스에 로직을 구현해서 코드가 중복되는 것을 방지할 수 있음

```java
// 각 응용 서비스에서 공통되는 로직을 별도 클래스로 구현
public final class MemberServiceHelper {

    public static MemberfindExistingMember(MemberRepository repo, String memberId) {
        Member member = memberRepository.findById(memberId);
        if (member == null) {
            throw new NoMemberException(memberId);
        }
        return member;
    }
}

// 공통 로직을 제공하는 메서드를 응용 서비스에서 사용
import static com.myshop.member.application.MemberServiceHelper.*;

public class ChangePasswordService {
    private MemberRepository memberRepository;

    public void changePassword(String memberId, String curPw, String newPw) {
        Member member = findExistingMember(memberRepository, memberId);
        member.changePassword(curPw, newPw);
    }
}
```

#### 정리

- 한 클래스가 여러 역할을 갖는 것보다 각 클래스마다 구분되는 역할을 갖는 것을 선호한다. 
- 한 도메인과 관련된 기능을 하나의 응용 서비스 클래스에서 모두 구현하는 방식보다 구분되는 기능을 별로의 서비스 클래스로 구현하는 방식을 사용한다.

### 6.3.2 응용 서빗스의 인터페이스와 클래스

다음과 같이 인터페이스를 만들고 이를 상속한 클래스를 만드는것이 필요할까?

```java
public class ChangePasswordService {
    public void changePassword(String memberId, String curPw, String newPw);
}

public class ChangePasswordServiceImpl implements ChangePasswordService {
    ... 구현
}
```

- 인터페이스가 필요한 몇 가지 상황이 있는데 그중 하나는 구현 클래스가 여러 개인 경우다.
- 구현 클래스가 다수 존재하거나 런타임에 구현 객체를 교체해야 할 때 인터페이스를 유용하게 사용할 수 있다.

인터페이스와 클래스를 따로 구현하면 소스 파일만 많아지고 구현 클래스에 대한 간접 참조가 증가해서 전체 구조가 복잡해진다. 

#### 정리

- 인터페이스가 명확하게 필요하기 전까지는 응용 서비스에 대한 인터페이스를 작성하는 것이 좋은 선택이라고 볼 수 없다.

### 6.3.3 메서드 파라미터와 값 리턴

응용 서비스가 제공하는 메서드는 도메인을 이용해서 사용자가 요구한 기능을 실행하는 데 필요한 값을 파라미터로 전달받아야 한다.

- 스프링 MVC와 같은 웹 프레임워크는 웹 요청 파라미터를 자바 객체로 변환하는 기능을 제공하므로 응용 서비스에 데이터로 전달할 요청 파라미터가 두 개 이상 존재하면 데이터 전달을 위한 별도(`RequestDto`) 클래스를 사용하는 것이 편리하다.

```java
@Controller
@RequestMapping("/member/changePassword")
public class MemberPasswordController {

    // 클래스를 이용해서 응용 서비스에 데이터를 전달하면
    // 프레임워크가 제공하는 기능을 활용하기에 좋음
    @PostMapping()
    public String submit(ChangePasswordRequest request) {
        Authentication auth = SecurityContext.getAuthentication();
        changePwdReq.setMemberId(auth.getId());
        try {
            changePasswordService.changePassword(request);
        } catch (NoMemberException ex) {
            // 알맞은 익셉션 처리 및 응답
        }
    }
}
```

응용 서비스의 결과를 표현 영역에서 사용해야 하면 응용 서비스의 메서드의 결과로 필요한 데이터를 리턴한다. 

- 결과 데이터가 필요한 대표적인 예가 식별자다.
- 온라인 쇼핑몰 주문 후 주문 상세 내역을 볼 수 있는 링크를 바로 보여준다.
- 이 링크를 제공하려면 방금 요청한 주문의 번호를 알아야 한다.
- 이 요구를 충족하려면 주문 응용 서비스는 주문 요청 처리 후에 주문번호를 결과로 리턴해야 한다.

```java
public class OrderService {

    @Transactional
    public OrderNo placeOrder(OrderRequest request) {
        OrderNo orderNo = orderRepository.nextId();
        Order order = createOrder(orderNo, orderRequest);
        orderRepository.save(order);
        // 응용 서비스 실행 후 표현 영역에서 필요한 값 리턴
        return orderNo;
    }
}
```

표현 영역 코드는 응용 서비스가 리턴한 값을 사용해서 사용자에게 알맞은 결과를 보여줄 수 있게 된다.

```java
@Controller
public class OrderController {

    @PostMapping("/order/place")
    public String order(OrderRequest request, ModelMap model) {
        setOrder(request);
        OrderNo orderNo = orderService.placeOrder(orderReq);
        modelMap.setAttribute("orderNo", orderNo.toString());
        return "order/success";
    }
}
```

#### 정리

- 응용 서비스는 표현 영역에서 필요한 데이터만 리턴한다는 것이 기능 실행 로직의 응집도를 높이는 확실한 방법이다.

### 6.3.4 표현 영역에 의존하지 않기

응용 서비스의 파라미터 타입을 결정할 때 주의할 점은 표현 영역과 관련된 타입을 사용하면 안 된다는 점이다.

- 예를 들어, 다음과 같이 표현 영역에 해당하는 HttpServletRequest 나 HttpSession을 응용 서비스에 파라미터로 전달하면 안 된다.

```java
@Controller
@RequestMapping("/member/changePassword")
public class MemberPasswordController {

    @PostMapping()
    public String submit(HttpServletRequest request) {
        try {
            //응용 서비스가 표현 영역을 의존하면 안 됨!
            changePasswordService.changePassword(request);
        } catch(NoMemberException ex) {
            // 알맞은 익셉션 처리 및 응답
        }
    }
}
```

- 응용 서비스에서 표현 영역에 대한 의존이 발생하면 응용 서비스만 단독으로 테스트하기가 어려워진다. 
- 게다가 표현 영역의 구현이 변경되면 응용 서비스의 구현도 함께 변경해야 하는 문제도 발생한다.

응용 서비스가 표현 영역의 역할까지 대신하는 상황이 벌어질수도 있다는 것이다.

- 예를 들어, 응용 서비스에 파라미터로 HttpServletRequest를 전달했는데 응용 서비스에서 HttpSession을 생성하고 세션에 인증과 관련된 정보를 담는다고 해보자.

```java
public class AuthenticationService {

    public void authenticate(HttpServletRequest request) {
        String id = request.getParameter("id");
        String password = request.getParameter("password");
        if (checkIdPasswordMatching(id, password)) {
            // 응용 서비스에서 표현 영역의 상태 처리
            HttpSession session = request.getSession();
            session.setAttribute("auth", new Authentication(id));
        }
    }
}
```

- HttpSession이나 쿠키는 표현 영역의 상태에 해당하는데 이 상태를 응용 서비스에서 변경해버리면 표현 영역의 코드만으로 표현 영역의 상태가 어떻게 변경되는지 추적하기 어려워진다.
- 즉, 표현 영역의 응집도가 깨지는 것이다.
- 이것은 결과적으로 코드 유지 보수 비용을 증가시키는 원인이 된다.

#### 정리

- 철저하게 응용 서비스가 표현 영역의 기술을 사용하지 않도록 해야 한다. 
- 이를 지키기 위한 가장 쉬운 방법이 서비스 메서드의 파라미터와 리턴 타입으로 표현 영역의 구현 기술을 사용하지 않는 것이다.

### 6.3.5 트랜잭션 처리

회원 가입에 성공, 실제로 회원 정보를 DB에 삽입하지 않는다면? 

- 고객은 로그인을 할 수 없다. 

배송지 주소를 변경하는 데 실패, 안내 화면을 보여줬는데 실제로는 DB에 변경된 배송지 주소가 반영되어 있다면? 

- 고객은 물건을 제대로 받지 못하게 된다.

트랜잭션을 관리하는 것은 응용 서비스의 중요한 역할이다.

```java
public class ChangePasswordService {

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Member member = findExistingMember(request.getMemberId());
        member.changePassword(request.getCurrentPassword(), request.getNewPassword());
    }
}
```

#### 정리

- 프레임워크가 제공하는 트랜잭션 기능을 적극 사용하는 것이 좋다.
- 프레임워크가 제공하는 규칙을 따르면 간단한 설정만으로 트랜잭션을 시작하여 커밋하고 익셉션이 발생하면 롤백(`Rollback`)할 수 있다.

## 6.4 표현 영역

표현 영역의 책임은 다음과 같다.

- 사용자가 시스템을 사용할 수 있는 흐름(화면)을 제공하고 제어
- 사용자의 요청을 알맞은 응용 서비스에 전달하고 결과를 사용자에게 제공
- 사용자의 세션을 관리

#### 사용자가 시스템을 사용할 수 있는 흐름(화면)을 제공하고 제어

- 웹 서비스의 표현 영역은 사용자가 요청한 내용을 응답으로 제공한다.
- 표현 영역은 응용 서비스를 이용해서 표현 영역의 요청을 처리하고 그 결과를 응답으로 전송한다.

#### 사용자의 요청을 알맞은 응용 서비스에 전달하고 결과를 사용자에게 제공

예를 들어, 암호 변경을 처리하는 표현 영역은 다음과 같이 HTTP 요청 파라미터로부터 필요한 값을 읽어와 응용 서비스의 메서드가 요구하는 객체로 변환해서 요청을 전달한다.

```java
@PostMapping()
public String changePassword(HttpServletRequest request, Errors errors) {

    // 표현 영역은 사용자 요청을 응용 서비스가 요구하는 형식으로 변환한다.
    String curPw = request.getParameter("curPw");
    String newPw = request.getParameter("newPw");
    String memberId = SecurityContext.getAuthentication().getId();
    ChangePasswordRequest changePasswordRequest = new ChangePasswordRequest(memberId, curPw, newPw);

    try {
        // 응용 서비스를 실행
        changePasswordService.changePassword(changePasswordRequest);
        return successView;
    } catch (BadPasswordException | NoMemberException ex) {
        // 응용 서비스의 처리 결과를 알맞은 응답으로 변환
        errors.reject("idPasswordNoMatch");
        return formView;
    }
}
```

```java
// 프레임워크가 제공하는 기능을 사용해서 HTTP 요청을 응용 서비스의 입력으로 쉽게 변경 처리
@PostMapping
public String changePassword(ChangePasswordRequest request, Errors errors) {

    String memberId = SecurityContext.getAuthentication().getId();
    request.setMemberId(memberId);

    try {
        changePasswordService.changePassword(changePasswordRequest);
        return successView;
    } catch (BadPasswordException | NoMemberException ex) {
        // 응용 서비스의 처리 결과를 알맞은 응답으로 변환
        errors.reject("idPasswordNoMatch");
        return formView;
    }
}
```

#### 사용자의 세션을 관리

표현 영역의 다른 주된 역할은 사용자의 연결 상태인 세션을 관리하는 것이다.

### 정리

- 사용자가 시스템을 사용할 수 있는 흐름(화면)을 제공하고 제어
- 사용자의 요청을 알맞은 응용 서비스에 전달하고 결과를 사용자에게 제공
- 사용자의 세션을 관리
