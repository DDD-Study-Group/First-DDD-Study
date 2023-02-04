# Chapter 8 애그리거트 트랜잭션 관리

# 8.1 애그리거트와 트랜잭션

![IMG_9319.jpg](https://s3-us-west-2.amazonaws.com/secure.notion-static.com/d5acae5c-9882-4711-9a0c-add8a366c104/IMG_9319.jpg)

[출처: 도메인 주도개발 시작하기 / 최범균]

| 방식 | 예시 |
| --- | --- |
| Pessismistic Lock | 운영자가 배송지를 조회하고 상태를 변경하는 동안, 고객이 애그리거트를 수정하지 못하게 막는다. |
| Optimistic Lock | 운영자가 배송지를 조회한 이후에 고객이 정보를 변경하면, 운영자가 애그리거트를 다시 조회한 뒤 수정하도록 한다.  |

# 8.2 Pessismistic Lock

- 먼저 애그리거트를 조회한 쓰레드가 애그리거트를 사용하는 동안, 다른 쓰레드가 애그리거트를 수정하지 못하게 막는 방식이다.

![IMG_9320.jpg](https://s3-us-west-2.amazonaws.com/secure.notion-static.com/82189464-01a0-4471-9e84-55fb2fba38fe/IMG_9320.jpg)

![IMG_9321.jpg](https://s3-us-west-2.amazonaws.com/secure.notion-static.com/90c92779-184b-4ee0-a19d-f0cd51eb9866/IMG_9321.jpg)

[출처: 도메인 주도개발 시작하기 / 최범균]

- 운영자 스레드가 배송중 로 변경을 완료하면 고객 스레드가 `주문` 애그리거트를 점유한다.
    - 주문 애그리거트의 상태가 `배송중` 이면 배송지를 변경할 수 없는 도메인 규칙에 의거해서, 고객 스레드는 배송지를 변경할 수 없고 트랜잭션이 실패하게 된다.

      **JPA EntityManager**

        ```kotlin
        val order = entityManager.find(Order::class, 
        															 orderNo, 
        															 LockModeType.PESSIMISTIC_WRITE)
        ```

      **Spring data JPA**

        ```kotlin
        interface MemberRepository : Repository<Member, MemberId> {
        	@Lock(LockModeType.PESSMISTIC_WRITE)
        	@Query("select m from Member m where m.id = :id")
        	fun findByIdForUpdate(
        		@Param("id") memberId: MemberId
        	): Optional<Member>
        }
        ```


## 8.2.1 선점 잠금과 교착상태

![IMG_9322.jpg](https://s3-us-west-2.amazonaws.com/secure.notion-static.com/e6bfbd3d-97cd-4788-a60f-dbd32c9b8334/IMG_9322.jpg)

- 스레드 1,2 는 상대방 스레드가 선점한 잠금을 구할 수 없어 영원히 다음 단계를 진행하지 못한다.(교착상태, Dead Lock)
- 점유시간에 제한을 두어 해결할 수 있다.
    - DBMS 마다 다르기 때문에 반드시 사전에 확인이 필요하다.

    ```kotlin
    interface MemberRepository : Repository<Member, MemberId> {
    	@Lock(LockModeType.PESSMISTIC_WRITE)
    	@QueryHints({ 
    		@QueryHint(name = "javax.persistence.lock.timeout", value = "2000")
    	})
    	@Query("select m from Member m where m.id = :id")
    	fun findByIdForUpdate(
    		@Param("id") memberId: MemberId
    	): Optional<Member>
    }
    ```


### 예방하기 위해서는 아래의 조건들 중 한개 이상을 제거해야 한다.

- **상호배제**(**Mutual Exclusion**)
    - 한번에 한개의 프로세스만 자원을 점유하는 것을 말한다.
    - 교착상태를 예방하기 위해서는 한번에 여러개의 프로세스가 자원을 점유할 수 있도록 한다.
- **점유와 대기**(**Hold and Wait**)
    - 한 개 이상의 자원을 점유한 상태로 다른 프로세스에서 점유한 자원을 점유하기 위해 대기하는 것을 말한다.
    - 교착상태를 예방하기 위해서는 현재 점유중인 자원을 점유해제 후, 다른 자원을 점유하기 위해 대기하거나 상호배제 조건을 해제하고, 하나의 프로세스가 자원을 점유하기 전 해당 자원이 필요한 모든 프로세스가 함께 자원을 점유하도록 한다.
- **비선점**(**None-preemption**)
    - 다른 프로세스가 점유한 자원은 점유가 해제될 때까지 다른 프로세스에서 점유할 수 없는 것을 말한다.
    - 교착상태를 예방하기 위해서는 다른 프로세스가 점유한 자원을 점유하고자 할 때에는 점유한 자원을 해제하고 점유 대기하거나, 하나의 자원을 동시에 여러개의 프로세스가 점유하도록 한다.
- **환형 대기**(**Circular Wait**) ****
    - 자원을 점유하기 위해 대기하고 있는 프로세스들이 원형을 이루고 있는 것을 말한다.
    - 자원을 선형으로 점유대기 하도록 해야 한다.

# 8.3 Optimistic Lock

- 선점 잠금으로 모든 트랜잭션 충돌 문제를 해결할 수 없다.

  ![IMG_9323.jpg](https://s3-us-west-2.amazonaws.com/secure.notion-static.com/1ed76203-dce8-427c-bec3-7cc854a8829f/IMG_9323.jpg)

  [출처: 도메인 주도개발 시작하기 / 최범균]

    1. 운영자는 배송을 위해 `주문`을 조회한다. (**Lock**)
    2. 시스템은 `주문` 조회 결과를 제공한다. (**Unlock**)
    3. 고객이 `주문` 정보를 조회한다. (**Lock**)
    4. 시스템은 `주문` 조회 결과를 제공한다. (**Unlock**)
    5. 운영자는 `주문`상태(`배송중`)를 변경한다. (**Lock**)
    6. 시스템은 `주문`상태를 변경하고 결과를 제공한다. (**Unlock**)
    7. 고객은 배송지를 변경한다. (**Lock**)
    8. 시스템은 배송지를 변경하고 결과를 제공한다. (**Unlock**)

- **비선점 잠금**은 동시에 접근(조회)하는 것은 허용하고, 수정할 때 가능여부를 확인하고 수정하는 방식이다.

  **AGGREGATE 버전 활용**

  ![IMG_9324.jpg](https://s3-us-west-2.amazonaws.com/secure.notion-static.com/ec08d361-5e9d-4315-8e55-d68d1a227ca9/IMG_9324.jpg)

  **SQL**

    ```sql
    UPDATE aggtable SET version = version + 1, colx = ?, coly = ?
    WHERE  aggid = ? AND version = 현재버전
    ```


      **JPA**

```kotlin
@Entity
@Table(name = "purchase_order")
@Access(AccessType.FIELD)
class Order {
	@EmbeddedId
	private val number: OrderNo
	@Version
	private val version: Long
}
```

- 수정가능여부를 확인하고 수정할 때, 선점잠금이 필요하다.
    - 동시에 두 쓰레드가 수정가능하다고 확인을 하면, 이후에 어떤 쓰레드가 먼저 실행이 되느냐에 따라서 트랜잭션이 깨질 수 있다.

**Presentation** Layer

- 예외를 통해 트랜잭션 충돌여부를 확인할 수 있다.

    ```kotlin
    @Controller
    class OrderController(
    	private val changeShippingService: ChangeShippingService
    ) {
    	@PostMapping("/changeShipping")
    	fun changeShipping(@RequestBody changeReq: ChangeShippingRequest): String {
    		try {
    			changeShippingService.changeShipping(changeReq)
    			return "changeShippingSuccess"
    		} catch(ex: OptimisticLockingFailureException) {
    			// 누군가 먼저 같은 주문 애그리거트를 수정했으므로 트랜잭션이 충돌했다는 메시지를 보여준다.
    			return "changeShippingTxConflict"
    		}
    	}
    }
    ```


**Application** Layer

- 트랜잭션 세부 관리 방법(version) 에 대해 알 필요가 없다.
