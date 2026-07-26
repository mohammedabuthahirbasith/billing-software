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

---

## 2026-07-22 — Payments: Record Payment Method at Time of Sale

**Phase:** Core Domain — Point of Sale (Payments)
**Files:** `V5__add_invoice_payment_method.sql`, `model/PaymentMethod.java`, `model/Invoice.java`, `dto/InvoiceRequest.java`, `dto/InvoiceResponse.java`, `dto/InvoiceSummaryResponse.java`, `service/InvoiceService.java`; frontend: `pages/InvoiceForm.jsx`, `pages/InvoiceList.jsx`, `pages/InvoiceDetail.jsx`

### What it does

Every invoice now records how it was paid — `CASH`, `CARD`, or `UPI` — captured as a required field right at checkout, alongside the rest of the sale. Shows up in the invoice list, the invoice detail page, and the API response.

### Why it's written this way

- **Payment at time of sale, not a separate "mark as paid" step.** Confirmed with the user up front: this matches how walk-in retail actually works — the customer pays right there at checkout. Kept the data model to a single required field on `Invoice` rather than a separate `Payment` entity, since split/partial payments were explicitly out of scope. No new domain concept, no new table — just a field that belongs exactly where the rest of the sale's facts already live.
- **`DEFAULT 'CASH'` on the migration backfills history without a separate data migration.** Every invoice created before this feature existed still needs a valid, non-null value for the new `NOT NULL` column — verified this worked correctly: an invoice from two days ago now reports `paymentMethod: "CASH"` after the migration ran, with zero manual data massaging needed.
- **Invalid enum values fail at JSON deserialization, before validation even runs** — sending `"paymentMethod": "BITCOIN"` doesn't need any new error-handling code to produce a clean 400; Jackson rejects the unknown enum value and the existing `spring.web.error.*` configuration (from the earlier stack-trace-leak fix) renders it as a proper message instead of a raw parse-exception dump. One more proof point that fix generalizes correctly to new fields, not just the cases it was originally written for.
- **`voidInvoice()` needed zero changes.** Payment method is descriptive metadata about how a completed sale happened — voiding doesn't touch it, refund tracking is explicitly out of scope for this pass.

### Interview talking points

- **Backfilling a `NOT NULL` column safely** — a `DEFAULT` value on the `ALTER TABLE` statement handles existing rows automatically; the application layer's own validation (`@NotNull` on the request DTO) is what actually enforces the field going forward. Two different mechanisms, two different jobs — the DB default is a one-time historical safety net, not the real source of truth.
- **Scope discipline** — payments *could* have meant partial payments, refunds, multiple tender types split across one sale. Confirming "one method, full amount, at time of sale" up front kept this to a single field instead of a new subsystem, and that was the right size for what was actually asked.

---

## 2026-07-22 — Sales Reports Dashboard

**Phase:** Core Domain — Reporting
**Files:** `V6__add_invoice_report_indexes.sql`, `repository/SalesSummaryRow.java`, `repository/PaymentMethodBreakdownRow.java`, `repository/TopProductRow.java`, `repository/InvoiceRepository.java`, `repository/InvoiceItemRepository.java`, `dto/SalesReportResponse.java`, `service/ReportService.java`, `controller/ReportController.java`; frontend: `pages/Reports.jsx`, `components/Layout.jsx`, `App.jsx`

### What it does

`GET /api/reports/sales?from=...&to=...&topN=10` (OWNER-only) returns total revenue/GST/subtotal, a completed-vs-voided invoice count, a breakdown by payment method, and a ranked top-products list, for any date range. The frontend adds quick presets (Today/This Week/This Month) plus a manual date range, four stat tiles, a payment-method bar comparison, and a top-products table.

### Why it's written this way

