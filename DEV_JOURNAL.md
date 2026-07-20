# POS Billing — Dev Journal

A running log of fixes, architecture decisions, and interview-ready explanations from building this project, kept per the Mentor Rule in `backend/AI_INSTRUCTIONS.md`. Entries are appended in build order (oldest first) so the log doubles as a narrative of how the system came together.

Stack: React (Vite) on Vercel · Spring Boot on Railway · PostgreSQL on Neon.

---

## 2026-07-15 — Product Catalog: Service & Controller (CRUD)

**Phase:** Core Domain — Product Catalog CRUD
**Files:** `dto/ProductRequest.java`, `dto/ProductResponse.java`, `service/ProductService.java`, `controller/ProductController.java` (new) — built against existing `model/Product.java`, `repository/ProductRepository.java`, `V2__create_products.sql`

### What it does

- `ProductRequest` — a validated record for create/update input (`@NotBlank`, `@NotNull`, `@DecimalMin`, `@Min`), so malformed input never reaches the service or the database.
- `ProductResponse` — the outbound shape, with a static `from(Product)` factory mapping the entity to a DTO.
- `ProductService` — owns the business rules: SKU uniqueness on create, SKU uniqueness re-check on update (only if the SKU changed), a shared 404 lookup helper, and a manual `updatedAt` stamp (the entity has no `@PreUpdate` hook).
- `ProductController` — thin REST layer mapping `POST / GET / GET {id} / PUT {id} / DELETE {id}` under `/api/products` to the service.

### Why it's written this way

- **DTOs instead of exposing the entity directly.** `Product` is a JPA `@Entity`; returning it straight from the controller couples your HTTP contract to serialization and lazy-loading concerns. Matches the existing `AuthController` / `UserResponse` pattern.
- **`ResponseStatusException` over a custom exception hierarchy.** `AuthService` already throws `ResponseStatusException(HttpStatus.CONFLICT/...)` directly rather than using a `@ControllerAdvice`. Kept consistent rather than introducing a second error-handling paradigm for one entity.
- **Constructor injection, no `@Autowired`.** Matches `AuthService`/`AuthController`; keeps dependencies explicit and unit-testable without a Spring context.
- **SKU re-check only on change:**

  ```java
  boolean skuChanged = !product.getSku().equals(request.sku());
  if (skuChanged && productRepository.existsBySku(request.sku())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists: " + request.sku());
  }
  ```

  Without the `skuChanged` guard, updating a product *without* touching its SKU would still hit `existsBySku` and false-positive against itself.
- **No pagination yet.** `getAll()` returns the full list — fine for a single store's catalog today, but flagged now (not built now) as the thing to add via `Pageable`/`Page<ProductResponse>` once catalog size grows. Scope was "basic CRUD," so this stays out until it's actually needed.
- **Security is inherited for free.** `SecurityConfig` already does `.anyRequest().authenticated()` with only `/api/auth/**` and `/api/health` permitted, so `/api/products/**` is JWT-protected with zero extra config.

### Interview talking points

- **Layered architecture / separation of concerns** — Controller (HTTP transport) → Service (business rules, transactional boundary) → Repository (persistence); each layer has one reason to change.
- **DTO pattern decouples the API contract from the domain model** — the entity schema can evolve without breaking API consumers, and vice versa.
- **Fail-fast validation at the boundary** — `@Valid` + Bean Validation rejects bad input before it touches a service or a SQL statement.
- **Idempotency-aware update semantics** — the `skuChanged` check shows the update path was reasoned about separately from create, not copy-pasted.
- **Stateless REST + JWT** — `SessionCreationPolicy.STATELESS` means this scales horizontally with zero session affinity.
- **Known gap, stated honestly:** no explicit `@Transactional` on service methods yet — Spring Data's single-call CRUD methods are individually transactional by default, but a future multi-table update (e.g. stock ledger writes) will need an explicit `@Transactional` boundary.

---

## 2026-07-15 — Why `GET /api/products` Returned 401

**Phase:** Core Domain — Product Catalog CRUD
**Files:** none changed — this is existing behavior of `security/SecurityConfig.java` and `security/JwtAuthenticationFilter.java`

