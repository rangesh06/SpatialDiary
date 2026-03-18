# AGENTS.md — Android Studio / Kotlin Agentic Coding Guide

> This file governs all AI-assisted code generation in this project.
> Every suggestion, refactor, or new file produced by Gemini Code Assist
> must conform to the rules below. When in doubt, prefer explicitness,
> simplicity, and testability over cleverness.

---

## 1. Project Architecture — Strict MVVM

Follow **MVVM (Model-View-ViewModel)** with a clean separation of concerns
across three layers. Never collapse layers or let them bleed into each other.

```
app/
├── data/                        # Data layer
│   ├── local/                   # Room DAOs, entities, database
│   ├── remote/                  # Retrofit API interfaces, DTOs
│   ├── repository/              # Repository implementations
│   └── mapper/                  # DTO ↔ Domain model mappers
├── domain/                      # Domain layer (pure Kotlin, zero Android deps)
│   ├── model/                   # Domain models
│   ├── repository/              # Repository interfaces (contracts)
│   └── usecase/                 # One class per use case
├── presentation/                # UI layer
│   ├── ui/
│   │   ├── screen/              # Composables or Fragments (one per screen)
│   │   └── component/           # Reusable UI components
│   ├── viewmodel/               # One ViewModel per screen
│   └── uistate/                 # Sealed UiState classes per screen
├── di/                          # Hilt modules
└── util/                        # Pure utility functions (no Android deps ideally)
```

### Layer rules

| Layer | Allowed dependencies | Forbidden |
|---|---|---|
| `domain` | Pure Kotlin only | Android SDK, Hilt, Room, Retrofit |
| `data` | `domain` interfaces, Android SDK | `presentation` |
| `presentation` | `domain` use cases, Android SDK | Direct `data` access |

---

## 2. SOLID Principles

### S — Single Responsibility
Each class has exactly one reason to change.

```kotlin
// ✅ Good — one responsibility
class FormatDateUseCase @Inject constructor() {
    operator fun invoke(timestamp: Long): String =
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
}

// ❌ Bad — mixing responsibilities
class UserViewModel : ViewModel() {
    fun fetchUser() { /* network call */ }
    fun formatDate(ts: Long): String { /* formatting logic */ }
    fun saveToDb(user: User) { /* database write */ }
}
```

### O — Open/Closed
Open for extension, closed for modification. Use interfaces and sealed classes.

```kotlin
// ✅ Good — extend without modifying
sealed class PaymentMethod {
    data class Card(val last4: String) : PaymentMethod()
    data class UPI(val id: String) : PaymentMethod()
}

fun processPayment(method: PaymentMethod): String = when (method) {
    is PaymentMethod.Card -> "Charging card ending ${method.last4}"
    is PaymentMethod.UPI  -> "Sending via UPI ${method.id}"
}
```

### L — Liskov Substitution
Subtypes must be substitutable for their base types without breaking behavior.

```kotlin
// ✅ Good — all repos honour the same contract
interface UserRepository {
    suspend fun getUser(id: String): Result<User>
}
class RemoteUserRepository @Inject constructor(...) : UserRepository { ... }
class FakeUserRepository : UserRepository { ... } // used in tests
```

### I — Interface Segregation
Prefer small, focused interfaces over wide ones.

```kotlin
// ✅ Good — segregated
interface Readable  { suspend fun read(id: String): Note }
interface Writable  { suspend fun write(note: Note) }
interface Deletable { suspend fun delete(id: String) }

// ❌ Bad — one fat interface
interface NoteRepository : Readable, Writable, Deletable, Searchable, Exportable
```

### D — Dependency Inversion
Depend on abstractions, not concretions. Always inject via Hilt.

```kotlin
// ✅ Good — ViewModel depends on interface, not implementation
class NoteViewModel @Inject constructor(
    private val getNotes: GetNotesUseCase   // domain interface, not Room DAO
) : ViewModel() { ... }
```

---

## 3. ViewModel Rules

- One `ViewModel` per screen. Never share a ViewModel across unrelated screens.
- Expose state via a **single** `StateFlow<UiState>` sealed class.
- Expose one-shot events (navigation, toasts) via `Channel<UiEvent>` → `receiveAsFlow()`.
- Never hold a reference to a `View`, `Context`, `Fragment`, or `Activity`.
  Use `ApplicationContext` only if absolutely necessary, injected via Hilt.