- **First feature in the codebase needing real SQL aggregation.** Everything before this was CRUD or single-row lookups. Got an independent design review specifically for the query layer before writing any code — worth it, since the first draft had several real bugs, not just style nits (below).
- **Constructor-expression record projections, not interface projections.** A mismatched alias in an interface projection returns silent `null` at runtime, forever. A constructor expression fails at Hibernate bootstrap the moment the app starts if a type or argument doesn't line up — confirmed this directly: the app failed to start twice while iterating on the JPQL (nested-class FQN awkwardness), and each time the error was immediate and precise, not a mystery `null` discovered days later in production.
- **`COALESCE` only where it's actually needed.** A bare aggregate with no `GROUP BY` always returns one row — `COUNT` is correctly `0` for zero matches, but `SUM` returns SQL `NULL`, an NPE waiting to happen. A `GROUP BY` query has the *opposite* problem: it doesn't return a null-filled row for an empty group, it omits the row entirely. Verified this directly — a live query against real data showed `CARD` (which had zero invoices in range) missing from the raw grouped result, requiring the service layer to explicitly fill in all three payment methods as zero rows rather than letting the frontend guess why one just vanished.
- **`List<T>`, not `Page<T>`, for the top-products query.** Returning `Page<T>` here would silently run a second, wasted `COUNT(*)` query for a `getTotalElements()` value nothing reads, and passing a `Pageable` with a `Sort` gets validated against the entity metamodel instead of the query's own aggregate expression — a `PropertyReferenceException` waiting at runtime, not compile time. `ORDER BY` lives explicitly in the JPQL instead; the `Pageable` is unsorted and only contributes `LIMIT`/`OFFSET`.
- **`Asia/Kolkata`, not UTC, for date-range boundaries.** This is an India-specific GST app — "today" has to mean the actual Indian retail day. Since `createdAt` is stored as `Instant` (zone-agnostic in Postgres), the only place this matters is where a `LocalDate` param gets converted to an `Instant` boundary — one shared constant, no DST edge cases since India has a fixed UTC+5:30 offset.
- **`InvoiceItem`'s existing snapshot columns pay off again.** The top-products query joins `Invoice` (needed for the date/status filter) but never joins `Product` — `productName`/`sku` are already sitting right there on `InvoiceItem` from the Invoicing feature, for exactly this kind of historical-accuracy reason.
- **Reports are OWNER-only**, matching the same reasoning as product management and invoice voiding: revenue and sales figures are business-sensitive, not a cashier's concern.
- **Palette validated, not eyeballed, for the payment-method bars.** Ran the design system's color validator against two hand-picked candidate palettes first — both failed a colorblind-separation check outright. Used the reference palette's own pre-validated first three categorical slots instead (blue/orange/aqua), which are specifically confirmed to hold up across *all* pairs simultaneously, not just neighboring ones — the right bar for a chart where all three series sit on screen at once.

### A live lint catch: setState inside an effect

`Reports.jsx` initially called a shared `loadReport()` helper directly inside a mount `useEffect` — ESLint's `react-hooks/set-state-in-effect` rule flagged it immediately. Fixed by inlining the initial fetch as `apiFetch(...).then(setReport).catch(...)` directly in the effect (matching the exact pattern every other list page in this app already uses), and reserving the shared helper for the preset-button and custom-range event handlers, where calling setState has no such restriction.

### Interview talking points

- **Aggregate in the database, not in application code** — three `SUM`/`GROUP BY` queries instead of fetching every matching invoice and reducing in Java. Scales with data volume instead of against it, and is the difference between "works on my 10 test invoices" and "works."
- **Fail-fast beats silent-wrong** — the constructor-expression vs. interface-projection choice is a small decision with an outsized payoff: a broken query announces itself at the next deploy, not via a support ticket months later asking why a report number looks off.
- **`GROUP BY`'s "missing rows" gap is a classic, easy-to-miss bug class** — it's not about handling nulls, it's about handling *absence*. Anyone who's shipped a dashboard has a story about a category that silently vanished instead of showing zero.
- **Index the columns your new access pattern actually scans** — this is the first feature doing a range scan instead of a PK lookup, and the migration that added the composite index went in in the same change, not as an afterthought once it got slow.

---

## 2026-07-24 — Multi-Tenancy Retrofit

