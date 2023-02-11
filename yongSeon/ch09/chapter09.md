## 9.1 도메인 모델과 경계
- DDD에서의 도메인 모델에서 가장 빠지기 쉬운 함정은 완벽한 도메인 모델을 만들 수 있다고 생각하는 것
- 한개의 모델로 하위 도메인을 모두 표현할 수 없다. 오히려 하나의 모델로 표현하려고 하면 모델이 너무 복잡해지고, 이해하기 어려워진다.
- 아래처럼 같은 유저지만 하위 도메인에 따라 다른 형태를 띄기 때문에 하나의 모델로 표현하는 것은 옳지 않다.

|  도메인  |         설명          |
|:-----:|:-------------------:|
| 유저-회원 |    회원정보를 입력하고 관리    |  
| 유저-주문 | 주문을 입력하고 주문한 상품을 관리 |
| 유저-배송 |      배송상태를 관리       |

### 문제점
- 하나의 모델로 표현하려고 하면 모델이 너무 복잡해지고, 이해하기 어려워진다.
- 여러 도메인과 얽히면서 도메인의 요구사항을 표현하기 어렵다.
- 도메인별로 발전하는 도메인 규칙을 적용하기 어렵다.

### 정리
- 도메인 모델은 컨텍스트(문맥)에 따라 다른 의미를 갖는다.
- 이렇게 구분되는 경계의 있는 컨텍스트를 바운디드 컨텍스트라고 부른다.


## 9.2 바운디드 컨텍스트
- 논리적으로 하나의 바운디드 컨텍스트에게는 하나의 모델이 존재한다.
- 유저를 표현하는 모델 중 유저-회원, 유저-주문, 유저-배송은 각각 하나의 모델이 존재한다.
- 이상적으로 하위 도메인과 바운디드 컨텍스트는 1:1 관계가 이상적이지만 그렇지 않을 때도 많다.
- 조직구조에 맞춰서 개발이 되거나 용어가 정해지지 않았을 때에는 두개의 하위 도메인으로 개발이 될 수 있다.

### 조직구조에 따른 다른 구조
|  주문 하위 도메인   |   주문 하위 도메인    |
|:------------:|:--------------:|
| 주문 바운디드 컨텍스트 | 결제금액 바운디드 컨텍스트 |

### 용어에 따른 다른 구조
| 카탈로그 하위 도메인  |  재고 하위 도메인   |
|:------------:|:------------:|
| 상품 바운디드 컨텍스트 | 상품 바운디드 컨텍스트 |

### 소규모 서비스에서의 바운디드 컨텍스트 구조
- 소규모 서비스에서는 여라개의 하위 도메인이 하나의 바운디드 컨텍스트로 구성되는 경우가 많다.
- 중요한 것은 하위 도메인 모델이 섞이지 않도록 해야한다. (하위 도메인 모델이 섞이면 하나의 모델로 표현하는 것과 다를 바가 없다.)
- 하위 도메인마다 패키지를 나누어 구분되도록 해야한다.

| 온라인 쇼핑 바운디드 컨테스트 |                |              |
|:----------------:|:--------------:|:------------:|
|   주문 바운디드 컨텍스트   | 카탈로그 바운디드 컨텍스트 | 회원 바운디드 컨텍스트 |

### 정리
- 바운디드 컨텍스트는 도메인 모델을 구분하는 경계가 되기 때문에 바운디드 컨텍스트를 구현하는 하위 도메인에 알맞은 모델을 포함한다.
- 같은 사용자라 하더라도 주문과 회원 바운디드 컨텍스트가 갖는 모델이 달라진다.
- 같은 상품이라도 카탈로그 Product와 재고 Product는 각 컨텍스트에 맞는 모델을 갖는다.
- 회원의 Member는 애그리거트 루트이지만 주문의 Orderer는 밸류가 되고 카탈로그의 Product는 상품이 속할 Catergory와 연관을 갖지만 재고의 Product는 카탈로그의 Category와 연관을 맺지 않는다.

```markdown
- Project
  - Member Management
    - Member
    - Address
  - Catalog Management
    - Product
    - Category
  - Order Management
    - Orderer
  - Inventory Management
    - Product
```

## 바운디드 컨텍스트 구현
- 바운디드 컨텍스트가 도메인 모들만 포한 하는 것이 아니라 사용자에게 제공하는데 필요한 표현영역, 응용서비스, 인프라스트럭처 영역을 모두 포함한다.

- ![img.png](image%2Fimg.png)

- 모든 바운디드 컨텍스트를 도메인 주도로 개발할 필요는 없다. 상품의 리뷰 같은 경우 도메인 모델이 복잡하지 않고, 리뷰를 조회하는 기능만 필요하다면 도메인을 개발하지 않고 않고 단순하게 구현해도 된다.
- 도메인의 기능들이 서비스에 흩어지게 되지만 코드를 유지보수하는데 있어서 문제가 되지 않으면 괜찮다. 

