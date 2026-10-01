# QLess Firestore Security Rules

The source of truth is [`firestore.rules`](firestore.rules) (Storage:
[`storage.rules`](storage.rules)). Deploy from the repo root:

```bash
firebase deploy --only firestore:rules,storage
```

Test locally against the emulators with:

```bash
firebase emulators:start --only firestore,storage
```

## Admin accounts

Admin access comes from the `admin` custom claim, not a Firestore field,
so users can't grant it to themselves. Grant or revoke it from
`functions/`:

```bash
gcloud auth application-default login   # once
node scripts/setAdmin.js admin@qless.com
node scripts/setAdmin.js admin@qless.com --revoke
```

## Rules summary

| Collection | Read | Create | Update | Delete |
|------------|------|--------|--------|--------|
| users | Own, admin | Own (no `noShowCount`) | Own (no `noShowCount`) | Own |
| products | Public | Admin | Admin | Admin |
| stores | Public | Admin | Admin | Admin |
| config | Public | Admin | Admin | Admin |
| reservations | Own, admin | Own | Own (can't change `userId`), admin | Own |
| orders | Own, admin | Own | Admin | Admin |
| user_events | Admin | Own | none | none |
| carts | Own | Own | Own | Own |
| favorites | Own | Own | Own | Own |
| notifications | Own | Admin | Own | Own |
| Storage `products/*` | Public | Admin | Admin | Admin |

## Testing

```bash
cd functions && npm run test:rules
```