**Phase:** Core Architecture — Multi-Tenancy
**Files:** `V7__add_multi_tenancy.sql`, `model/Store.java` (new), `model/User.java`, `model/Product.java`, `model/Invoice.java`, `security/AuthenticatedUser.java` (new), `security/CurrentUser.java` (new), `security/JwtService.java`, `security/JwtAuthenticationFilter.java`, `controller/MeController.java`, `repository/{Product,Invoice,InvoiceItem,Store}Repository.java`, `service/{Product,Invoice,Report,Auth}Service.java`, `dto/{RegisterRequest,UserResponse}.java`; frontend: `pages/Register.jsx`, `pages/Dashboard.jsx`

### What it does

Every store is now an isolated tenant. A `Store` row is the boundary; `users`, `products`, and `invoices` each carry a `store_id`. Signing up via `POST /api/auth/register` now provisions a brand-new store and its first OWNER together — self-serve SaaS onboarding, not just "add a user." Staff created via "Add Staff" join the creator's existing store, never a new one. All existing production data was migrated into one auto-created "Default Store" so nothing already live had to change hands.

### Why it's written this way

This is the highest-risk, largest-blast-radius change in the project's history — every prior feature was additive; this one retrofits isolation onto an already-deployed, already-populated single-tenant system, where a missed filter anywhere means one store's data leaks into another's. It went through an adversarial design review before any code was written, and that review caught two serious bugs that a normal read-through would very plausibly have missed:

1. **`InvoiceService.getAll()` had no `@PreAuthorize` and called the inherited, unscoped `JpaRepository.findAll()`.** Harmless with one shared tenant; the instant a second store existed, every logged-in user of *any* store would see every invoice ever created by *any* store through a perfectly ordinary `GET /api/invoices`. Verified directly: before the fix, this would have been the single worst possible instance of the exact failure this whole feature exists to prevent.
2. **`ProductRepository.findBySku()` (the barcode-scan lookup) was unscoped, and SKU uniqueness was moving from global to per-store.** Once two different stores legitimately use the same SKU, an unscoped lookup expecting `Optional<Product>` starts throwing `IncorrectResultSizeDataAccessException` — a 500 in ordinary daily use, not just under attack.

Both are fixed by construction, not by remembering to check them:

- **Delete-and-replace, not add-alongside, for repository methods that can be deleted.** `existsBySku`/`findBySku` were removed entirely rather than left next to their store-scoped replacements — this turns every call site that was missed into a **compile error** instead of a silent runtime leak. The two inherited methods that *can't* be deleted this way (`findAll()`/`findById()`) are the harder residual risk — every current call site is fixed, but nothing stops a future line of code from calling one directly. Noted explicitly as a known gap; an ArchUnit guard would close it permanently and is a good next step, not built in this pass.
- **A custom JWT principal, not a bare email string.** `AuthenticatedUser` (userId, email, role, storeId) is decoded once by `JwtAuthenticationFilter` and set as the `Authentication`'s principal, with a static `CurrentUser.get()` helper reading it from `SecurityContextHolder`. This is what let every service method reach the current store with **zero controller signature changes anywhere** — only service method *bodies* gained a one-line `CurrentUser.get().storeId()` call. It also means `storeId` costs no extra DB query per request, the entire point of a stateless JWT.
- **`AuthenticatedUser` implements `AuthenticatedPrincipal`, not just a bare record.** `Authentication.getName()` special-cases `UserDetails`/`AuthenticatedPrincipal` and falls back to `principal.toString()` for anything else — without this, `getName()` would silently start returning a record dump instead of the email for any caller. `MeController` was confirmed as today's only caller, but implementing the interface makes this robust against any future one too, not just the one found by inspection.
- **No `store` setter anywhere, `updatable = false` on the column.** A user's (and product's, and invoice's) store is fixed at creation. Combined with no setter, a JWT's `storeId` claim structurally cannot go stale relative to the DB for the life of a token — the same accepted trade-off the existing `role` claim already carried, just extended to a second field, not a new risk class.
- **No `DEFAULT` ever added to the new `store_id` columns**, even transiently. Nullable → backfill → `NOT NULL` with no default at any point in between means a future code path that forgets to set a store on insert *fails loudly*, rather than silently attributing a new tenant's row to "Default Store."
- **The migration looks up the old `products.sku` unique constraint's name dynamically** (via `information_schema`) rather than assuming Postgres's naming convention — a one-shot migration against live production data doesn't get a second attempt if that guess were wrong.
- **Email stays globally unique, not per-store**, stated as a deliberate choice: login has no store selector, and `findByEmail` is relied on everywhere to return at most one user. A person needing accounts at two different stores needs two different email addresses — a real, accepted limitation, not an oversight.

