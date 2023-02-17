## 6.5 값 검증
- 갑 검증은 응용 계층, 표현 계층 두곳 모두 검증할 수 있다.

### 표현계층에서의 값검증
```java
public class JoinService {
    public Member join(Member member) {
        checkEmptyEmail(member.getEmail());
        checkEmptyPassword(member.getPassword());
        checkEmptyName(member.getName());
        memberRepository.save(member);
        return member;
    }
}
```
```java
public class MemberController {
    
    @PostMapping("/members/new")
    public ResponseEntity<Member> create(@RequestBody Member member) {
        try {
            Member newMember = memberService.join(member);
            return ResponseEntity.ok(newMember);
        }catch (EmptyPropertyException e) {
            return ResponseEntity.badRequest().build();
        }catch (DuplicateMemberException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
```
- 응용 서비스에서 값 검증을 하게된다면 문제는 사용자가 값을 잘못해서 입력한 것과 중복되서 입력해서 잘못한 것인지 한번에 알 수 없다는 것이다.
- 이러한 예외처리 방법은 사용자가 여러번의 입력을 하게만들고 안좋은 경험을 만들어 낸다.

### 해결 방법
- 응용 서비스에서 에러코드르 한번에 모아서 익셉션을 발생시키는 방법
```java
public class JoinService {
    public Member join(Member member) {
        List<ValidationError> validationErrorList = new ArrayList<>();
        if (member.getEmail() == null || member.getEmail().isEmpty()) {
            validationErrorList.add(new ValidationError("email", "이메일은 필수 값입니다."));
        }else if(member.getPassword() == null || member.getPassword().isEmpty()) {
            validationErrorList.add(new ValidationError("password", "비밀번호는 필수 값입니다."));
        }
        
        if (!validationErrorList.isEmpty()) {
            throw new ValidationException(validationErrorList);
        }
        
        return memberRepository.save(member);
    }
}
```
- 표현 계층에서 필수 값을 검증하는 방법
```java
public class MemberController {
    
    @PostMapping("/members/new")
    public ResponseEntity<Member> create(@RequestBody Member member) {
        try {
            emptyEmail(member.getEmail());
            emptyPassword(member.getPassword());
            Member newMember = memberService.join(member);
            return ResponseEntity.ok(newMember);
        }catch (ValidationException e) {
            return ResponseEntity.badRequest().body(e.getErrors());
        }
    }
}
```
- 스프링에서 제공하는 @Valid 어노테이션을 사용하는 방법
```java
public class JoinMemberRequestDto {
    @Email
    @NotEmpty(message = "이메일은 필수 값입니다.")
    private String email;
    
    @NotEmpty(message = "비밀번호는 필수 값입니다.")
    private String password;
    
    @NotEmpty(message = "이름은 필수 값입니다.")
    private String name;
}

public class MemberController {
    
    @PostMapping("/members/new")
    public ResponseEntity<Member> create(@RequestBody @Valid JoinMemberRequestDto joinMemberRequestDto) {
        Member member = new Member(joinMemberRequestDto.getEmail(), joinMemberRequestDto.getPassword(), joinMemberRequestDto.getName());
        Member newMember = memberService.join(member);
        return ResponseEntity.ok(newMember);
    }
}
```

### 느낀점
- 필자는 응용서비스의 완성도를 높이기 위해 응용서비스에서의 값검증을 선호한다고 한다는 것을 보고 새로운 견해를 느낄 수 있었다.
- 표현계층에서는 값이나 형식, 범위를 검증하고 응용계층은 주로 논리적인 오류를 검증을 하는데에 이점이 나는 더 크다고 생각한다.
- 그리고 응용서비스에서의 값검증을 하게되면 사용자가 여러번의 입력을 하게만들고 안좋은 경험을 만들어 낸다는 것이 매우 공감이 되었다.
- 응용서비스 계층은 비지니스로직이 들어가는 곳이기 때문에 단순하고 그 의미가 잘 담겨야된다고 생각한다.

## 6.6 권한 검사
- 스플링 환경에서는 스프링 시큐리티라는 모듈을 사용하여 유연하게 권한 검사할 수 있다.
- 권한 검사는 프레임워크 복잡도에 떠나 아래와 같은 영역에서 권한 검사흘 할 수 있다.
  - 표현영역
  - 응용 서비스
  - 도메인 영역

### 표현 계층에서 권한 검사
![img_1.png](images%2Fimg_1.png)
- 아래처럼 처리할 경우 서블릿 필터를 통해 권한 검사를 할 수 있다.
  - URL을 통해 컨트롤러에 접근하기 전에 인증여부를 검사해서 인증된 요청만 처리한다.
  - 인증된 사용자가 아닐 경우 401 에러를 반환한다.