### What happened

Not a bug — the security config working as designed. `SecurityConfig` permits only `/api/auth/**` and `/api/health`; everything else requires an authenticated request:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**", "/api/health").permitAll()
    .anyRequest().authenticated()
)
```

A browser hitting `/api/products` directly sends no `Authorization` header, so `JwtAuthenticationFilter` never authenticates the request and it falls through to the `authenticationEntryPoint` → `401`.

### How to verify correctly

```bash
# 1. Log in to get a JWT
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"yourpassword"}'
# → { "token": "eyJhbGciOi...", "email": "...", "role": "OWNER" }

# 2. Use that token on the products endpoint
curl http://localhost:8080/api/products \
  -H "Authorization: Bearer eyJhbGciOi..."
```

A plain browser GET will always 401 here — browsers don't attach bearer tokens automatically. Use curl/Postman, or the frontend once it's storing and attaching the JWT.

### Interview talking point

Textbook stateless JWT auth via a security filter chain: no server-side session, every request independently authenticated by inspecting the `Authorization` header. A 401 on an unauthenticated request to a protected resource is correct behavior, not a defect. (If you ever need to sanity-check entity/repository/DB wiring without auth in the loop, temporarily add the route to `permitAll()` — but revert before committing, or the catalog becomes world-readable.)

---

## 2026-07-16 — Bug: Every 4xx Error Was Silently Rewritten to 401

**Phase:** Core Domain — Product Catalog CRUD (end-to-end verification)
**Files:** `security/SecurityConfig.java` (`.requestMatchers` permit list)

### What happened

Ran the full CRUD sweep with a real JWT against a local Postgres instance. The happy paths (200/201/204) all worked, but every case designed to fail — duplicate SKU (409), missing id (404), invalid payload (400) — came back as **401** instead, even though the server logs showed the correct status being resolved internally (`ResponseStatusExceptionResolver : Resolved [... 409 CONFLICT ...]`).

The mechanism, confirmed by reading `JwtAuthenticationFilter` and the Spring Boot version (4.1.0):

1. A controller/service throws `ResponseStatusException` (or validation fails). Spring resolves it and calls `response.sendError(status, reason)`.
2. Because a reason string is attached, the servlet container performs an internal **ERROR dispatch** to `/error` to render the response — effectively a second pass through the entire filter chain.
3. Spring Boot registers the security filter chain for the `ERROR` dispatcher type, but `JwtAuthenticationFilter extends OncePerRequestFilter`, whose `shouldNotFilterErrorDispatch()` returns `true` by default — so it **does not re-run on the error dispatch**.
4. With no `Authentication` in the `SecurityContext` for that dispatch, `.anyRequest().authenticated()` rejects it and fires the `authenticationEntryPoint`, which calls `sendError(401, "Unauthorized")` — overwriting the correct 409/404/400 wholesale.

This is a pre-existing gap, not something introduced by the Product CRUD code — it would have masked any `ResponseStatusException` app-wide (e.g. duplicate-email registration would also have wrongly shown 401 instead of 409). It only surfaced now because this was the first time 4xx paths were exercised end-to-end under JWT auth.

### The fix

```java
.requestMatchers("/api/auth/**", "/api/health", "/error").permitAll()
```

`/error` just renders a status that's already been decided — it isn't a protected resource, so it doesn't need authentication. This is the standard, documented fix for this exact interaction.

### Interview talking points

- **Filter chains apply per *dispatch*, not per incoming HTTP request.** A single client request can trigger multiple internal dispatches (REQUEST, then ERROR on failure), and each one independently passes through the security filter chain.
- **`OncePerRequestFilter`'s "once" is per-dispatch-type, not truly global** — `shouldNotFilterErrorDispatch()` defaults to `true` specifically so custom filters don't accidentally double-run business logic on error rendering. Good default in general, but it means custom auth filters must be reasoned about explicitly for the error path.
- **A masked status code is worse than a wrong one** — it fails silently in a way that looks like a security control working correctly (401), making it easy to ship without noticing the real bug underneath.

---

## 2026-07-16 — Bug: Stack Traces Leaking to API Clients on Every Error

**Phase:** Core Domain — Product Catalog CRUD (end-to-end verification)
**Files:** `application.properties`

### What happened

While verifying the 401-masking fix above, every error response body also contained a full Java stack trace under a `"trace"` field — internal class names, package structure, line numbers, filter-chain internals, all exposed directly to the API client. Not something introduced by Product CRUD; it's Boot's default error controller behavior, just never previously exercised end-to-end.

Root cause took two passes to pin down:

1. First attempt: set `server.error.include-stacktrace=never` in `application.properties`. No effect — trace still appeared. Turned out **Spring Boot DevTools auto-applies development-friendly property defaults**, including forcing stack traces (and messages) to always be included, specifically so they're visible while developing. This silently overrode the file setting.
2. Tried disabling DevTools' property injection wholesale (`spring.devtools.add-properties=false`). This did suppress the trace — but it *also* silently killed `message` and the validation `errors` array, which are genuinely useful for API consumers and not a security concern.
3. Root cause of *that*: Spring Boot 4 renamed the entire property namespace from `server.error.*` to `spring.web.error.*` (confirmed via the autoconfigure jar's `spring-configuration-metadata.json` — the IDE's "deprecated configuration property" diagnostic was the tell). The old keys are deprecated and inconsistently honored; the new keys default to `never` for all three attributes already.

### The fix

```properties
spring.web.error.include-stacktrace=never
spring.web.error.include-message=always
spring.web.error.include-binding-errors=always
```

Verified: 404/409/400 responses now include a clean `message` (and `errors` for validation) with zero stack trace content.

### Interview talking points

- **Framework "convenience" defaults are a production risk if not audited** — DevTools optimizes for local developer experience (see everything!) at the direct expense of what you'd want shipped (leak nothing). Never assume local dev behavior matches prod behavior for anything security-adjacent.
- **When a property silently does nothing, check whether it's been renamed**, not just whether it's spelled right — IDE deprecation diagnostics are exactly the signal to catch this quickly instead of trial-and-error.
- **Information disclosure via stack traces** is a real OWASP-category concern (sensitive data exposure) — line numbers and internal package structure hand an attacker a map of your implementation for free.

---

## 2026-07-17 — Invoicing/Billing Domain: Sales, GST, and Transactional Stock

**Phase:** Core Domain — Invoicing/Billing
**Files:** `V3__add_product_version.sql`, `V4__create_invoices.sql`, `model/InvoiceStatus.java`, `model/Invoice.java`, `model/InvoiceItem.java`, `model/Product.java` (added `@Version`), `repository/InvoiceRepository.java`, `repository/InvoiceItemRepository.java`, `dto/InvoiceItemRequest.java`, `dto/InvoiceRequest.java`, `dto/InvoiceItemResponse.java`, `dto/InvoiceResponse.java`, `dto/InvoiceSummaryResponse.java`, `service/InvoiceService.java`, `controller/InvoiceController.java`, `service/ProductService.java` (delete guard)

### What it does

A sale now composes with the existing Product catalog: `POST /api/invoices` takes a list of `{productId, quantity}` lines, looks up each product's *current* price/GST rate/HSN code server-side, decrements stock, and persists an immutable record of what was actually sold. `GET /api/invoices` lists sales (summary shape, newest first); `GET /api/invoices/{id}` returns full line-item detail; `POST /api/invoices/{id}/void` reverses a sale — restoring stock and marking it `VOID` — without ever allowing an edit.

### Why it's written this way

- **Snapshot pricing, not live lookups.** Each `InvoiceItem` copies `productName`/`sku`/`hsnCode`/`unitPrice`/`gstRate` at the moment of sale. If a product's price changes next week, last week's invoices must still show what was actually charged — this is an audit requirement, not a style preference.
- **The client never supplies price or GST rate** — only `productId` + `quantity`. Pricing is read server-side from the current `Product` row, which closes off a trivial price-tampering vector (a malicious client sending `unitPrice: 0.01`).
- **Invoices are immutable by design** — create, list, get, void. No `PUT`. A sale record that could silently change after money and stock have already moved is an audit-trail risk, so the routing layer doesn't even expose the verb.
- **Optimistic locking (`@Version` on `Product`) for concurrent stock deduction.** Two simultaneous sales of the last unit of a SKU must not both succeed. The flush is explicit and inside the transactional method body (`productRepository.flush()` in a try/catch), not left to Spring's implicit flush-on-commit — if left implicit, the optimistic-lock exception fires *after* the method has already returned, outside any catch block, and surfaces as an unhandled 500 instead of a clean 409. One flush after the loop (not per line item) trades naming the exact conflicting SKU for a single DB round trip — the right call for typical POS cart sizes.
- **BigDecimal rounding happens per line, at the point of calculation**, not once at the end. `price`/`gstRate` are both scale-2 `numeric` columns, but their raw product/quotient in Java lands at scale 4 — round-then-sum (not sum-then-round) keeps the invoice total exactly equal to what a customer could add up from the printed line totals themselves.
- **List and detail use different DTOs** (`InvoiceSummaryResponse` vs `InvoiceResponse`). `InvoiceItem` is a lazy `@OneToMany`; if the list endpoint mapped every invoice's `items`, that's an N+1 — one extra query per invoice in the list. The list endpoint deliberately never touches the lazy collection.
- **Void restores stock instead of leaving it decremented.** A cancelled sale that doesn't give inventory back is a correctness bug, not a shortcut — voiding is a real reversal, not just a status flag.
- **No separate `@Version` on `Invoice`.** Two concurrent voids of the same invoice already collide on `Product`'s version during the stock-restoration flush (whichever commits second rolls back entirely, including the status flip) — so double-restoration is prevented transitively. The plain `if (status == VOID) throw 409` check handles the simpler sequential case (e.g., a double-clicked void button) where there's no actual concurrency, just a stale client state.
- **`Product.delete()` now guards against deleting a product with invoice history** (`409 Conflict`, not a raw DB constraint failure). Without this, deleting a sold product hits the `invoice_items` foreign key and throws an ugly `DataIntegrityViolationException` — precisely the kind of exception the earlier `/error`-dispatch bug would have masked as a 401 before that fix.
- **`invoiceNumber` is derived (`"INV-%06d"` from the id), not a stored column.** Avoids a second write or a separate sequence just to generate a display number. Trade-off, stated up front rather than discovered later: gaps appear in the visible sequence when a create rolls back or an invoice is voided (Postgres's `IDENTITY` sequence still advances). Real GST filing technically wants a consecutive serial per financial year — out of scope here, deferred alongside the CGST/SGST split.

### A real bug found during verification: `getById` needed `@Transactional`

`GET /api/invoices/{id}` initially threw a 500 — `LazyInitializationException: Cannot lazily initialize collection of role 'Invoice.items' ... no session`. Cause: `spring.jpa.open-in-view=false` closes the Hibernate session as soon as the repository call returns; `getById()` wasn't `@Transactional`, so by the time `InvoiceResponse.from(invoice)` tried to read the lazy `items` collection, there was no session left to load it from. Fixed by adding `@Transactional(readOnly = true)` to `getById()`, keeping the session open for the duration of the DTO mapping. `getAll()` didn't need this — it maps to `InvoiceSummaryResponse`, which never touches `items`.

### Interview talking points

- **Snapshotting vs. foreign-key-only references** — a classic trade-off in any system recording a transaction against a mutable catalog (invoicing, e-commerce order history). The FK stays for traceability; the snapshot fields are what actually get displayed and legally matter.
- **Optimistic vs. pessimistic locking, and the alternative** — a single atomic `UPDATE products SET stock_quantity = stock_quantity - :qty WHERE id = :id AND stock_quantity >= :qty` (checking affected-row count) sidesteps the read-modify-write race entirely without `@Version` at all, and is the more common pattern in high-throughput POS/e-commerce systems. `@Version` was chosen here because it's more idiomatic JPA and the entity is needed anyway to build the `InvoiceItem` snapshot — a deliberate trade, not the only correct answer.
- **`open-in-view=false` is a real production setting, not free** — it forces explicit `@Transactional` boundaries around anything touching lazy associations, which is exactly the discipline that caught the `getById` bug above. With `open-in-view=true` (Boot's old default), that bug would have been silently masked by an accidental extra query per request instead of surfacing immediately.
- **Immutability as an architectural constraint, not a missing feature** — no `PUT /api/invoices/{id}` isn't an oversight to point out in a code review; it's the mechanism that guarantees a sale record can't drift from what actually happened.

---

## 2026-07-19 — Role-Based Access Control (RBAC)

**Phase:** Security & Access Control
**Files:** `security/SecurityConfig.java`, `controller/ProductController.java`, `controller/InvoiceController.java`, `service/AuthService.java`, `dto/CreateUserRequest.java` (new), `controller/UserController.java` (new); frontend: `api.js`, `pages/Login.jsx`, `components/Layout.jsx`, `pages/ProductList.jsx`, `pages/InvoiceDetail.jsx`, `pages/StaffForm.jsx` (new), `App.jsx`

### What it does

`Role` (`OWNER`/`CASHIER`) has existed on `User` since the very first migration, and `JwtAuthenticationFilter` has always put a `ROLE_<role>` authority on the `SecurityContext` — but nothing ever checked it. Every logged-in user, regardless of role, could delete products or void invoices. This closes that gap:

- `@PreAuthorize("hasRole('OWNER')")` on `ProductController.create/update/delete` and `InvoiceController.voidInvoice`. Product reads and invoice creation stay open to any authenticated user (CASHIER's actual job).
- A new OWNER-only `POST /api/users` endpoint, backed by a refactored `AuthService`, so an OWNER can actually provision CASHIER logins — previously impossible, since public registration always created an OWNER.
- Frontend hides OWNER-only buttons (Add/Edit/Delete product, Void invoice, Add Staff nav link) based on the role captured at login — cosmetic only, not the real security boundary.

### Why it's written this way

- **Method-level `@PreAuthorize`, not URL-pattern matching in `SecurityConfig`.** Co-located with the endpoint it protects, so "what can a CASHIER not do" is visible on the method itself. Avoids fragile Ant-pattern matching for sub-resource actions like `/api/invoices/{id}/void`, where a path-based rule is easy to get subtly wrong (e.g. matching `/api/invoices/*` vs `/api/invoices/**`).
- **Void is OWNER-only, product management is OWNER-only, selling is not** — mirrors real POS practice: voiding a completed sale is a classic internal-fraud vector (a dishonest cashier rings up a sale, pockets the cash, then voids the invoice to erase the trail), so it's manager-restricted in virtually every real point-of-sale system. Catalog/pricing changes are a management function, not a cashier one.
- **No self-service CASHIER signup, by design.** Public `/api/auth/register` still always creates an OWNER (that's store setup). Staff accounts are provisioned by the OWNER through the new endpoint — the standard pattern (account owner creates logins for employees; employees never self-register into someone else's store).
- **`AuthService` refactor, not duplication.** `register()` and the new `createStaffUser()` both funnel through one private `createUser(email, password, role)` that owns the duplicate-email check and password hashing — adding staff creation didn't mean copy-pasting the existing signup logic.
- **Client-side button-hiding is explicitly cosmetic.** The comment in `Layout.jsx`/`StaffForm.jsx` says so directly: a CASHIER who navigates to a hidden route or calls a restricted endpoint directly still gets a clean 403 from the server. Hiding the button is UX politeness, not the security boundary — worth stating plainly so it never gets mistaken for one.

### A nice validation of earlier work

A `@PreAuthorize` failure throws `AccessDeniedException`, which Spring Security's default handler turns into `sendError(403)` — the *same* internal `/error` dispatch mechanism that was silently rewriting 4xx responses to 401 before the fix documented above. Verified during this work: 403s render with the same clean `{status, error, message, path}` shape as every other error, with zero additional config. The earlier fix was general, not 404/409/400-specific, and this is direct proof of that.

### Interview talking points

- **Authentication vs. authorization** — the filter chain (`.anyRequest().authenticated()`) answers "who are you," `@PreAuthorize` answers "what are you allowed to do." Two different concerns, two different layers, both necessary.
- **Defense in depth, not defense in one place** — the backend enforces the real boundary; the frontend hiding buttons is a UX layer on top, not a substitute. A common junior mistake is to treat hiding a button as "fixing" an authorization gap.
- **Refactor-before-extend** — pulling `createUser()` out of `register()` before adding `createStaffUser()` is the textbook moment to apply DRY: two near-identical code paths that would otherwise silently drift (e.g. a duplicate-email check fixed in one place but not the other).
- **A role model is only as good as its provisioning story** — `Role` existing on `User` since day one but having no way to *create* a CASHIER account was a real, easy-to-miss gap. Modeling a permission without modeling how someone ends up with it is an incomplete feature.

---

## 2026-07-20 — Barcode/SKU-Driven Checkout

**Phase:** Core Domain — Point of Sale UX
**Files:** `repository/ProductRepository.java` (`findBySku`), `service/ProductService.java` (`getBySku`), `controller/ProductController.java` (`GET /api/products/by-sku/{sku}`); frontend: `pages/InvoiceForm.jsx`

### What it does

The New Invoice page now has a scan input, auto-focused on load, sitting above the existing manual product-picker dropdown. Scan a barcode (or type a SKU and hit Enter) and the matching product is added to the cart immediately — no mouse needed. Backed by a new `GET /api/products/by-sku/{sku}` endpoint doing a straight indexed lookup.

### Why it's written this way

- **No camera, no scanning library.** Real USB/Bluetooth barcode scanners are "keyboard wedges" — they emulate a keyboard, typing the decoded value followed by an Enter keystroke into whatever input currently has focus. The entire feature is just: keep an input focused, and treat an Enter keystroke in it as "a scan just happened." This is how virtually every real POS (Square, Lightspeed, etc.) handles hardware scanners, and it's zero new dependencies, zero camera permissions, and works with scanners already deployed in a store today.
- **`findBySku` needs no new index.** `sku` already has a `unique` constraint from the very first products migration (V2), and Postgres backs unique constraints with a B-tree index automatically — the lookup this feature depends on for feeling instant was already fast before this feature existed.
- **Shared `addItemToCart` helper, not two divergent code paths.** Both the scan flow and the pre-existing manual dropdown-picker flow now go through one function that merges into an existing cart line by `productId` (incrementing quantity) rather than always appending a new row. Repeat-scanning the same item is the norm at checkout (a customer buying three of the same item means three scans), so silently creating three separate one-quantity rows for the same product would be a confusing, wrong-looking cart. Making both entry paths share this logic means the cart behaves identically regardless of how an item got added — one behavior, not an inconsistency between "the fast way" and "the manual way."
- **The scan input re-focuses after every attempt, success or failure**, so a cashier working through a full basket never has to touch the mouse between items — the whole point of the feature.
- **A failed lookup (unknown SKU) shows a clean inline error and clears the input, but doesn't block scanning the next item** — one bad scan shouldn't stall the whole checkout.

### A process note, not a code note

Mid-session, the backend's background dev process died unexpectedly (a stray interrupt during a session gap, visible as a bare `^C` in its stderr log with no corresponding shutdown sequence). Caught immediately because the next verification curl call failed with a connection error rather than a real response — a good reminder that "the server was up five minutes ago" isn't the same as "the server is up now," especially across any gap in an interactive session.

### Interview talking points

- **Keyboard-wedge scanning** is worth naming explicitly if asked "how does barcode support work" — it's a hardware/input-layer trick, not a computer-vision problem, and recognizing that distinction is what keeps this feature simple.
- **Shared logic over shared UI** — the scan input and the dropdown picker look completely different, but they call the same `addItemToCart`. Consistency lived in the function, not in trying to make the two UIs identical.
- **An index you already have is a feature you get for free** — this lookup didn't need new schema work because the uniqueness constraint on `sku` was already doing double duty as a performance index.