### Two more real bugs, caught during verification, not by review

`GET /api/me` 500'd with `Could not initialize proxy [Store#1] - no session` — the exact same class of bug as the earlier `InvoiceService.getById()` lazy-loading fix from the Invoicing feature, just in a new spot. `MeController.me()` used `storeRepository.getReferenceById(...)` (a lazy proxy, meant for FK-only writes) but then called `.getName()` on it — by the time that lazy field was actually accessed, the session was already closed. The identical bug was lurking in `AuthService.createStaffUser()` for the same reason: it used `getReferenceById` even though the shared `createUser()` helper it feeds always reads `store.getName()` for the response. Both fixed by switching to `findById` (an eager, fully-materialized fetch) — `getReferenceById`'s no-extra-query optimization only makes sense when a caller genuinely needs nothing but the id for an FK reference, which is true in `ProductService.create()`/`InvoiceService.create()` (left as `getReferenceById`, correctly) but was never true in either of these two spots.

### Interview talking points

- **Shared-schema multi-tenancy with a `store_id` discriminator** — the standard, simplest approach for this scale, versus schema-per-tenant or database-per-tenant, which buy stronger isolation at real operational cost (migrations run N times, connection pool per tenant, etc.) that isn't justified here.
- **A design review is worth the most exactly where the blast radius is largest** — every other feature this session got a review pass too, but this is the one where "found two real, serious cross-tenant leaks before a single line was written" is a concrete, countable result, not a hypothetical benefit.
- **Compile errors are a stronger correctness guarantee than a code review.** Deleting `existsBySku`/`findBySku` outright instead of leaving them alongside scoped replacements is a small technique with an outsized payoff: it converts "did the reviewer catch every call site" into "did it build."
- **Statelessness has a real, nameable trade-off, not just a real, nameable benefit.** A JWT's `storeId` claim can't be revoked mid-lifetime short of waiting out its expiry — worth being able to say exactly what that trade-off is and why it was accepted (immutable store assignment by design), rather than discovering it looks like a bug later.
- **The same bug class recurring is a signal, not a coincidence.** Two lazy-initialization-outside-a-session bugs in one project, in different files, both caught only by actually running the code — worth naming the pattern explicitly (`open-in-view=false` forces `@Transactional` discipline everywhere a lazy field crosses a service boundary) rather than treating each occurrence as an isolated one-off.

---

## 2026-07-24 — Post-Deploy Hardening: JWT Compatibility, an ArchUnit Guard, and Rate Limiting

**Phase:** Core Architecture — Hardening
**Files:** `security/JwtAuthenticationFilter.java`, `security/RegisterRateLimitFilter.java` (new), `security/SecurityConfig.java`, `pom.xml`, `test/architecture/RepositoryScopingArchTest.java` (new)

With the original roadmap (auth, GST invoicing, payments, reports, multi-tenancy) fully shipped, this round wasn't a new feature — it was closing gaps the multi-tenancy retrofit either caused or knowingly deferred.

### A real production bug, caught within minutes of deploy

Right after the multi-tenancy migration went live, `GET /api/me` started 500ing for the account that had been logged in throughout testing. Root cause: that browser still held a JWT issued by the *old* `JwtService`, minted before the `storeId` claim existed. `JwtAuthenticationFilter` parsed it fine (signature still valid, nothing else about the token changed) and built an `AuthenticatedUser` with `storeId = null` — then `MeController` called `storeRepository.findById(null)`, which Spring Data rejects outright with `IllegalArgumentException`, surfacing as a 500 instead of a clean 401.