- All business logic belongs in **use cases**, not in the ViewModel.
- ViewModels call use cases; they do not call repositories directly.

```kotlin
// ✅ Canonical ViewModel structure
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onLoginClicked(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            loginUseCase(email, password)
                .onSuccess { _events.send(LoginEvent.NavigateToHome) }
                .onFailure { _uiState.value = LoginUiState.Error(it.message.orEmpty()) }
        }
    }
}

sealed class LoginUiState {
    object Idle    : LoginUiState()
    object Loading : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

sealed class LoginEvent {
    object NavigateToHome : LoginEvent()
}
```

---

## 4. Use Cases

- One public function: `operator fun invoke(...)`.
- Named `<Verb><Noun>UseCase` (e.g., `GetUserProfileUseCase`, `SubmitOrderUseCase`).
- No Android SDK imports.
- Return `Result<T>` or a domain-specific sealed class.
- May call one or more repository interfaces; never another use case unless
  the dependency is explicit and intentional.

```kotlin
class GetUserProfileUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String): Result<UserProfile> =
        runCatching { userRepository.getUserProfile(userId) }
}
```

---

## 5. Repository Pattern

- The **interface** lives in `domain/repository/`.
- The **implementation** lives in `data/repository/`.
- Implementations decide *where* data comes from (remote, local, cache).
- Map DTOs / entities to domain models **inside** the repository, using mapper classes.
- Never return Room entities or Retrofit DTOs outside the data layer.

```kotlin
// domain/repository/UserRepository.kt
interface UserRepository {
    suspend fun getUserProfile(userId: String): UserProfile
    fun observeUser(userId: String): Flow<UserProfile>
}

// data/repository/UserRepositoryImpl.kt
class UserRepositoryImpl @Inject constructor(
    private val api: UserApi,
    private val dao: UserDao,
    private val mapper: UserMapper
) : UserRepository {

    override suspend fun getUserProfile(userId: String): UserProfile {
        val dto = api.fetchUser(userId)
        dao.insertUser(mapper.toEntity(dto))
        return mapper.toDomain(dto)
    }

    override fun observeUser(userId: String): Flow<UserProfile> =
        dao.observeUser(userId).map(mapper::toDomain)
}
```

---

## 6. Kotlin Code Style

### General
- Prefer `val` over `var`. Mutability must be justified.
- Prefer `data class` for models. Never use mutable `var` fields in domain models.
- Use `object` for singletons and stateless utility holders.
- Avoid nullable types (`?`) unless null is a meaningful domain state.
  Use `Result`, `Optional`-style sealed classes, or default values instead.
- Use named arguments for functions with ≥ 3 parameters.
- Maximum function length: **30 lines**. Extract if longer.
- Maximum class length: **200 lines**. Split if larger.
- No magic numbers or strings. Use named constants or string resources.

### Coroutines
- All suspend functions must be called from a coroutine scope or another suspend function.
- Use `viewModelScope` in ViewModels, `lifecycleScope` in UI, custom scopes in repositories.
- Prefer `Flow` for streams; prefer `suspend` for single-shot async operations.
- Always handle exceptions — use `runCatching`, `catch {}` operators, or `CoroutineExceptionHandler`.
- Never use `GlobalScope`.
- Never use `runBlocking` on the main thread.

```kotlin
// ✅ Correct coroutine usage in ViewModel
fun loadData() {
    viewModelScope.launch {
        _uiState.value = UiState.Loading
        runCatching { fetchDataUseCase() }
            .onSuccess { _uiState.value = UiState.Success(it) }
            .onFailure { _uiState.value = UiState.Error(it.message.orEmpty()) }
    }
}
```

### Extension functions
- Only add extension functions for types you don't own (e.g., `String`, `View`).
- Group them in dedicated files: `StringExtensions.kt`, `ViewExtensions.kt`.
- Do not put business logic inside extension functions.

---

## 7. Jetpack Compose Rules

