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

## Rules Summary:

| Collection | Read | Create | Update | Delete |
|------------|------|--------|--------|--------|
| users | Own only | Own only | Own only | Own only |
| products | Public | Auth | Auth | Auth |
| stores | Public | Auth | Auth | Auth |
| reservations | Own + Admin | Own | Own | Own |
| orders | Own + Admin | Own | Own + Admin | Admin |
| user_events | Auth | Auth | Auth | Auth |
| carts | Own only | Own only | Own only | Own only |
| favorites | Own only | Own only | Own only | Own only |
| notifications | Own only | Auth | Own only | Own only |
| config | Public | Auth | Auth | Auth |