This is the standard "old client, new server" problem that shows up whenever a JWT's payload shape changes: existing tokens don't retroactively gain new claims. Fixed at the filter level, not the controller level — if `storeId` is missing from a token's claims, the filter now simply doesn't authenticate the request at all, rather than authenticating it with a null field that every store-scoped code path assumes is always present. Verified directly: a fresh token still authenticates normally (`200`), a token with no `Authorization` header gets a clean `401`, and a garbage/malformed token also gets a clean `401` — none of the three paths crash.

### Closing the one gap multi-tenancy couldn't close by construction

The multi-tenancy retrofit deleted `existsBySku`/`findBySku` outright specifically so a missed call site would fail to *compile*, not fail silently at runtime — but `findAll()`/`findById()` are inherited from `JpaRepository` and can't be deleted the same way. That gap is now closed with an ArchUnit test: no class in `com.billing.billing.service` may call `findAll()`/`findById()` on any Spring Data repository (with `StoreRepository` deliberately excluded — `Store` *is* the tenant boundary, not tenant-owned data, so looking one up by the current user's own `storeId` from their JWT is never a cross-tenant risk, unlike `Product`/`Invoice`).

Didn't just trust that the rule looked right — verified it two ways: it passes cleanly against the current codebase, and it actually *catches* a violation. Temporarily changed `ProductService.getAll()` back to a bare `productRepository.findAll()` (exactly the kind of regression this guard exists to prevent) and confirmed the build failed with the precise file and line, then reverted. A rule that only ever passes is worse than no rule — it looks like coverage without providing any.

One iteration was needed to get there: the first version flagged `AuthService.createStaffUser()`'s `storeRepository.findById(...)` as a violation. That's a real distinction, not a false positive to shrug off — `Store` isn't a tenant-scoped *resource* the way `Product`/`Invoice` are, it's the tenant identity itself, and the id being looked up is always the caller's own `storeId`, never attacker-controlled. Excluding it by type (with the reasoning in a comment) keeps the guard meaningful instead of either missing real gaps or crying wolf on a safe pattern.

### Rate limiting a bigger attack surface than it used to be

`POST /api/auth/register` is `permitAll()` and, since multi-tenancy, provisions a whole new `Store` per request — a bigger blast radius than a typical "add a user row" signup endpoint, and worth defending against a scripted flood of fake stores. Added a simple in-memory per-IP fixed-window limiter (5 attempts/hour) as a servlet filter, deliberately not reaching for Redis or any distributed rate-limiting infrastructure this single-instance, free-tier deployment doesn't otherwise need. Reads `X-Forwarded-For` first (Render sits behind a proxy, so the raw socket address is the load balancer's, not the client's) falling back to the direct remote address. Verified directly: 5 rapid registrations succeed, the 6th gets a clean `429`, and unrelated routes like login are unaffected.

Known, accepted simplification: the per-IP window map is never swept, so it grows with every distinct IP ever seen. Not worth a cleanup task at this project's actual traffic volume — and a free-tier host that sleeps/restarts on idle resets it anyway.

### Interview talking points

- **"Old client, new server" is a general JWT lesson, not a one-off bug** — any time a token's claim shape changes, existing tokens in the wild don't get the new claim, and the code path that consumes it needs to treat "claim is missing" as invalid, not as null-and-proceed.
- **A guard rail is only as good as its proven failure mode** — writing an ArchUnit rule and trusting it compiles is not the same as knowing it fires. Deliberately breaking the code it's meant to catch, confirming the failure, then fixing it back is the actual verification step, the same discipline as testing a smoke detector by triggering it, not just checking the light is on.
- **A false positive can be a correctness finding in disguise** — the `StoreRepository` exclusion wasn't papering over a flaky rule, it was the process forcing an explicit answer to "is Store tenant-owned data, or the tenant itself?" — a distinction worth having written down regardless of the test.
- **Match infrastructure to actual scale** — an in-memory rate limiter with an unswept map is a real simplification, but it's the *correct* one for a single-instance deployment with this traffic profile; reaching for Redis here would be solving a problem the project doesn't have.