- One Composable = one responsibility. If a composable exceeds ~80 lines, extract sub-composables.
- Never access a `ViewModel` from a child composable. Pass state and lambdas (callbacks) down.
- Never use `mutableStateOf` to hold business/domain state. That belongs in the ViewModel.
- Use `remember` for purely local UI state (e.g., dropdown expansion, focus).
- Annotate all Composables with `@Composable`. Preview functions use `@Preview`.
- Hoist state to the lowest common ancestor that needs it.
- Use `LaunchedEffect(Unit)` for one-time side effects on composition.
- Collect `StateFlow` with `collectAsStateWithLifecycle()` (not `collectAsState()`).

```kotlin
// ✅ Stateless composable — easy to test and preview
@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onLoginClicked: (String, String) -> Unit
) {
    // UI only — no ViewModel reference, no business logic
}

// ✅ Stateful screen that wires ViewModel to UI
@Composable
fun LoginRoute(viewModel: LoginViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LoginScreen(uiState = uiState, onLoginClicked = viewModel::onLoginClicked)
}
```

---

## 8. Dependency Injection — Hilt

- Every injectable class is annotated with `@HiltViewModel`, `@Singleton`, `@ActivityScoped`, etc.
- Modules live in `di/`. One module file per feature or layer (e.g., `NetworkModule.kt`, `DatabaseModule.kt`).
- Bind interfaces to implementations using `@Binds` (preferred) or `@Provides`.
- Never instantiate dependencies manually with `MyClass()` where injection is available.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
}
```

---

## 9. State & UI Events

| Concern | Mechanism |
|---|---|
| Screen state (loading/success/error) | `StateFlow<UiState>` sealed class |
| One-shot events (navigation, snackbar) | `Channel<UiEvent>` → `receiveAsFlow()` |
| Form/input state | `StateFlow` of a data class |
| List data | `StateFlow<List<T>>` or Paging 3 |

Never use `LiveData` for new code. Use `StateFlow` / `SharedFlow`.

---

## 10. Error Handling

- Use `Result<T>` as return type for all suspend repository and use-case functions.
- Map exceptions to domain-specific error sealed classes before they reach the ViewModel.
- The UI layer only knows about `UiState.Error(message: String)` — never raw exceptions.
- Always catch specific exceptions, not just `Exception` / `Throwable`, unless at a boundary.

```kotlin
sealed class AppError {
    object NetworkUnavailable   : AppError()
    object Unauthorised         : AppError()
    data class Unknown(val msg: String) : AppError()
}
```

---

## 11. Testing Requirements

Every new class must have a corresponding test file.

| Component | Test type | Tool |
|---|---|---|
| Use cases | Unit test | JUnit 5 + MockK |
| ViewModels | Unit test | JUnit 5 + Turbine + MockK |
| Repositories | Unit test + integration | JUnit 5 + MockK + Room in-memory |
| Composables | UI test | Compose testing APIs |
| End-to-end flows | Instrumented | Espresso / Hilt test runner |

### Test naming
```kotlin
// Pattern: `given_when_then` or descriptive sentence
@Test
fun `given invalid email, when login clicked, then emits error state`() { ... }
```

### ViewModel test structure
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val loginUseCase: LoginUseCase = mockk()
    private lateinit var viewModel: LoginViewModel

    @BeforeEach
    fun setUp() { viewModel = LoginViewModel(loginUseCase) }

    @Test
    fun `given valid credentials, when login clicked, then navigates to home`() = runTest {
        coEvery { loginUseCase(any(), any()) } returns Result.success(Unit)

        viewModel.events.test {
            viewModel.onLoginClicked("user@example.com", "secret")
            assertThat(awaitItem()).isEqualTo(LoginEvent.NavigateToHome)
        }
    }
}
```

---

## 12. Resource & String Management

- All user-facing strings → `res/values/strings.xml`. No hardcoded strings in Kotlin or Composable code.
- All dimensions → `res/values/dimens.xml`.
- All colours → define in the theme (`MaterialTheme.colorScheme`). Never hardcode hex in Composables.
- All drawable assets: use vector drawables (`VectorDrawable`) whenever possible.
- Name resources with prefix: `screen_feature_element` (e.g., `login_email_hint`).

---

## 13. Navigation

- Use **Jetpack Navigation Component** with type-safe routes (Navigation 2.8+ with `@Serializable`).
- Navigation logic belongs in the UI layer — never in a ViewModel.
  ViewModels emit a `UiEvent.Navigate(...)` and the Composable/Fragment handles it.
