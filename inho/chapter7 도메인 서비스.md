# Chapter 7 도메인 서비스

# 7.1 여러 애그리거트가 필요한 기능

- 실제 구현을 하다 보면 한 애그리거트로 기능을 구현할 수 없을 때가 있다.

  **총** **결제금액 계산 시 필요한 애그리거트**

  |  애그리거트 |  |
      | --- | --- |
  | 상품 | 구매하는 상품 의 가격이 필요하다. |
  |   | 상품에 따라 배송비가 추가되기도 한다. |
  | 주문 | 상품별로 구매 개수가 필요하다. |
  | 할인쿠폰 | 할인쿠폰별로 지정한 할인금액이나 비율에 따라 주문 총 금액을 할인한다. |
  |  | 할인쿠폰을 조건에 따라 중복사용 하거나 지정한 카테고리의 상품에만 적용할 수 있다. (제약조건, 도메인 규칙) |
  |  | 할인쿠폰 : 카테고리 = 1 : 1  |
  |  | 할인쿠폰 : 조건(?) = M : 1 |
  | 회원 | 회원 등급에 따라 추가 할인이 가능하다. |
    - `주문` 애그리거트에서 **총 결제금액 계산**

        ```kotlin
        class Order(
        	private val orderer: Orderer,
        	private val orderLines: List<OrderLine>,
        	private val usedCoupons: List<Coupon>
        ) {
        	fun calculatePayAmounts(): Money {
        		val totalAmounts = calculateTotalAmounts()
        		// 쿠폰별로 할인 금액을 구한다.
        		val discount = coupons.map { calculateDiscount(coupon) }
        													.reduce { total, discount -> 
        														 total + discount
        													} 
        		// 회원에 따른 추가 할인을 구한다.
        		val membershipDiscount = calculateDiscount(orderer.member.grade)
        		// 실제 결제 금액 계산
        		return totalAmounts - discount - membershipDiscount
        	}
        
        	private fun calculateDiscount(coupon: Coupon): Money {
        		// orderLines 의 각 상품에 대해 쿠폰을 적용해서 할인 금액 계산하는 로직
        		// 쿠폰의 적용 조건등을 확인하는 코드
        		// 정책에 따라 복잡한 if-else 와 계산코드
        		....
        	}
        	
        	private fun calculateDiscount(grade: MemberGrade): Money {
        		// 등급에 따라 할인금액 계산
        	}
        }
        ```

        - 감사 세일로 전품목 한 달간 2% 세일을 진행한다면, 이 정책은 `주문`과 상관이 없음에도 주문 애그리거트가 수정된다.
        - **한 애그리거트에 넣기 애매한 기능을 억지로 넣으면 안된다.**
            - 코드가 복잡해진다.
            - 의존성이 증가한다.

# 7.2 도메인 서비스