---

## 2026-07-25 — Returns / Refunds

**Phase:** Core Domain — Returns
**Files:** `V8__add_invoice_returns.sql`, `model/InvoiceReturn.java`, `model/InvoiceReturnItem.java`, `repository/InvoiceReturnRepository.java`, `repository/InvoiceReturnItemRepository.java`, `repository/InvoiceItemRepository.java`, `dto/ReturnRequest.java`, `dto/ReturnResponse.java`, `dto/InvoiceItemResponse.java`, `service/ReturnService.java`, `service/InvoiceService.java`, `controller/ReturnController.java`; frontend: `pages/InvoiceDetail.jsx`

### What it does

A customer can now return *some* of what they bought — a specific quantity on a specific invoice line — rather than only the all-or-nothing `voidInvoice()` that already existed. Stock is restored for the returned quantity, a refund amount is calculated and recorded, and the rest of the sale stands untouched. OWNER-only, matching void.

### Why it's written this way

- **A return is a new, append-only record, never a mutation of the original invoice.** Invoices and their items were made immutable back in the Invoicing feature specifically so what was originally sold is a permanent fact; a `Return`/`ReturnItem` pair references the invoice/item it came from instead of rewriting anything, so the audit trail — what was sold vs. what was later returned — stays fully intact. `InvoiceReturnItem` carries no denormalized product snapshot at all (unlike `InvoiceItem`'s snapshot of `Product`) — it always points at an `InvoiceItem` that's *already* an immutable snapshot, so re-duplicating those fields a second time would be pure redundancy, not protection.
- **The over-return check is computed on demand, not tracked as a mutable counter.** Rather than adding a `quantityReturned` field to `InvoiceItem` (which would break its immutability) or to `Invoice`, "how much of this line has already been returned" is a small aggregate query (`SUM(quantityReturned) WHERE invoiceItem.id = :id`) run fresh every time a new return is validated. Slightly more DB work per request, in exchange for the append-only model staying genuinely append-only — no field on an "immutable" entity is quietly mutated after all.
- **The frontend's "already returned" number is a display hint, not the enforcement.** The invoice detail page computes it client-side by summing the return history it already fetched — cheap, and it means the backend's own aggregate query is the *only* thing that can ever accept or reject a return. The two numbers being computed two different ways (one for display, one for enforcement) sounds like a smell, but it's actually what makes them incapable of diverging into a real bug: nothing the frontend shows is ever trusted for a decision the backend makes.
- **Returns and void were made deliberately mutually exclusive on any one invoice, and this was the actual point in the plan where a real interaction bug had to be resolved.** `voidInvoice()`'s existing restock loop restores each line's *full original* quantity — that logic predates returns and had no reason to think about "some units already restocked." Left alone, a return followed by a void on the same invoice would restock the returned units twice: once when the return was processed, once again when void ran its unconditional full-quantity restock. Rather than teaching `voidInvoice()` to subtract already-returned quantities (real but more invasive), a single new guard — reject voiding an invoice that already has any returns — closes the double-restock path entirely, on top of the symmetric, already-obvious rule that a voided invoice can't accept new returns either.
- **`findByIdAndInvoice_Id` on `InvoiceItemRepository`, not a scan of `invoice.getItems()`.** Validating that a requested `invoiceItemId` actually belongs to the target invoice could have meant loading the whole lazy `items` collection and filtering it in Java for one match. A direct, doubly-scoped repository lookup does the same validation as a single indexed query, and avoids materializing every line on the invoice just to check one of them.

### Interview talking points

- **Append-only correction records are a recognizable pattern beyond this app** — accounting systems never rewrite a posted transaction, they post a correcting entry against it. This feature is that pattern applied to a POS: the original sale is permanent, a return is a new fact layered on top of it, and the "current" position is always derivable by combining the two rather than by mutating history.
- **A validation split between "what's shown" and "what's enforced" is a legitimate pattern, not a shortcut** — it's the same principle behind never trusting client-side form validation alone: the UI hint improves the experience, the server call is what actually decides, and keeping them cleanly separated (rather than trying to make the frontend's number authoritative) is what prevents them from ever contradicting each other.
- **The void/return interaction is the kind of bug that only shows up by tracing what already-shipped code does under a new feature's inputs**, not by reviewing the new feature in isolation — `voidInvoice()`'s restock loop was correct code, fully tested, when it was the only thing that ever restocked a product; it became wrong the moment a second, independent restocking path (returns) could run against the same invoice first. Worth explicitly re-checking every existing mutation path a new feature's data could interact with, not just the new feature's own logic.

