# QLess — Subtle UI redesign spec

Reference screens (design-tool source files: read them for exact layout, spacing and copy — inline styles hold the values; `{{accent}}` = #4F52B8. They will not render on their own in a browser):
- `Main.html` → HomeActivity
- `Compare.html` → ProductDetailActivity (price comparison)
- `Checkout.html` → CheckoutActivity / CartActivity
- `Reservation.html` → ReservationsActivity (active reservation detail)
- `Admin.html` → `admin/index.html` + `admin/style.css` (web panel)

Goal: restyle the existing Java + XML + Material 3 app to match these screens. Keep all existing logic, IDs used in Java, Firebase calls and navigation. Change only resources and layouts.

## Principles
- Quiet: off-white background, white cards with 1dp hairline borders, no gradients, no elevation shadows, no emoji.
- One accent (muted indigo) used only for primary buttons, selected chips/radio, active nav item, progress.
- Green used only for "Lowest" price tag and "Reserved/Collected" states.
- Prices, pickup codes and countdowns in a monospace font.

## Colors (replace/extend `res/values/colors.xml`)
| Token | Hex | Use |
|---|---|---|
| background | #F7F7F5 | screen background |
| surface | #FFFFFF | cards, bottom bars, inputs |
| surface_muted | #F0EFEB | image placeholders, thumbnails |
| outline | #E7E6E2 | card/input borders (1dp) |
| divider | #EFEEEA | rows inside cards |
| text_primary | #1C1C22 | titles, body |
| text_secondary | #5E5E68 | supporting text |
| text_tertiary | #6E6E78 | captions, meta, inactive nav |
| primary | #4F52B8 | accent (buttons, selection) |
| primary_container | #EEEEF6 | info banner background |
| on_primary_container | #3E4196 | badge text on primary_container |
| success | #2F7A55 | "Lowest", reserved check |
| success_container | #EAF3EE | badge/check background |
| warning | #9A5B13 | time running out |

Remove the gradient drawables (gradient_start/center/end) from screens; keep color names if referenced, but point them to the flat values above.

## Typography
- Font: **Geist** (400/500/600) for UI, **Geist Mono** (400/500) for prices, codes, timers. Use Android downloadable Google Fonts (`res/font/` + font provider) or bundle the .ttf files.
- Sizes (sp): screen title 22–26 / 500 / letterSpacing -0.02; section title 14–15 / 500; body 14; meta/caption 12; nav label 11; pickup code 38 mono with letterSpacing 0.08.
- Avoid 700 weights; 500 is the "bold".

## Shape & spacing
- Screen horizontal padding: 20dp.
- Corner radius: cards 14dp, large card / image 18dp, buttons & inputs 12dp, chips fully rounded, badges 6dp.
- Cards: `MaterialCardView` with `cardElevation=0`, `strokeWidth=1dp`, `strokeColor=@color/outline`.
- Touch targets ≥ 44dp. Primary button height 48–50dp, chips 36dp, list rows ≥ 64dp.
- Section spacing: 22–26dp between blocks; 8dp between stacked store cards.

## Components
- **Primary button**: filled, primary bg, white text 15sp/500, radius 12dp, no elevation.
- **Secondary/text button**: transparent, text_secondary.
- **Chip**: 36dp, outline stroke, white bg; selected = primary fill + white text.
- **Search field**: 48dp, white, 1dp outline, radius 14dp, leading search icon in text_tertiary.
- **List row in card**: 44dp thumbnail (radius 10, surface_muted) · title 14/500 + meta 12 text_tertiary · price mono right. Divider between rows.
- **Store option (Compare)**: card with radio ring on left; selected = 1.5dp primary stroke + filled dot; "Lowest" badge (success on success_container).
- **Info banner (Checkout)**: primary_container bg, clock icon in primary, text 13sp text_secondary with "48 hours" emphasized.
- **Bottom bar**: white, 1dp top outline, holds quantity stepper (−/qty/+) + primary button.
- **Bottom navigation**: 5 items (Home, Search, Cart, Pickups, Profile), white, top hairline, active = primary, inactive = text_tertiary, line icons 1.8 stroke, labels always shown, no indicator pill.
- **Countdown**: mono text + 4dp rounded LinearProgressIndicator (track divider, indicator primary).
- Icons: outlined/line style only (Material Symbols Outlined, weight ~300).

## Screens
1. **Home**: wordmark "QLess" left, location pill right (opens LocationPickerActivity) → "What do you need today?" → search → category chips (horizontal scroll) → active reservation card (green dot, "1 reservation ready", "Store · collect within 47 h", chevron) → "Lowest prices near you" card list with "See all" → recommendations caption → bottom nav.
2. **Compare (ProductDetail)**: back + bookmark icon buttons → image (180dp, radius 18) → category caption, product name 22sp, price range ("€1.19 to €1.79 across 4 stores") → "Choose a store" list sorted by price → bottom bar: quantity stepper + "Add to cart · €total".
3. **Checkout**: back + "Review reservation" → items grouped per store (store name + distance header, card of rows) → 48 h / pay-at-pickup / 10% fee banner → bottom summary (stores · items total, "Due now €0.00") + "Confirm reservation".
4. **Reservation**: close button → green check circle, "Reserved at {store}", "Show this code at the counter and pay there." → code card (PICKUP CODE label, big mono code, divider, "Collect within" + countdown, progress bar, "Held until …") → items card with "Pay at pickup" total → store address row → "Get directions" primary + "Cancel reservation" text button.
5. **Admin (web)**: 232px white sidebar (Dashboard, Products, Stores, Reservations, Users, Analytics; active = #F2F1EE bg) → header with date + "Add store" (outline) / "Add product" (primary) → 4 stat tiles (mono numbers) → 2/3 "Recent reservations" table with status badges (Awaiting pickup = primary_container, Collected = success_container, Expired = #F2F1EE) + 1/3 "Expiring soon" list (warning color when under ~4 h).
