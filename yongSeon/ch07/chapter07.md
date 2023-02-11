## 도메인 서비스
### 느낀점
- 도메인 서비스는 도메인 모델의 복잡성을 해결하기 위한 방법이다.
- 도메인 서비스는 도메인 모델의 오퍼레이션을 표현하기 위한 서비스지 응용서비스처럼 인프라스트럭처에서 도메인 모델을 가져와서 사용하는 것이 아니다.
- 특정 인프라스트럭처에 종속된 오퍼레이션일 경우 인터페이스를 통해 추상화 시킨다.

### 도메인 서비스 정의
- 도메인 서비스는 아래와 같은 상황에 종종 쓰이게 된다
    - 계산 로직: 하나의 애그리거트에 넣기에는 복잡한 로직.
    - 외부 시스템과 연동이 필요한 로직: 특정 외부시스템을 통해 도메인 기능을 구현해야되는 경우

**계산 로직 예**
- 할인 계산 도메인 서비스를 사용하는 것은 애그리거트가 될 수도 있고 응용 서비스가 될 수도 있다.
- 하지만 애그리거트안에 도메인 서비스를 넣는 것은 지양하는 것이 좋다.
- 애그리거트는 도메인의 상태를 관리하는 것이 주된 역할이기 때문에 특정한 상황에 따라서 상태를 변경하는 로직을 넣는 것은 지양하는 것이 좋다.
```java
public class DiscountCalculationService {
    
    public Money calculateDiscountPrice(MemberGrade grade, List<OrderLine> orderLines, List<Coupon> couponList) {
        Money discountPrice = 
                couponList.stream()
                        .map(coupon -> coupon.calculateDiscountPrice(orderLines))
                        .reduce(Money.ZERO, Money::plus);
        
        Money membershipDiscountPrice = calculateDiscountPrice(orderLines, grade);
        
        return couponList.plus(membershipDiscountPrice);
    }
    
    private Money calculateDiscountPrice(List<OrderLine> orderLines) {
      ...
    }
}
```

**외부 시스템과 연동이 필요한 로직 예**
- 외부 시스템이나 타 도메인과 연동이 필요할 때에도 도메인 서비스를 사용할 수 있다.
- 이 경우에도 도메인 서비스의 목적인 오퍼레이션을 통해 구현하는 것이 중요하다. 인프라스트럭처와 연관된 순간 도메인 서비스는 특정 인프라스트럭처에 종속되게 되고
  도메인 서비스를 사용하는 응용 서비스도 특정 인프라스트럭처에 종속되게 된다.
- 추상화된 인터페이스를 통해 의존관계를 역전시켜서 도메인 서비스를 사용하는 응용 서비스가 특정 인프라스트럭처에 종속되지 않게 만들어야 한다.
```java
public interface SurveyPermissionChecker {
    boolean hasPermission(Member member);
}

public class CreateSurveyService {
    private final SurveyPermissionChecker surveyPermissionChecker;
    
    public CreateSurveyService(SurveyPermissionChecker surveyPermissionChecker) {
        this.surveyPermissionChecker = surveyPermissionChecker;
    }
    
    public void createSurvey(Member member, Survey survey) {
        if (!surveyPermissionChecker.hasPermission(member)) {
            throw new SurveyPermissionException();
        }
        
        surveyRepository.save(survey);
    }
}
```

### 도메인 서비스의 패키지 위치
- 도메인 서비스는 도메인 모델의 로직을 표현하기 때문에 동일한 패키지에 넣는 것이 좋다.
- 도메인 서비스가 너무 많이 존재한다면 도메인 하위 패키지를 만들어서 분리하는 것도 좋은 방법이다.
    - domain.model, domain.service, domain.repository, domain.dao

![img_2.png](images%2Fimg_2.png)

### 도멩니 서비스와 인터페이스
- 도메인 서비스가 고정되지 않고 특정 인프라스트럭처나 별도의 시스템에 종속된다면 추상화를 통해 의존성을 끊는 것이 좋다.
- 도메인 영역이 특정 구현에 종속되는 것을 방지하고 도메인 영역에 대한 테스트가 쉬워진다.