---

## 2026-07-26 — UX Polish: Loading States and Toast Notifications

**Phase:** Frontend — Cross-Cutting UX
**Files:** `components/Toast.jsx` (new), `contexts/toastContext.js` (new), `hooks/useToast.js` (new), `components/Button.jsx`, `App.jsx`, and every page with a user-triggered action: `Login.jsx`, `Register.jsx`, `ProductForm.jsx`, `ProductList.jsx`, `InvoiceForm.jsx`, `StaffForm.jsx`, `InvoiceDetail.jsx`

### What it does

Every button that triggers a backend call now shows a spinner and disables itself while the request is in flight, instead of looking clickable (and clickable-again) with nothing visibly happening. Actions that succeed — creating a product, voiding an invoice, processing a return, adding staff — now surface a toast notification confirming what happened, instead of the user having to infer success from a page navigating or a table quietly updating.

### Why it's written this way

- **One `loading` prop on the existing `Button` component, not a bespoke spinner per page.** Every submit flow in this app already followed the identical shape — `setError(null)`, `try { await apiFetch(...) }`, `catch { setError(err.message) }` — so the fix belonged in one shared place, not seven. `Button` grew a `loading` boolean that forces `disabled` and renders a small inline spinner alongside the label; every page just needed one new piece of state (`isSubmitting`, or something more specific like `isVoiding`/`deletingId` where multiple independent actions can be in flight on the same page) threaded into that one prop.
- **Per-row loading state where the action isn't singular.** `ProductList`'s delete button and `InvoiceDetail`'s void/return actions each needed their *own* in-flight tracking (`deletingId` for "which row," separate `isVoiding`/`isReturning` booleans) rather than one shared flag — a single boolean would have disabled every row's delete button while only one was actually running, which is a worse experience than no loading state at all.
- **Success toasts, not error toasts.** Every action already had (and keeps) an inline error banner tied to its form/page — that's the right place for something the user needs to read and act on before retrying. Toasts were added purely for the *silent-success* problem: actions that either navigate away immediately (so there's no time to read an inline message) or stay on the page with only a subtle state change (a badge flipping, a row disappearing). Layering an error toast on top of an already-shown inline error would just be the same information twice; the two mechanisms were kept for different problems on purpose, not merged.
- **A context split across three tiny files, not one.** `Toast.jsx` originally exported both the `ToastProvider` component and a `useToast()` hook from the same file — ESLint's `react-refresh/only-export-components` rule rejected this immediately, because a file mixing component and non-component exports breaks Vite's fast-refresh boundary detection. Splitting into `contexts/toastContext.js` (just the `createContext` call), `components/Toast.jsx` (the provider + rendering), and `hooks/useToast.js` (the hook) satisfies the rule and, incidentally, matches how the rest of the codebase already separates concerns (plain logic in `.js`, components in `.jsx`).
- **`finally` for actions that stay on the page, a plain `catch`-side reset for actions that navigate away.** Delete/void/return/add-staff all reset their loading flag in a `finally` block since the component remains mounted either way. Login/Register/create-product/create-invoice only reset it in the `catch` — on success they navigate immediately and the component unmounts, so there's no moment where the button would visibly flip back to "not loading" right before disappearing; the button just stays in its loading state through the transition, which reads as smoother, not as a bug.

### Interview talking points