- 이 방법은 스프링 시큐리티도 유사한 방식으로 처리한다. 

### 응용 계층에서 권한 검사
- URL만으로도 접근 제어를 할 수 없을 경우 응용서비스를 통해 권한 검사를 할 수 있다.
- 접근 제어코드를 꼭 서비스 계층에 넣지 않아도 된다. AOP를 통해 서비스 접근에 대한 권한 검사를 할 수 있다.

```java
public class BookMemberService {
    
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public void block(Long memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("회원이 존재하지 않습니다."));
        member.block();
    }
}
```

### 도메인 계층에서 권한 검사
```java
public class DeleteArticleService {
    public void delete(String userId, Long articleId) {
        Article article = articleRepository.findById(articleId);
        permissionService.checkPermission(userId, article);
        article.markDeleted();
    }
}
```
- 도메인 계층에서의 권한 검사는 구현이 복잡하다, 왜나하면 인프라스트럭처에서 가져온 결과를 통해 처리해야 되기 때문이다. 
- 스프링 시큐리티와 같은 보안 프레임워크를 확장해서 개발 도메인 객체 수준의 권한 검사를 할 수도 있지만 프레임워크에 대한 높은 이해도가 필요하다.

## 6.7 조회 전용 기능과 응용 서비스
- 서비스에서 단순한 조회같은 경우 아래와 같이 호출하는 형태로 구현할 수 있다.
```java
public class OrderListService {
    public List<Order> getOrders(String userId) {
        return orderRepository.findByUserId(userId);
    }
}
```
- 서비스에서 수행하는 추가적인 로직이 없을 뿐더러 단일 쿼리만 실행하는 조회 전용 기능이여서 트랜잭션이 필요하지 않는다.
- 이런 경우에는 서비스를 사용하지 않고 조회 기능을 직접 호출하는 것이 좋다.
```java
public class OrderController {
    private OrderRepository orderRepository;
    
    @GetMapping("/orders")
    public List<Order> getOrders(String userId) {
        return orderRepository.findByUserId(userId);
    }
}
```
- DDD 관점으로 설계하면서 서비스를 사용하고 있다가 바로 가져가서 쓰는 것이 어색할 수 있지만 서비스 계층이 사용자 요청을 처리하는데 별다른 기여를 하지 못한다면 만들지 않아도 된다.

### 느낀점
- 여러 계층에서의 값 검증이나 권한 검사를 하는 것은 도메인의 목적이나 개발자의 재량에 따라 달라질 수 있다는 것을 알 수 있었다.
- 소프트웨어의 설계는 항상 트레이드오프가 존재하고 그에 따라서 설계의 방향이 달라질 수 있다는 것을 알 수 있었다.

7.1 여러 에그리거트가 필요한 기능
- 여러 애그리거트가 필요한 기능을 구현할 때에 책임을 어디로 전달해야 될까?
- 다수의 애그리거트를 조합해서 기능을 구현할 때에는 애그리거트의 경계를 넘어서는 책임을 전달해야 하지만 하나의 애그리거트에 전달하게 된다면 
확장하면 할수록 애그리거트가 커지게 된다.
- 아래의 예를 살펴보자
### 결제기능 구현
- 상품 애그리거트: 구매하는 상품에 대한 정보를 가지고 있다. 또한 상품에 종류에 따라 배송비를 추가로 한다.
- 주문 애그리거트: 상품별로 구매 개수를 가지고 있다.
- 할인 쿠폰 애그리거트: 쿠폰 별로 할인 금액이나 비율을 가지고 있고 족언에 따라 사용이 가능하다.
- 회원 애그리거트: 회원 등급에 따라 할인 금액이나 비율을 가지고 있다.
위와 같은 상황에서 결제를 처리해야되는 애그리거트가 있어야 한다면 그나마 주문일텐데 그렇게 된다면 다른 애그리거트의
정보들을 알아야 한다. 그렇게 되면 결제 애그리거트는 다른 애그리거트에 의존하게 된다. 
```java
public class Order {
    private List<OrderLine> orderLines;
    
    private Money calculateTotalPrice() {
        Money totalPrice = calculateTotalPrice();
        for (OrderLine orderLine : orderLines) {
            totalPrice = totalPrice.plus(orderLine.calculatePrice());
        }
        
        Money discountPrice = calculateDiscountPrice();
        discountPrice = member.calculateDiscountPrice(totalPrice);
        
        return totalPrice;
    }
}
```
- 여기서 봤을 때 결제 애그리거트는 상품, 주문, 할인 쿠폰, 회원 도메인에 의존하게 된다.
- 그리고 여기서 추가적인 브랜드 할인 같은 정책이 들어간다면 더 추가적인 책임을 가지게 되고 도메인에 대한 개념이 점점 희미해진다. 


