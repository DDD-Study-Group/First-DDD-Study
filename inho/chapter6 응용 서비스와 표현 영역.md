# Chapter 6 응용 서비스와 표현 영역

# 6.1 표현 영역과 응용 영역

- 도메인 영역을 잘 구현하지 않으면 사용자의 요구를 충족하는 완성도 높은 소프트웨어를 만들지 못한다.
- 표현영역과 응용영역은 사용자와 도메인 영역을 연결해준다.

![IMG_9271.jpg](https://s3-us-west-2.amazonaws.com/secure.notion-static.com/99cecc61-be87-4fff-a5f2-aa8211df6489/IMG_9271.jpg)

[출처: 도메인 주도개발 시작하기 / 최범균]

| Layer | 역할 |
| --- | --- |
| Presentation | 사용자의 요청을 해석한다 |
|  | 사용자의 요청을 Application 이 요구하는 형식의 파라미터로 변환한다. |
|  | 사용자가 원하는 기능을 제공하는 Application 을 실행한다. |
|  | Application 이 실행한 결과를 사용자에게 알맞은 형식으로 응답한다. |
| Application | Presentation 으로부터 기능을 실행하는데 필요한 파라미터를 받아 기능을 실행하고 결과를 제공한다. |

**Presentation**

```kotlin
@PostMapping("/member/join")
open fun join(request: HttpServletRequest): ModelAndView {
		val email = request.getParameter("email")
		val password = request.getParameter("password")
		// 사용자의 요청을 응용 서비스에 맞게 변환
		val joinReq = JoinRequest(email, password)
		// 변환한 객체(데이터)를 이용해서 응용 서비스에 실행
		joinService.join(joinReq)
		...
}
```

**Application**

```kotlin
@Service
open class JoinService(private val memberRepository: MemberRepository) {
	fun join(joinReq: JoinRequest) {
		val member = Member(email, password)
		memberRepository.save(member)
	}
}
```

# 6.2 응용 서비스의 역할

### 역할과 특징

- 사용자가 요청한 기능을 실행한다.
    - Repository 로 부터 도메인 객체를 가져와 기능을 실행한다.
- 주로 도메인 객체 간의 흐름을 제어하고, 위임하기 때문에 단순한 형태를 갖는다.

  **Aggregate 를 통한 기능 실행**

    ```kotlin
    fun doSomeFunc(req: SomeReq): Result {
    	// 1. Repository 에서 애그리거트를 구한다.
    	val agg = someAggrepository.findById(req.id)
    	checkNull(agg)
    	// 2. Aggregate 의 도메인 기능을 실행한다.(위임)
    	agg.doFunc(req.value)
    	// 3. 결과를 리턴한다.
    	return createSuccessResult(agg)
    }
    ```

  **Aggregate 생성**

    ```kotlin
    fun doSomeCreation(req: CreateSomeReq): Result {
    	// 1. 데이터 중복 등 데이터가 유효한지 검사한다.
    	validate(req)
    	// 2. Aggregate 를 생성한다.
    	val newAgg = createSome(req)
    	// 3. Repository 에 Aggregate 를 저장한다. (영속성 처리)
    	someAggRepository.save(newAgg)
    	// 4. 결과를 리턴한다.
    	return createSuccessResult(newAgg)
    }
    ```

- **Transaction** 처리

    ```kotlin
    fun blockMembers(blockingIds: Array<String>) {
    	if (blockingIds.length == 0) return
    
    	val members = memberRepository.findByIdIn(blockingIds)
    	members.forEach {
    		it.block()
    	}
    }
    ```

    - 만약, `blockMembers()` 에 대해서 Transaction 범위가 설정되지 않는다면 일부 멤버만 blocking 될 수 있다.
- **Application** 에 도메인 로직이 들어가면 복잡해지고 코드중복 / 로직 분산(낮은 응집도) 등 코드 품질이 저하된다.

## 6.2.1 도메인 로직 넣지 않기

**Application 에 도메인 로직이 없는 경우**

```kotlin
@Service
open class ChangePasswordService(
	private val memberRepository: MemberRepository
) {
	fun changePassword(memberId: String, oldPw: String, newPw: String) {
		val member = memberRepository.findById(memberId)
		checkMemberExists(member)

		member.changePassword(oldPw, newPw)
		memberRepository.save(member)
	}
}
```

```kotlin
class Member() {
	fun changePassword(oldPw: String, newPw: String) {
		if (!matchPassword(oldPw)) throw BadPasswordException()

		setPassword(newPw)
	}
	// 현재 암호와 일치하는지 검사하는 도메인 로직
	private fun matchPassword(pw: String): Boolean = passwordEncoder.matches(pwd)

	private fun setPassword(newPw: String) {
		if (isEmpty(newPw)) throw IllegalArgumentException("no new password")

		this.password = newPw
	}
}
```

**Application 에 도메인 로직이 있는 경우**

```kotlin
@Service
open class ChangePasswordService(
	private val memberRepository: MemberRepository
) {
	fun changePassword(memberId: String, oldPw: String, newPw: String) {
		val member = memberRepository.findById(memberId)
		checkMemberExists(member)

		// Domain
		if (!passwordEncoder.matches(oldPw, member.password))
			throw BadPasswordException()
		member.setPassword(newPw)
		//

		memberRepository.save(member)
	}
}
```

- **코드의 응집도가 떨어진다.**
    - 도메인 로직을 파악하기 위해 여러 영역(Layer)을 분석해야 한다.
    - 캡슐화가 깨진다.
- 도메인 로직이 여러 **Application** 에 중복구현 될 수 있다.
    - 비정상적인 계정의 정지를 막기 위해 암호를 확인하는 요구사항이 추가되었을 때

      **Application 에 Domain 로직이 섞인 경우**

        ```kotlin
        @Service
        open class DeactivationService(
        	private val memberRepository: MemberRepository
        ) {
        	fun deactivate(memberId: String, pwd: String) {
        		val member = memberRepository.findById(memberId)
        		checkMemberExists(member)
        
        		// Domain 중복
        		if (!passwordEncoder.matches(oldPw, member.password))
        			throw BadPasswordException()
        		member.setPassword(newPw)
        		//
        
        		member.deactivate()
        		memberRepository.save(member)
        	}
        }
        ```

      **Application 과 Domain 로직이 분리된 경우**

        ```kotlin
        @Service
        open class DeactivationService(
        	private val memberRepository: MemberRepository
        ) {
        	fun deactivate(memberId: String, pwd: String) {
        		val member = memberRepository.findById(memberId)
        		checkMemberExists(member)
        
        		member.deactivate(pwd)
        		memberRepository.save(member)
        	}
        }
        ```

        ```kotlin
        class Member() {
        	fun changePassword(oldPw: String, newPw: String) {
        		if (!matchPassword(oldPw)) throw BadPasswordException()
        
        		setPassword(newPw)
        	}
        
        	fun deactivate(pwd: String) {
        		if (!matchPassword(pwd)) throw BadPasswordException()
        
        		// 비활성화 처리 로직
        		....
        		//
        	}
        
        	// 현재 암호와 일치하는지 검사하는 도메인 로직
        	private fun matchPassword(pw: String): Boolean 
        		= passwordEncoder.matches(pwd)
        
        	private fun setPassword(newPw: String) {
        		if (isEmpty(newPw)) throw IllegalArgumentException("no new password")
        
        		this.password = newPw
        	}
        }
        ```

- 소프트웨어의 가치를 높이려면 도메인 로직을 도메인 영역에 모아서 코드 중복을 줄이고 응집도를 높여야 한다.

# 6.3 응용 서비스의 구현

- **Application** 은 **Presentation** Layer 와 **Domain** Layer 를 연결시켜주는 매개체의 역할
    - 복잡한 로직을 수행하지 않는다. → Thin Layer

## 6.3.1 응용 서비스의 크기

- 구현방식
    - 한 응용 서비스 클래스에 도메인의 모든 기능 구현하기(😭 )

      **회원과 관련된 모든 기능(가입 / 탈퇴 / 암호변경 / 암호 초기화)을 하나의 응용 서비스에 구현**

        ```kotlin
        @Service
        open class MemberService(
        	private val notifier: Notifier,
        	private val memberRepository: MemberRepository
        	// 각 기능을 구현하는데 필요한 여러개의 Repository 추가
        ) {
        	fun join(joinRequest: JoinRequest) =
        		try {
        			findExistingMember(joinRequest.memberId)
        		} catch(exception: NoMemberException) {
        			memberRepository.save(
        				Member(joinRequest.memberId, joinRequest.password)
        			)
        		}
        	}
        	
        	fun leave(memberId: String, curPw: String) {
        		val member = findExistingMember(memberId)
        		member.leave()
        		memberRepository.save(member)
        	}
        
        	fun changePassword(memberId: String, curPw: String, newPw: String) {
        		val member = findExistingMember(memberId)
        		member.changePassword(curPw, newPw)
        		memberRepository.save(member)
        	} 	
        	
        	fun initializePassword(memberId: String) {
        		val member = findExistingMember(memberId)
        		member.initializePassword()
        		memberRepository.save(member)
        		notifier.notifyPassword(member, newPassword)
        	}
        	// 중복구현할 가능성이 낮아진다.
        	// 그러나 이 메소드를 별도의 객체로 분리하면 기능별로 클래스를 만들어도 중복없다...
        	private fun findExistingMember(memberId: String): Member {
        		val member = memberRepository.findById(memberId)
        		return member ?: throw NoMemberException(memberId) 
        	}
        }
        ```

      | 장점 | 각 기능에서 동일한 로직을 위한 중복코드 제거(인정하진 못하겠음) |
              | --- | --- |
      | 단점 | 클래스가 복잡해지고 커진다.(가독성 저하 → 생산성 저하) |
      |  | 의존성이 집중되고 결합도가 커진다. |
      |  | 일부 도메인 기능에 대한 변경에도 전체 기능에 대한 테스트가 필요하다. |
      |  | 단위 테스트가 매우 어려워진다. |
      |  | 시간이 지날수록 코드 품질이 매우 나빠진다. |
    - 도메인 기능별로 응용 서비스 클래스 만들기 (😄 )

        ```kotlin
        @Service
        open class JoinMemberService(
        	private val memberRepository: MemberRepository
        ) {
        	fun join(joinRequest: JoinRequest) =
        		try {
        			memberRepository.findById(joinRequest.memberId)
        		} catch(exception: NoMemberException) {
        			memberRepository.save(
        				Member(joinRequest.memberId, joinRequest.password)
        			)
        		}
        	}
        }
        ```

        ```kotlin
        @Service
        open class LeaveMemberService(
        	private val memberRepository: MemberRepository
        ) {
        	fun leave(memberId: String, curPw: String) {
        		val member = memberRepository.findById(memberId)
        		member.leave()
        		memberRepository.save(member)
        	}
        }
        ```

        ```kotlin
        @Service
        open class ChangePasswordService(
        	private val memberRepository: MemberRepository
        ) {
        	fun changePassword(memberId: String, curPw: String, newPw: String) {
        		val member = memberRepository.findById(memberId)
        		member.changePassword(curPw, newPw)
        		memberRepository.save(member)
        	} 	
        }
        ```

        ```kotlin
        @Service
        open class InitializePasswordService(
        	private val notifier: Notifier,
        	private val memberRepository: MemberRepository
        ) {
        	fun initializePassword(memberId: String) {
        		val member = memberRepository.findById(memberId)
        		member.initializePassword()
        		memberRepository.save(member)
        		notifier.notifyPassword(member, newPassword)
        	}
        }
        ```

        ```kotlin
        @Repository
        open class MemberRepository(private val memberDAO: MemberDAO) {
        	fun findById(memberId: String): Member {
        		val member = memberDAO.findById(memberId)
        		return member ?: throw NoMemberException(memberId) 
        	}
        }
        ```

      | 장점 | Application 클래스의 크기가 작아진다. |
              | --- | --- |
      |  | 의존성이 분산되고 응집도가 높아진다. |
      |  | 일부 도메인에 대한 기능 변경시 전체 영향을 주지 않는다.  |
      |  | 단위 테스트가 쉬워진다. |
      |  | 코드 품질을 일정 수준으로 유지하기 쉽다. |
      | 단점 | 클래스가 많아진다. (인정 못하겠음, 클래스가 많다 적다의 기준이 모호하고, 주로 생산성을 떨어뜨리는 원인은 클래스의 갯수가 아니라 복잡한 의존성과 높은 결합도임) |

## 6.3.2 응용 서비스의 인터페이스와 클래스

```kotlin
interface ChangePasswordService {
	fun changePassword(memberId: String, curPw: String, newPw: String)
}

class ChangePasswordServiceImpl : ChangePasswordService {
	override fun changePassword(memberId: String, curPw: String, newPw: String) {
			...
	}
}
```

- 구현 클래스가 여러개인 경우를 제외하고 인터페이스가 반드시 필요한 경우는 거의 없으며, 인터페이스가 반드시 필요한지 고민해보고 사용해야 한다.
    - Top → Down 개발방식으로 개발하자 (**Presentation** → **Application** → **Domain** → **Infra** )

## 6.3.3 메서드 파라미터와 값 리턴

| Presentation → Application | Application 에서 요구한 조건에 맞는 파라미터를 전달하여 실행한다. |
| --- | --- |
|  | Presentation Layer 의 객체들을 전달하면 안된다. (HttpServletRequest…) |
| Application → Presentation | Presentation 에서 필요한 값(객체) 만 제공한다. |
|  | ENTITY 를 리턴하면 안된다. |

**Presentation**

```kotlin
@Controller
@RequestMapping("/member/changePassword")
open class MemberPasswordController(
	private val changePasswordService: ChangePasswordService
) {
	// 클래스를 이용해서 응용 서비스에 데이터를 전달하면
	// 프레임워크가 제공하는 기능을 활용하기에 좋음
	@PostMapping()
	open fun submit(
		@RequestBody @Valid changePwdReq: ChangePasswordRequest
	): String {
		val auth = SecurityContext.getAuthentication()
		changePwdReq.memberId = auth.id
		
		return try {
			changePasswordService.changePassword(changePwdReq)
		} catch (ex: NoMemberException) {
			// 예외처리 및 응답
		}
	}
}
```

**Application**

```kotlin
@Service
open class ChangePasswordService(private val memberRepository: MemberRepository) {
	@Transactional
	open fun changePassword(changePwdReq: ChangePasswordRequest): String = 
		with(changePwdReq) {
			val member = MemberRepository.findById(this.memberId)
			member.changePassword(this.curPwd, this.newPwd)
			member.id
		}
	}
}
```

## 6.3.4 표현 영역에 의존하지 않기

- **Application** 에서는 **Presentation** 영역과 관련된 타입을 **사용하면 안된다.**

    ```kotlin
    @Controller
    @RequestMapping("/member/changePassword")
    open class MemberPasswordController(
    	private val changePasswordService: ChangePasswordService
    ) {
    	// 클래스를 이용해서 응용 서비스에 데이터를 전달하면
    	// 프레임워크가 제공하는 기능을 활용하기에 좋음
    	@PostMapping()
    	open fun submit(request: HttpServletRequest): String {
    		return try {
    			// 응용 서비스가 표현 영역을 의존하면 안됨!
    			changePasswordService.changePassword(request)
    		} catch (ex: NoMemberException) {
    			// 예외처리 및 응답
    		}
    	}
    }
    ```

    - **Application**(`ChangePasswordService.changePassword()`) 테스트가 복잡하고 어렵다.
        - `HttpServletRequest` 를 mocking 해야 한다.
    - **Application** → **Presentation** 을 의존하게 되어 **Presentation** 에서 변경이 일어나면 **Application** 이 영향을 받는다.
    - **Application** 에서 **Presentation** 객체를 변경하게 되어 **Presentation** 의 응집도가 낮아진다.

## 6.3.5 트랜잭션 처리

- 트랜잭션을 관리하는 것은 **Application** 의 중요한 역할이다.
    - 회원가입은 하였지만 회원정보가 DB 에 저장되지 않으면 로그인 불가하다.
    - 배송지 주소를 변경이 실패하였지만 DB 에는 업데이트 되었다면 물건을 제대로 받을 수 없다.

    ```kotlin
    @Service
    open class ChangePasswordService(
    	private val memberRepository: MemberRepository
    ) {
    	@Transactional
    	open fun changePassword(req: ChangePasswordRequest) {
    		val member = memberRepository.findById(req.memberId)
    		member.changePassword(req.curPwd, req.newPwd)
    		memberRepository.save(member)
    	}
    } 
    ```

- 프레임워크가 제공하는 트랜잭션 기능을 적극 활용하는 것이 좋다.
    - 간단한 설정만으로 트랜잭션을 적용할 수 있다.
    - 코드가 간결해진다.

# 6.4 표현 영역

- **Presentation** 영역의 책임
    - **사용자**가 시스템을 사용할 수 있는 흐름(화면) 을 제공하고 제어한다.
    - **사용자**의 요청을 알맞은 **Application** 서비스에 전달하고 결과를 사용자에게 제공한다.
    - **사용자**의 세션을 관리한다.

![IMG_9288.jpg](https://s3-us-west-2.amazonaws.com/secure.notion-static.com/bb073680-6455-4efd-bf19-f3b181e60df5/IMG_9288.jpg)

[출처: 도메인 주도개발 시작하기 / 최범균]

- 사용자는 **Presentation** 이 요구한 형식에 맞게 값을 입력하여 요청하고,
- **Presentation** 은 사용자의 요청에 맞는 **Application** 에게 위임하여 사용자의 요청을 처리하고,
- **Presentation** 은 **Application** 으로부터 처리결과를 받아 사용자가 요구한 형식에 맞게 응답한다.

```kotlin
@PostMapping()
open fun changePassword(
	// 표현영역은 사용자 요청을 응용 서비스가 요구하는 형식으로 변환한다.
	@RequestBody @Valid request: ChangePasswordRequest, 
	errors: Errors
): String {
	try {
		changePasswordService.changePassword(request)
		return successView
	} catch (ex: BadPasswordException) {
		// 응용 서비스의 처리결과를 알맞은 응답으로 변환
		errors.reject("idPwsswordNotMatch")
		return formView
	} catch (ex: NoMemberException) {
		// 응용 서비스의 처리결과를 알맞은 응답으로 변환
		errors.reject("idPwsswordNotMatch")
		return formView
	}
}
```

# 6.5 값 검증

- 값에 대한 모든 검증(형식 / 논리)을 **Application** 에서 하는 방법

    ```kotlin
    @Controller
    open class MemberController(private val joinService: JoinService) {
    	@PostMapping()
    	open fun join(
    		@ModelAttribute("joinRequest") joinRequest: JoinRequest, 
    		bindingResult: BindingResult,
    		modelMap: ModelMap
    	): String {
    		try {
    			val memberId = joinService.join(joinRequest)
    			modelMap.addAttribute("id", memberId)
    			return "member/info"
    		} catch (e: ValidationErrorException) {
    			// 응용 서비스가 발생시킨 검증 에러 목록을 
    			// 뷰에서 사용할 형태로 변환
    			e.errors.forEach {
    				if (it.hasName()) {
    					bindingResult.rejectValue(it.name, it.code)
    				} else {
    					bindingResult.reject(it.code)
    				}
    				populateProductsModel(joinRequest, modelMap)
    				return "member/error"
    			}
    		}
    	}
    }
    ```

    ```kotlin
    @Service
    open class JoinService(private val memberRepository: MemberRepository) {
    	@Transactional
    	fun join(joinReq: JoinRequest): String {
    		// 값의 형식 검사
    		checkEmpty(joinReq.id, "id")
    		checkEmpty(joinReq.name, "name")
    		checkEmpty(joinReq.password, "password")
    		if (!joinReq.password.equals(joinReq.confirmPassword)) {
    			throw InvalidPropertyException("confirmPassword")
    		}
    		// 로직 검사
    		checkDuplicateId(joinReq.id)
    		// 가입 처리
    		val member = Member(joinReq.id, joinReq.password, joinReq.name)
    		memberRepository.join(member)
    		return member.id
    	}
    
    	private fun checkEmpty(value: String?, propertyName: String) {
    		if (value.isNullOrEmpty())
    			throw EmptyPropertyException(propertyName)
    	}
    
    	private fun checkDuplicateId(id: String) {
    		val count = memberRepository.countsById(id)
    		if (count > 0) throw DuplicateIdException()
    	}
    }
    ```

    - 장점
        - 검증(형식 / 논리)하는 코드의 응집도가 높아진다.
        - 검증하는 코드의 중복을 미연에 방지할 수 있다.
        - **Presentation** 이 단순해진다.
    - 단점
        - **Application** 의 크기가 커지고 복잡해질 수 있다.
        - 검증하는 코드만 테스트 할 수 없다. → `MemberRepository` 를 mocking 해야 한다.
- 값의 형식에 대한 검증은 **Presentation** 에서 하고, 논리에 대한 검증은 **Application** 에서 하는 방법

    ```kotlin
    @Controller
    open class MemberController(private val joinService: JoinService) {
    	@PostMapping()
    	open fun join(
    		@ModelAttribute("joinRequest") joinRequest: JoinRequest, 
    		bindingResult: BindingResult,
    		modelMap: ModelMap
    	): String {
    		// 값의 형식 검사
    		checkEmpty(joinReq.id, "id")
    		checkEmpty(joinReq.name, "name")
    		checkEmpty(joinReq.password, "password")
    		if (!joinReq.password.equals(joinReq.confirmPassword)) {
    			bindingResult.rejectValue("confirmPassword", "INVALID_PASSWORD")
    			populateProductsModel(joinRequest, modelMap)
    			return "member/error"
    		}
    
    		try {
    			val memberId = joinService.join(joinRequest)
    			modelMap.addAttribute("id", memberId)
    			return "member/info"
    		} catch (e: DuplicateIdException) {
    			return "member/duplicateId"
    		}
    	}
    
    	private fun checkEmpty(value: String?, propertyName: String) {
    		if (value.isNullOrEmpty())
    			throw EmptyPropertyException(propertyName)
    	}
    }
    ```

    ```kotlin
    @Service
    open class JoinService(private val memberRepository: MemberRepository) {
    	@Transactional
    	fun join(joinReq: JoinRequest): String {
    		// 로직 검사
    		checkDuplicateId(joinReq.id)
    		// 가입 처리
    		val member = Member(joinReq.id, joinReq.password, joinReq.name)
    		memberRepository.join(member)
    		return member.id
    	}
    
    	private fun checkDuplicateId(id: String) {
    		val count = memberRepository.countsById(id)
    		if (count > 0) throw DuplicateIdException()
    	}
    }
    ```

    - 장점
        - 형식 검증 / 논리 검증하는 코드가 분리되어 복잡도(**Presentation** / **Application**) 가 관리된다.
    - 단점
        - 검증하는 코드의 응집도가 낮아진다.
        - `JoinService.join()` 가 호출되는 시점에서 `JoinRequest` 이 올바른 형식인지 100% 보장할 수 없다.
        - 형식 검증에 대한 코드가 중복코드가 발생할 수 있다.
        - 형식 검증하는 코드만 테스트 할 수 없다. → `JoinService` 를 mocking 해야 한다.
- 값의 형식에 대한 검증을 Value Object 에 위임하고 Application 에서 모든 검증을 진행하는 방법

    ```kotlin
    data class JoinRequest {
    	val id: String, 
    	val password: String, 
    	private val confirmPassword: String,
    	val name: String
    
    	constructor(
    		id: String?, 
    		password: String?, 
    		confirmPassword: String?, 
    		name: String?
    	) {
    		if (id.isNullOrEmpty()) 
    			throw EmptyPropertyException("id")
    		if (password.isNullOrEmpty()) 
    			throw EmptyPropertyException("password")
    		if (password.isNullOrEmpty()) 
    			throw EmptyPropertyException("confirmPassword")
    		if (name.isNullOrEmpty()) 
    			throw EmptyPropertyException("name")
    		
    		this.id = id
    		this.password = password
    		this.confirmPassword = confirmPassword
    		this.name = name
    	}
    
    	fun confirmPassword(): Boolean = password == confirmPassword
    }
    ```

    ```kotlin
    @Service
    open class JoinService(private val memberRepository: MemberRepository) {
    	@Transactional
    	fun join(joinReq: JoinRequest): String {
    		// 값의 형식 검사
    		if (!joinReq.confirmPassword()) {
    			throw InvalidPropertyException("confirmPassword")
    		}
    		// 로직 검사
    		checkDuplicateId(joinReq.id)
    		// 가입 처리
    		val member = Member(joinReq.id, joinReq.password, joinReq.name)
    		memberRepository.join(member)
    		return member.id
    	}
    
    	private fun checkEmpty(value: String?, propertyName: String) {
    		if (value.isNullOrEmpty())
    			throw EmptyPropertyException(propertyName)
    	}
    
    	private fun checkDuplicateId(id: String) {
    		val count = memberRepository.countsById(id)
    		if (count > 0) throw DuplicateIdException()
    	}
    }
    ```

    - 장점
        - 형식 검증 / 논리 검증하는 코드가 분리되어 복잡도(**Presentation** / **Application**) 가 관리된다.
        - 검증하는 코드의 중복을 미연에 방지할 수 있다.
        - mocking 없이 형식 검증 코드를 테스트 할 수 있다.
    - 단점
        - 검증하는 코드의 응집도가 낮아진다.
        - `JoinService.join()` 가 호출되는 시점에서 `JoinRequest` 이 올바른 형식인지 100% 보장할 수 없다.
        - 검증하는 코드만 테스트 할 수 없다. → `JoinService` 를 mocking 해야 한다.

# 6.6 권한 검사

- 개발하는 시스템 요구사항에 따라 복잡도가 다르기 때문에 프레임워크에서 제공하는 기능을 확장할지, 직접 구현할지 고민이 필요하다.
    - 프레임워크에서 제공하는 기능에 대한 이해도가 부족하다면 직접 구현하는 것이 유리할 수 있다.

| Presentation | 인증된 사용자인지 아닌지 검사한다. |
| --- | --- |
|  | 인증된 사용자의 요청만 Controller 에 전달한다. |
|  | URL 별 권한 검사 및 접근제어 |
| Application | 서비스 메소드 단위로 권한 검사 |
|  | BlockMemberService.block() 은 관리자(ADMIN) 만 호출가능 |
| Domain | 도메인 객체 단위로 권한 검사 |
|  | 게시글 작성자 또는 관리자만 게시글을 삭제할 수 있다. |

![IMG_9289.jpg](https://s3-us-west-2.amazonaws.com/secure.notion-static.com/b0c4dd0d-5b76-4480-9766-615415bb0346/IMG_9289.jpg)

[출처: 도메인 주도개발 시작하기 / 최범균]

```kotlin
@Service
open class BlockMemberService(private val memberRepository: MemberRepository) {
	@PreAuthorize("hasRole('ADMIN')") 
	fun block(memberId: String) {
		val member = memberRepository.findById(memberId)
		member?.let { it.block() } ?: throw NoMemberException()
	}
}
```

```kotlin
@Service
open class DeleteArticleService(
	private val userRepository: UserRepository,
	private val articleRepository: ArticleRepository
) {
	fun delete(userId: String, articleId: Long) {
		val article = articleRepository.findById(articleId)
		checkArticleExistence(article)
		
		val user = userRepository.findById(userId)
		val deletedArticle = article.delete(userId, user.permission)
		articleRepository.delete(deletedArticle)
		...
	}
}
```

# 6.7 조회 전용 기능과 응용 서비스

- 단순한 조회 기능을 구현한다면, **Application** 의 역할이 없을 수 있다.

```kotlin
@Service
open class OrderListService(private val orderDAO: OrderDAO) {
	fun getOrderList(orderId: String): List<Order> {
		return orderDAO.selectByOrderer(orderId)
	}
}
```

- **Application** 의 역할이 없다면 굳이 서비스 클래스를 만들 필요는 없다.