- **A cross-cutting UX concern is a strong signal to look for the one place all the call sites already agree on, not to touch every call site differently.** Every form in this app happened to follow byte-for-byte the same try/catch shape before this change — that uniformity is what made a two-file, mostly-mechanical rollout possible instead of a bespoke change per page.
- **Toasts and inline errors solve different problems and both stayed** — the fix here wasn't "replace inline errors with toasts," it was "add toasts for the gap inline errors don't cover" (silent success). Recognizing that two mechanisms solve different problems, rather than picking one to standardize on, avoided deleting a pattern that was already working.
- **A linter catching a fast-refresh violation is worth understanding, not just satisfying** — the fix (splitting exports by kind: context object, component, hook) is a small structural discipline that keeps paying off as the app grows, not a one-off workaround for this file.

### Follow-up, same day — a real bug found by actually using it

Manual testing of the round above surfaced one genuine bug and two design misses:

- **`apiFetch` hardcoded the string `"Unauthorized"` for every single `401`, discarding whatever the server actually said.** `AuthService.login()` already returns `"Invalid email or password"` in the response body on bad credentials — confirmed directly (`curl` against `/api/auth/login` with a wrong password returns `{"message":"Invalid email or password", ...}`) — but the frontend never looked at it, because the 401 branch threw a fixed string before the generic error-parsing code below it ever ran. This one hardcoded branch was written for a different scenario (an expired/invalid session token on a protected route, where there's no more specific message to show) but ended up swallowing every 401 site-wide, including login's own deliberately informative one. Fixed by removing the special case entirely: **every** error status now goes through the same body-parsing logic, and clearing the stored token/role is a side effect that still happens on 401, decoupled from what message gets shown.
- **Toast placement.** The first pass centered toasts in a full-width bar across the top of the viewport — visually loud and in the way of the page header. Moved to a compact, bottom-right stack (new toasts appear closest to the corner, older ones pushed upward), matching the placement convention most users already have muscle memory for from other apps.
- **The loading spinner was a generic circular spin.** Replaced with a small custom "scanning barcode" pulse — five bars of varying width animating in a staggered wave — deliberately tying the loading indicator back to this app's actual signature feature (keyboard-wedge barcode checkout) instead of a generic, could-be-any-app spinner. Uses `currentColor` so it automatically matches whatever text color the surrounding button already has, no separate color prop needed.

**Interview talking point:** the `apiFetch` bug is a good example of why a defensive special case written for one real scenario can quietly break a different one it was never tested against — the fix wasn't "add a special case for login," it was recognizing the *general* rule (always show the server's actual message) already covered the specific case correctly, and the special case was the bug, not the fix.

### Second follow-up — the spinner still wasn't visible, and it wasn't a wiring bug

After the fixes above, the spinner still didn't show up on any action. The code driving it was correct — `isSubmitting`/`isVoiding`/etc. really were being set to `true` before the `await` and flipped back afterward — but a local Spring Boot backend on the same machine can respond in single-digit milliseconds. If the state flips to loading and back to not-loading faster than the browser's next paint (~16ms), the loading frame is never actually painted to the screen — not "brief," genuinely never rendered. This isn't specific to login or to this app; it's a generic consequence of tying a loading indicator directly to a fetch's real duration.

The fix is a small, deliberately artificial one: `withMinDelay()` wraps the request promise in a `Promise.allSettled` alongside a fixed timer, so the combined await never resolves faster than ~350ms regardless of how fast the actual response was, while still surfacing the real result (or the real error) once both are done. Applied at all seven action call sites that already had a loading flag. This is a well-established pattern for exactly this problem — deliberately padding a fast response so a "this is working" signal is reliably perceivable — not a workaround specific to this app, and it means the spinner behaves identically whether the backend is on the same machine or across a real network.

**Interview talking point:** a loading indicator's job is *perceptual*, not just technically correct — code that flips a boolean at exactly the right two moments can still fail its actual purpose if those two moments are closer together than a screen can render. Worth checking not just "does the state change happen" but "is there guaranteed to be a paintable frame where it's visible."