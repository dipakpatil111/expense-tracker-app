package com.example.expensetracker

import org.json.JSONArray
import org.json.JSONObject

/** NEW: full JSON backup / restore (transactions, loans, payments, recurring rules). Restore merges by uid. */
object Backup {
    suspend fun toJson(db: AppDatabase): String {
        val root = JSONObject()
        root.put("app", "ExpenseTracker")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("transactions", JSONArray().also { arr ->
            db.transactionDao().getAllRaw().forEach { t ->
                arr.put(JSONObject().put("uid", t.uid).put("amount", t.amount).put("type", t.type).put("mode", t.mode)
                    .put("txCategory", t.txCategory).put("description", t.description).put("timestamp", t.timestamp)
                    .put("source", t.source).put("smsHash", t.smsHash ?: "").put("isDeleted", t.isDeleted).put("updatedAt", t.updatedAt))
            }
        })
        root.put("loans", JSONArray().also { arr ->
            db.loanDao().getAllLoansRaw().forEach { l ->
                arr.put(JSONObject().put("uid", l.uid).put("personName", l.personName).put("amount", l.amount)
                    .put("originalAmount", l.originalAmount).put("type", l.type).put("hasInterest", l.hasInterest)
                    .put("monthlyRate", l.monthlyRate).put("startDate", l.startDate).put("isSettled", l.isSettled)
                    .put("note", l.note).put("isDeleted", l.isDeleted).put("updatedAt", l.updatedAt))
            }
        })
        root.put("payments", JSONArray().also { arr ->
            db.loanDao().getAllPaymentsRaw().forEach { p ->
                arr.put(JSONObject().put("uid", p.uid).put("loanUid", p.loanUid).put("amount", p.amount)
                    .put("paymentType", p.paymentType).put("paymentMode", p.paymentMode).put("paymentDate", p.paymentDate)
                    .put("note", p.note).put("isDeleted", p.isDeleted).put("updatedAt", p.updatedAt))
            }
        })
        root.put("rules", JSONArray().also { arr ->
            db.recurringDao().getAll().forEach { r ->
                arr.put(JSONObject().put("uid", r.uid).put("title", r.title).put("amount", r.amount).put("type", r.type)
                    .put("mode", r.mode).put("txCategory", r.txCategory).put("dayOfMonth", r.dayOfMonth)
                    .put("isActive", r.isActive).put("lastRunMonth", r.lastRunMonth))
            }
        })
        return root.toString(2)
    }

    /** returns number of records added or updated */
    suspend fun restore(db: AppDatabase, json: String): Int {
        val root = JSONObject(json)
        if (root.optString("app") != "ExpenseTracker") throw IllegalArgumentException("Not an Expense Tracker backup file")
        var n = 0
        val txDao = db.transactionDao()
        val loanDao = db.loanDao()

        val txs = root.optJSONArray("transactions") ?: JSONArray()
        for (i in 0 until txs.length()) {
            val o = txs.getJSONObject(i)
            val cloud = Transaction(
                uid = o.getString("uid"), amount = o.getDouble("amount"), type = o.getString("type"), mode = o.getString("mode"),
                txCategory = o.getString("txCategory"), description = o.optString("description"), timestamp = o.getLong("timestamp"),
                source = o.optString("source", "MANUAL"), smsHash = o.optString("smsHash").takeIf { it.isNotEmpty() },
                isDeleted = o.optBoolean("isDeleted"), updatedAt = o.optLong("updatedAt", o.getLong("timestamp"))
            )
            val local = txDao.getByUid(cloud.uid)
            if (local == null) { txDao.insertIgnore(cloud); n++ }
            else if (cloud.updatedAt > local.updatedAt) { txDao.update(cloud.copy(id = local.id)); n++ }
        }

        val loans = root.optJSONArray("loans") ?: JSONArray()
        for (i in 0 until loans.length()) {
            val o = loans.getJSONObject(i)
            val cloud = LoanRecord(
                uid = o.getString("uid"), personName = o.getString("personName"), amount = o.getDouble("amount"),
                originalAmount = o.getDouble("originalAmount"), type = o.getString("type"), hasInterest = o.optBoolean("hasInterest"),
                monthlyRate = o.optDouble("monthlyRate", 0.0), startDate = o.getLong("startDate"), isSettled = o.optBoolean("isSettled"),
                note = o.optString("note"), isDeleted = o.optBoolean("isDeleted"), updatedAt = o.optLong("updatedAt", o.getLong("startDate"))
            )
            val local = loanDao.getLoanByUid(cloud.uid)
            if (local == null) { loanDao.insertLoan(cloud); n++ }
            else if (cloud.updatedAt > local.updatedAt) { loanDao.updateLoan(cloud.copy(id = local.id)); n++ }
        }

        val loanIdByUid = loanDao.getAllLoansRaw().associate { it.uid to it.id }
        val pays = root.optJSONArray("payments") ?: JSONArray()
        for (i in 0 until pays.length()) {
            val o = pays.getJSONObject(i)
            val loanId = loanIdByUid[o.optString("loanUid")] ?: continue
            val cloud = InterestPayment(
                uid = o.getString("uid"), loanId = loanId, loanUid = o.getString("loanUid"), amount = o.getDouble("amount"),
                paymentType = o.getString("paymentType"), paymentMode = o.optString("paymentMode", "ONLINE"),
                paymentDate = o.getLong("paymentDate"), note = o.optString("note"), isDeleted = o.optBoolean("isDeleted"),
                updatedAt = o.optLong("updatedAt", o.getLong("paymentDate"))
            )
            val local = loanDao.getPaymentByUid(cloud.uid)
            if (local == null) { loanDao.insertPayment(cloud); n++ }
            else if (cloud.updatedAt > local.updatedAt) { loanDao.updatePayment(cloud.copy(id = local.id)); n++ }
        }

        val existingRules = db.recurringDao().getAll().map { it.uid }.toHashSet()
        val rules = root.optJSONArray("rules") ?: JSONArray()
        for (i in 0 until rules.length()) {
            val o = rules.getJSONObject(i)
            if (existingRules.contains(o.getString("uid"))) continue
            db.recurringDao().insert(
                RecurringRule(
                    uid = o.getString("uid"), title = o.getString("title"), amount = o.getDouble("amount"), type = o.getString("type"),
                    mode = o.getString("mode"), txCategory = o.getString("txCategory"), dayOfMonth = o.getInt("dayOfMonth"),
                    isActive = o.optBoolean("isActive", true), lastRunMonth = o.optString("lastRunMonth")
                )
            ); n++
        }
        return n
    }
}
