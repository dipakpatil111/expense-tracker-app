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

## IMPORTANT when copying into your repo
Delete the old project's `app/src/main/java/com/example/expensetracker/` folder first (or let the new files overwrite).
The new files use the SAME names as old ones (Transaction.kt, LoanRecord.kt, TransactionDao.kt, LoanDao.kt),
so nothing stale stays behind. If an old Transaction.kt (without `isDeleted`) is left over, KSP fails with
"no such column: isDeleted".

## v3 - Opening balance, running balance, month statement, transfer
- More -> Opening balance: enter Cash in hand + Bank balance (and the date it is valid from).
- Home: "Total balance now" with Cash in hand and Bank/Online as RUNNING balances (not reset every month).
  Expense reduces, income adds. Uber/Rapido/Online entries go to bank, CASH entries go to cash.
- Home: "Balance statement" card for the selected month: Opening + Income - Expense +/- Transfer = Closing (cash, bank, total).
  Change the month with the arrows; a month's closing is automatically the next month's opening.
- Home: "Transfer cash / bank" (ATM withdraw / cash deposit). Not counted as income or expense.
  Tap a transfer in the list to delete it (both halves are removed).
- Transfers are excluded from income/expense totals, analytics, budgets and the Rapido card.
- Opening balance is stored on the phone (not in cloud sync / JSON backup).

## v3.1 - SMS fix
- Promotional senders (ending "-P", e.g. TX-KOTAKB-P) are ignored.
- "cashback" is no longer treated as credit; bank/card words alone are no longer proof of a transaction.
- A transaction SMS now needs a masked account/card number (A/c XX1234) or UPI/IMPS/NEFT/Ref words.
- Files changed: SmsParser.kt, SmsReceiver.kt

## v3.2 - Bank of Baroda "Dr./Cr." SMS
- "Rs.19.00 Dr. from A/C XXXXXX4523 and Cr. to abc@upi. Ref:..." is now recognised (Dr. = debit, Cr. = credit).
- Merchant name no longer includes your own account text. File changed: SmsParser.kt