- **[도메인 서비스](https://aspnetboilerplate.com/Pages/Documents/Domain-Services)**는 도메인 영역에 위치한 도메인 로직을 표현할 때 사용한다.
- 일반적인 **CRUD 작업(도메인 객체 조회 / 수정 / 삭제)** 가 아닌 도메인 로직만을 수행한다. → **Repository** 와 의존성 없음
- **Application** / **Domain** Layer 객체들과 협력하고, 다른 Layer(**Presentation** / **Infra**) 와 협력하지 않는다.


    | 계산로직 | 여러 애그리거트가 필요한 계산 |
    | --- | --- |
    |  | 한 애그리거트에 넣기에 복잡한 로직 |
    | 외부 시스템 연동이 필요한 도메인 로직 | 구현하기 위해 외부 시스템을 사용해야 하는 도메인 로직 |

## 7.2.1 계산 로직과 도메인 서비스

- 도메인 서비스를 이용해서 도메인 개념을 명시적으로 드러낸다.
- 도메인 서비스는 **상태 없이 도메인 로직만 구현한다.**
    - 응용 로직은 절대 수행하지 않는다. → 응용 로직은 **응용 서비스**가 수행
    - **애그리거트**의 상태를 변경하거나, **애그리거트**의 값을 계산하는 것은 도메인 로직이다.
- 도메인 서비스 이름은 도메인 의미가 드러나는 타입과 메소드 이름을 갖는다.
- 사용방법 종류
    - **애그리거트** 메소드를 실행할 때 파라미터로 **도메인 서비스**를 넘겨서 실행한다.
    - **도메인 서비스** 메소드를 실행할 때 파라미터로 **애그리거트**를 넘겨서 실행한다.

### **애그리거트** 메소드를 실행할 때 파라미터로 **도메인 서비스**를 넘겨서 실행

**Application Layer**

```kotlin
@Service
open class OrderService(
	private val disCalSvc: DiscountCalculateService,
	private val userRepository: UserRepository,
	private val orderRepository: OrderRepository,
) {
	@Transactional
	fun placeOrder(orderRequest: OrderRequest): OrderNo {
		val orderNo = orderRepository.nextId()
		val order = createOrder(orderNo, orderRequest)
		orderRepository.save(order)
		// 응용 서비스 실행 후 표현 영역에서 필요한 값 리턴
		return orderNo
	}

	private fun createOrder(orderNo: OrderNo, orderReq: OrderRequest): Order {
		val member = userRepository.findById(orderReq.userId)
		val order = Order(orderNo, 
											orderReq.orderLines, 
											orderReq.coupons,
											createOrderer(member),
											orderReq.shipping)
		order.calculateAmounts(disCalSvc, member.grade)
		return order
	}
}
```

**Domain Layer**

```kotlin
// Domain Service
class DiscountCalculateService {
	fun calculateDiscountAmounts(
		orderLines: List<OrderLine>,
		coupons: Coupons,
		grade: MemberGrade
	): Money {
		val couponDiscount = coupons.calculateDiscount() 
		val membershipDiscount = calculateDiscount(orderer.member.grade)
		return couponDiscount + membershipDiscount
	}
}
```

```kotlin
class Order(
	private val orderLines: List<OrderLine>,
	private val coupons: Coupons,
	private var paymentAmounts: Money = Money.ZERO
) {
	fun calculateAmount(disCalSvc: DiscountCalculationService, 
											grade: MemberGrade) {
		val totalAmounts = getTotalAmounts()
		val discountAmounts = 
				disCalSvc.calculateDiscountAmounts(orderLines, coupons, grade)
		paymentAmounts = totalAmounts - discountAmounts
	}
}

// 쿠폰(Coupon) 은 사용자 별로 쿠폰이 발급되고, 각각 서로 다른 상태(사용완료 / 미사용)를 가지므로 ENTITY 이다.
class Coupons(private val coupons: List<Coupon>) {
	fun calculateDiscount(): Money = 
			coupons.map { it.calculateDiscount() }
						 .reduce { total, discount -> total + discount }

	
}

class Coupon {
	fun calculateDiscount(): Money {
		// orderLines 의 각 상품에 대해 쿠폰을 적용해서 할인 금액 계산하는 로직
		// 쿠폰의 적용 조건등을 확인하는 코드
		// 정책에 따라 복잡한 if-else 와 계산코드
		....
	}
}
```

- 할인 계산 서비스(`DiscountCalculateService`) 를 사용하는 주체는 **애그리거트가** 될 수도 있고 **응용 서비스**가 될 수도 있다.
    - **Repository** 에 대한 의존성이 없다.
- **애그리거트**에 도메인 서비스를 전달하는 것은 **응용 서비스** 책임이다.
- **할인 계산 서비스**(`DiscountCalculateService`) 를 도메인 객체(`Order`) 에서 직접 의존하도록 하면 안좋은 이유는 무엇일까?
    - 도메인 서비스를 만들어 적용하려는 이유는 다른 애그리거트와 복잡한 협력이 필요한 경우이다.
    - **의존성 관리가 안된다.**
        - 위의 예제에서는 주문 애그리거트에 있는 모든 정보로 할인계산 서비스를 생성하고 수행하는데 문제가 없지만, 만약 주문 애그리거트에 없는 정보를 가지고 할인 계산 서비스를 수행해야 한다면, 주문 애그리거트에 할인 계산을 위한 불필요한 의존성이 추가될 수 있다.

### **도메인 서비스** 메소드를 실행할 때 파라미터로 **애그리거트**를 넘겨서 실행

**Application Layer**

```kotlin
@Service
open class AccountTransferService(
	private val transferService: TransferService,
	private val accountRepository: AccountRepository
) {
	@Transactional
	open fun transfer(request: TransferRequest) {
		val sender = accountRepository.findById(request.sender.accountId)
		val receiver = accountRepository.findById(request.receiver.accountId)
		
		transferService.transfer(sender, receiver, request.amounts)

		accountRepository.save(sender)
		accountRepository.save(receiver)
	}
}
```

**Domain Layer**

```kotlin
// Domain Service
class TransferService {
	fun transfer(sender: Account, receiver: Account, amounts: Money) {
		sender.withdraw(amounts)
		receiver.credit(amounts)
		...
	}
}
// ENTITY
class Account(private val id: AccountNumber, private val amounts: Money) [
	fun withdraw(amounts: Money) {
		....
	}
}
// VALUE
data class Money(private val value: Double)
```

## 7.2.2 외부 시스템 연동과 도메인 서비스

- 외부 시스템이나 타 도메인과의 연동 기능도 도메인 서비스가 될 수 있다.
- 항상 도메인 관점에서 인터페이스를 정의해야 한다.

**설문조사 시 사용자 권한 확인하는 기능**

설계 1) 저자 버전