| 주문 바운디드 컨텍스트 | 리뷰 바운디드 컨텍스트 |
|:------------:|:------------:|
|    표현 영역     |    표현 영역     |
| 응용 서비스 |     서비스      |
| 도메인 모델 |              |
|인프라스트럭처 | DAO |

### CQRS
- 두가지 방식을 혼합하여 사용할 수 있는데 그것이 CQRS이다.
- CQRS는 Command Query Responsibility Segregation의 약자로 명령과 쿼리를 분리하는 것이다.
- 도메인으로 상태를 변경하고 명령을 통해 조회하는 구조이다.

### 바운디드 컨텍스트의 유연한 구현
- 각 바운디드 컨텍스트를 구현할 때 서로 다른 기술이 사용될 수 있다.
- JPA/Hibernate를 사용하여 구현하고, MyBatis를 사용하여 구현하고, Spring Data JPA를 사용하여 구현할 수 있다.
- RDBMS를 사용하지 않고 NoSql을 사용하여 구현할 수도 있다. UI를 구현할 때도 있고 단순히 Rest API를 구현할 때도 있다.

## 바운디드 컨텍스트 간의 통합
- 기존에 카탈로그를 담당하던 팀과 새로운 추천 시스템을 담당하는 팀이 생겨서 두 팀의 기능을 통합해야 한다고 가정해보자.
- 사용자가 상품상세화면을 볼 때 추천 시스템에서는 상품을 추천해준다.

![img_1.png](image%2Fimg_1.png)

- 카탈로그 컨텍스트는 상품을 통한 도메인 모델을 제공하고 추천 컨텍스트는 추천을 연산하기 위한 모델을 제공한다.
- 카탈로그 시스템은 추천 시스템을 호출하여 추천 상품을 가져온다. 이때 추천 도메인 모델을 사용하기 보다는 카탈로그 도메인 모델을 사용한다.
```java

public interface ProductRecommendationService {
    List<Product> getRecommendationOf(Long productId);
}
```

![img_2.png](image%2Fimg_2.png)
- RecClient 는 Rest API를 통해 추천 시스템을 호출하는 클라이언트이다. 그렇기 때문에 추천 시스템의 도메인 모델과 일치하지 않는 모델을 전달 받을 수 있다.

```java
public class RecClient implements ProductRecommendationService {
    private final ProductRepository productRepository;
    
    public List<Product> getRecommendationOf(Long productId) {
        List<RecommendationItem> items = getRecommendationItems(productId);
        
        return toProducts(items);
    }
    
    private List<RecommendationItem> getRecommendationItems(Long productId) {
        // Rest API를 통해 추천 시스템을 호출
        
        return extanalClient.getRecommendationItems(productId);
    }
    
    private List<Prodcut> toProducts(List<RecommendationItem> items) {
        List<Long> productIds = items.stream()
            .map(RecommendationItem::getProductId)
            .collect(Collectors.toList());
        
        return productRepository.findAllById(productIds);
    }
}

```
- 위와 같이 구현하는 것이 복잡하다면 중간에 추천 시스템의 도메인 모델을 카탈로그 도메인 모델로 변환해주는 변환기를 두는 것도 방법이다.

### 간접적으로 통합하기 

![img_3.png](image%2Fimg_3.png)
- 사용자의 구매이력이나 조회 이력을 필요로 할 때가 있는데 이러한 상황에는 메시지 큐를 활용할 수 있다.
- 카탈로그 시스템에서 상품을 조회하면 메시지 큐에 전달된 조회 이력을 저장한다. 추천 시스템에서는 메시지 큐에 전달된 이력을 읽어서 추천을 연산한다.
- 도메인 모델 관점에 따라 두 바운디드 컨텍스트를 구현하는 코드가 달라진다.


```java
// ViewLogService에서는 상품 조회 관련 로그 기록 코드를 남긴다.
class ViewLogService(MessageClient messageClient ) {

    public void appendViewLog(String memberId, Long productId, LocalDateTime time) {
        messageClient.send(ViewLog(memberId, productId, time));
    }
}

class RabbitMQClient(RabbitTemplate rabbitTemplate) implements MessageClient {

    @override 
    public void send(ViewLog viewLog) {
        rabbitTemplate.convertAndSend(logQueueName, viewLog);
    }
}
```

### 정리
- 두 바운디드 컨테스트를 개발을 한다면 데이터의 구조를 협의하게 되는데 그 큐를 누가 제공하느냐에 따라 데이터 구조가 결정된다.
- 한쪽에서 메세지를 출판하고 다른 쪽에서 메세지를 구독하는 출판/구독 모델을 따른다.