- Never pass large objects through navigation arguments. Pass IDs only; load data in the destination.

```kotlin
@Serializable
data class UserDetailRoute(val userId: String)
```

---

## 14. Performance Guidelines

- **Avoid blocking the main thread.** All I/O, parsing, and computation must be on `Dispatchers.IO` or `Dispatchers.Default`.
- Use `Dispatchers.IO` for disk/network, `Dispatchers.Default` for CPU-bound work.
- Use `kotlinx.coroutines.flow.conflate()` or `distinctUntilChanged()` on flows where intermediate emissions can be safely dropped.
- Lazy-load heavy resources. Use Coil for image loading; never load bitmaps manually on the main thread.
- Minimise `recomposition` in Compose: pass stable types, use `@Stable` / `@Immutable`, use `key()` in lists.
- Avoid creating objects inside `@Composable` functions without `remember`.

---

## 15. Security

- Never log sensitive data (tokens, passwords, PII).
- Store secrets using `EncryptedSharedPreferences` or the Android Keystore; never in `BuildConfig` or plain `SharedPreferences`.
- Use `https` for all network traffic; enforce via `network_security_config.xml`.
- Validate all external input before processing.
- Obfuscate release builds with ProGuard/R8. Maintain a `-keep` rules file.

---

## 16. Code Generation Rules for Gemini Code Assist

When generating or modifying code, the agent **must**:

1. **Always match the existing layer.** If editing a file in `domain/`, produce pure Kotlin with no Android imports.
2. **Always create the corresponding test file** when generating a new class.
3. **Never generate `var` fields** in domain models or data classes without explicit instruction.
4. **Never call a repository directly from a ViewModel.** Route through a use case.
5. **Prefer constructor injection** (`@Inject constructor`) over field injection (`@Inject lateinit var`).
6. **Always annotate new ViewModels with `@HiltViewModel`.**
7. **Always use `collectAsStateWithLifecycle()`** instead of `collectAsState()` for Flow collection in Composables.
8. **Respect sealed class exhaustiveness** — every `when` on a sealed class must handle all branches.
9. **Do not introduce new libraries** without a comment explaining why and confirming no existing library covers the need.
10. **All new files must include a package declaration** matching the directory structure.
11. **Do not suppress lint or compiler warnings** unless accompanied by an explanatory comment.
12. **Match the naming conventions** of the surrounding package:
    - Classes: `PascalCase`
    - Functions/variables: `camelCase`
    - Constants: `UPPER_SNAKE_CASE`
    - Files: match the primary class name exactly

---

## 17. Git & Code Review Checklist

Before committing AI-generated code, verify:

- [ ] No hardcoded strings, colours, or dimensions
- [ ] No missing `@Inject` / `@HiltViewModel` annotations
- [ ] No raw exceptions leaking into the UI layer
- [ ] No direct repository calls from a ViewModel
- [ ] StateFlow and Channel declared correctly (private mutable, public immutable)
- [ ] All Composables are stateless (state hoisted or received as parameter)
- [ ] Corresponding test file exists and passes
- [ ] `when` expressions on sealed classes are exhaustive (no `else` unless justified)
- [ ] No `GlobalScope` or `runBlocking` on main thread
- [ ] All network calls use `Dispatchers.IO`

---

## 18. Quick Reference — Anti-Patterns to Reject

| Anti-pattern | Correct alternative |
|---|---|
| `LiveData` in new code | `StateFlow` / `SharedFlow` |
| Repository call inside ViewModel | Route through a use case |
| `var` in domain model | `val` in `data class` |
| Hardcoded string in Kotlin | `stringResource(R.string.x)` |
| `GlobalScope.launch` | `viewModelScope` / `lifecycleScope` |
| `runBlocking` on main thread | Proper coroutine scope |
| Android import in domain layer | Pure Kotlin; restructure if needed |
| Fat ViewModel with 5+ responsibilities | Extract use cases and mappers |
| Nullable `?` type without semantic reason | `Result<T>` or default value |
| Sharing a ViewModel between unrelated screens | Separate ViewModels per screen |
| `collectAsState()` in Compose | `collectAsStateWithLifecycle()` |
| Business logic inside a Composable | Move to ViewModel / use case |

---

*Last updated: 2026. Maintained alongside the project — update this file whenever architectural decisions change.*