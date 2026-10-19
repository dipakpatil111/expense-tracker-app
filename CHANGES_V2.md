# Expense Tracker v2

## Setup (zaroori - pehle ye karo)
1. Firebase Console -> Authentication -> Sign-in method -> **Email/Password enable** karo.
2. Purana cloud data chahiye to: app me More -> Cloud backup -> Sign in -> **Import old cloud data**
   (ye tabhi chalega jab tak purane open rules active hain).
3. Uske baad Firestore -> Rules me `firestore.rules` ka content paste karke **Publish** karo.
4. Build: GitHub Actions (same workflow) ya Android Studio.

## Bugs fixed
1. transactions.timestamp unique tha -> ab har row ka apna `uid` (cloud doc id bhi wahi).
2. destructive migration -> proper Migration 3->4 (data safe).
3. Firebase open tha -> Email login + `users/{uid}/...` + locked rules.
4. Delete sync -> soft delete (`isDeleted`) + last-write-wins (`updatedAt`); loan delete UI added.
5. Restore duplicates -> upsert by uid, legacy import me duplicate check.
6. SMS parser: OTP/promo/limit/failed skip, "Avl Bal" amount ignore, duplicate SMS block, multi-part SMS join.
7. PDF multi-page + header har page par.
8. Budget limit ka UI (monthly + category).
9. Budget alert: sirf 80% / 100% par, ek baar.
10. Code split: 20+ files + ViewModel.
11. Accrued/pending interest (principal repayment ke hisaab se).
12. Extra: date-range picker ka UTC bug (start din ki subah ki entries miss hoti thi) fixed.
13. Extra: ride "net after petrol" ab saari rides - petrol (pehle filtered earnings - poora petrol).

## New features
Budgets, search + filters, recurring entries (daily worker), loan interest reminders, loan statement share,
JSON backup/restore, CSV export, app lock (phone PIN/fingerprint), custom categories, light/dark theme,
analytics (month compare, daily chart, donut, savings rate, ride insights), undo delete, backdated entries.

## Not included
Voice entry, receipt photos, home widget, multiple wallets / credit-card tracking, Hindi UI, compound interest.

## Rapido net (added)
New categories "Rapido Recharge" and "Rapido Subscription". Home and Analytics show a "Rapido net" card:
Rapido earnings - recharge - subscription (- any other debit with mode RAPIDO). Petrol is NOT subtracted.
Old recharge entries saved under another category will not count until you edit their category.
