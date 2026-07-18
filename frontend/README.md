# Marketplace Frontend 
## Design system

All tokens live in `js/tailwind-config.js` (Tailwind Play CDN config) and
`css/styles.css` (custom shadows/animations Tailwind's utilities don't cover).
Change them there to re-theme the whole app.

- **Primary**: Electric Blue `#0066ff` (`primary-container`) — buttons, links, active states
- **Surface layers**: `surface`, `surface-container-low/high/highest` — Material 3
  tonal elevation instead of drop shadows for most surfaces
- **Type**: Geist for headlines/labels/buttons, Inter for body text
- **Icons**: Material Symbols Outlined (loaded via Google Fonts)
- `.product-card-shadow` — soft elevation on catalog/related-product cards
- `.status-amber/green/blue/red` — order status badge colors, mapped to the
  backend's `OrderStatus` enum
- `.glass-card` — frosted glass stat cards on the admin dashboard

## Pages

| File | Purpose |
|---|---|
| `index.html` | Catalog — sidebar category/price filters, search, sortable product grid |
| `product.html` | Product detail — hero image, spec sheet, quantity + add to cart |
| `cart.html` | Full-page cart — quantity steppers, order summary, clear cart |
| `checkout.html` | 4-state flow (form → loading → success → error), real Stripe Elements |
| `login.html` | Combined login/register with toggle (matches the Stitch design) |
| `orders.html` | Customer order history — expandable rows, status badges |
| `admin.html` | Product CRUD + order overview, gated to `role: ADMIN` |


## Setup

1. Open `js/config.js` and set:
   ```js
   window.API_BASE_URL = 'http://localhost:8080';        // your Spring Boot backend
   window.STRIPE_PUBLISHABLE_KEY = 'pk_test_...';         // from the Stripe dashboard
   ```
2. Serve the folder — e.g. `npx serve .` or Python's `python3 -m http.server` —
   rather than opening via `file://`, since `fetch()` calls to the backend need a
   proper origin for CORS to behave.
3. Make sure the backend's `CORS_ALLOWED_ORIGINS` env var includes wherever this
   frontend is served from.

## Notes on how it's wired to the backend

- **Auth**: JWT access/refresh tokens are stored in `localStorage` after login/register
  (`js/api.js` → `Auth` object). Every authenticated request attaches
  `Authorization: Bearer <token>`. A 401 response clears the session and redirects to
  `login.html`.
- **Cart is server-side**, every add/update/remove call hits
  `/api/cart/**` directly, so the stock numbers shown always reflect the database,
  not a stale client guess.
- **Checkout → Stripe**: `checkout.html` calls `POST /api/orders/checkout`, which
  atomically reserves stock and returns a Stripe `clientSecret`. The page mounts
  Stripe's Payment Element against that secret and calls `stripe.confirmPayment()`.
  The order itself only becomes `PAID` once your backend's webhook receives
  confirmation from Stripe not from this client-side call succeeding which is
  why the loading state stays up briefly even after `confirmPayment()` resolves.
- **Images** are pulled from Unsplash's `source.unsplash.com` as placeholders keyed
  by product id/category.swap in real product photography by replacing the `src`
  logic in `products.js`, `product.html`, `cart.html`, `orders.html`, and `admin.html`.

## What's intentionally left out
- Client-side route guarding beyond redirect-on-401 
- Refresh-token renewal (currently just logs the user out on expiry)
- Real product photography / CDN
- Wishlist, promo codes, and product reviews (present visually in the original
 mockups but not wired i.e no backend support for them yet)