**Application** Layer

```kotlin
@Service
open class CreateSurveyService(
	private val permissionChecker: SurveyPermissionChecker
) {
	fun createSurvey(req: CreateSurveyRequest): Long {
		validate(req)
		// 도메인 서비스를 이용해서 외부 시스템 연동을 표현
		if (!permissionChecker.hasUserCreationPermission(req.requestorId)) {
			throw NoPermissionException(req.requestorId)
		}
	}
}
```

**Domain** Layer

```kotlin
interface SurveyPermissionChecker {
	fun hasUserCreationPermission(userId: String): Boolean
}
```

**Infrastructure** Layer

```kotlin
class MemberPermissionDAO(
	private val restTemplate: RestTemplate
) : SurveyPermissionChecker {

	override fun hasUserCreationPermission(userId: String): Boolean {
		try {
			val response = restTemplate.postForObject("https://.../...",, 
																					      request, 
																					      Boolean::class.java)
			checkStatusCode(response.statusCode)
			return response.body
		} catch (unse: UnknownStatusException) {
			throw unse
		} catch (e: Exception) {
			throw UnknownErrorException()
		}
	}

	private fun checkStatusCode(status: HttpStatus) {
		when(status) {
			HttpStatus.OK -> Unit
			HttpStatus.NOT_FOUND -> NoMemberException()
			else -> UnknownStatusException(status)
		}
	}
}
```

설계 2) 스터디 멤버 버전

**Domain** Layer

```kotlin
class SurveyPermissionChecker(
	private val memberPermissionDAO: MemberPermissionDAO
) {
	fun hasUserCreationPermission(userId: String): Boolean {
		try {
			return memberPermissionDAO.hasUserCreationPermission(userId)
		} catch (unse: UnknownStatusException) {
			// 도메인에 맞는 예외 핸들링 처리
			return false
		}
	}
}
```

**Infrastructure** Layer

```kotlin
interface MemberPermissionDAO {
	fun hasUserCreationPermission(userId: String): Boolean
}

class StudyMemberPermissionDAO(
	private val restTemplate: RestTemplate
) : MemberPermissionDAO {

	override fun hasUserCreationPermission(userId: String): Boolean {
		try {
			val response = restTemplate.postForObject("https://.../...",, 
																					      request, 
																					      Boolean::class.java)
			checkStatusCode(response.statusCode)
			return response.body
		} catch (unse: UnknownStatusException) {
			throw unse
		} catch (e: Exception) {
			throw UnknownErrorException()
		}
	}

	private fun checkStatusCode(status: HttpStatus) {
		when(status) {
			HttpStatus.OK -> Unit
			HttpStatus.NOT_FOUND -> NoMemberException()
			else -> UnknownStatusException(status)
		}
	}
}
```

## 7.2.3 도메인 서비스의 패키지 위치

- 도메인 서비스는 도메인 로직을 표현하므로 도메인 클래스와 같은 패키지에 위치한다.

  ![IMG_9311.jpg](https://s3-us-west-2.amazonaws.com/secure.notion-static.com/2c235af4-eb9b-4d74-bb1c-46e67ca8d873/IMG_9311.jpg)

  [출처: 도메인 주도개발 시작하기 / 최범균]

- 도메인 서비스의 갯수가 많거나 ENTITY 나 VALUE 를 명시적으로 구분하고 싶다면 도메인 패키지 하위로 구분한다.
    - domain
        - model
        - service
        - repository

## 7.2.4 도메인 서비스의 인터페이스와 클래스

- 도메인 서비스의 로직이 고정되어 있지 않은 경우에는 인터페이스를 구현하여 세부 구현과 분리한다.

  ![IMG_9312.jpg](https://s3-us-west-2.amazonaws.com/secure.notion-static.com/9ab4ddc5-2e51-43d3-93f5-e57d4db201be/IMG_9312.jpg)

  [출처: 도메인 주도개발 시작하기 / 최범균]

    - 외부 시스템과의 연동
        - 실제 구현은 **Infrastructure** Layer 에 위치한다.
    - 실제 구현(세부기술) 에 의존하지 않게 된다.
    - 단위 테스트가 쉬워지고 코드가 간결해진다.
