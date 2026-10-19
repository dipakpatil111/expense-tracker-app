package com.example.expensetracker

import java.security.MessageDigest

data class ParsedSms(val amount: Double, val type: String, val category: String, val description: String)

/**
 * FIX: the old parser accepted any SMS containing words like "sent"/"paid"/"added" (OTPs, promos,
 * "credit limit Rs 50,000"...). This one skips OTP/offer/limit/failed messages, needs bank/UPI context,
 * ignores "Avl Bal" amounts, and decides debit/credit by which keyword appears first.
 */
object SmsParser {
    private val amountRx = Regex("""(?i)(?:inr|rs\.?|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val debitWords = listOf("debited", "spent", "withdrawn", "paid to", "paid ", "sent ", "purchase of", "payment of", "deducted", "transferred to")
    private val creditWords = listOf("credited", "received", "deposited", "refund", "cashback")
    private val skipWords = listOf(
        "otp", "one time password", "verification code", "will be debited", "will be credited", "request",
        "is due", "due on", "minimum due", "min due", "credit limit", "available limit", "limit of",
        "offer", "congratulations", "pre-approved", "apply now", "reward points", "failed", "declined",
        "unsuccessful", "reversed"
    )
    private val contextWords = listOf(
        "a/c", "acct", "account", "upi", "card", "bank", "vpa", "imps", "neft", "rtgs", "atm",
        "wallet", "txn", "ref no", "ref.", "paytm", "phonepe", "gpay", "google pay", "bhim"
    )
    private val merchantRx = Regex("""(?i)\b(?:to|at|towards|from)\s+(?:vpa\s+)?([A-Za-z0-9][A-Za-z0-9@._& ]{2,28})""")
    private val merchantCut = Regex("""(?i)\s+(on|ref|txn|upi|via|using|avl|bal|date|from|for|is|has)\b.*$""")

    fun parse(body: String): ParsedSms? {
        val lower = body.lowercase()
        if (skipWords.any { lower.contains(it) }) return null
        if (contextWords.none { lower.contains(it) }) return null

        val dIdx = debitWords.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()
        val cIdx = creditWords.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()
        if (dIdx == null && cIdx == null) return null
        val isDebit = if (dIdx != null && cIdx != null) dIdx < cIdx else dIdx != null

        val amount = amountRx.findAll(body).firstNotNullOfOrNull { m ->
            val pre = lower.substring((m.range.first - 20).coerceAtLeast(0), m.range.first)
            if (listOf("bal", "limit", "avl", "available").any { pre.contains(it) }) null
            else m.groupValues[1].replace(",", "").toDoubleOrNull()
        } ?: return null
        if (amount <= 0.0) return null

        val category = if (!isDebit) "Other" else when {
            listOf("hpcl", "bpcl", "iocl", "indian oil", "fuel", "petrol", "diesel").any { lower.contains(it) } -> "Petrol"
            listOf("swiggy", "zomato", "restaurant", "cafe", "tea", "hotel", "dominos", "kfc").any { lower.contains(it) } -> "Food"
            listOf("recharge", "airtel", "jio", "vodafone", "broadband", "electric", "bescom", "gas", "dth").any { lower.contains(it) } -> "Bills"
            lower.contains("rapido") -> RAPIDO_RECHARGE
            listOf("uber", "ola ").any { lower.contains(it) } -> "Rides"
            listOf("amazon", "flipkart", "myntra", "meesho", "ajio").any { lower.contains(it) } -> "Shopping"
            listOf("pharmacy", "medplus", "apollo", "hospital", "clinic", "1mg").any { lower.contains(it) } -> "Health"
            else -> "Other"
        }

        val merchant = merchantRx.findAll(body).map { it.groupValues[1].trim() }
            .firstOrNull { m ->
                val l = m.lowercase()
                listOf("your", "a/c", "ac ", "account", "bank").none { l.startsWith(it) }
            }
            ?.replace(merchantCut, "")?.trim()?.takeIf { it.length >= 3 }

        val label = if (isDebit) "Paid" else "Received"
        val desc = if (merchant != null) "$label · $merchant" else body.trim().take(60)
        return ParsedSms(amount, if (isDebit) "DEBIT" else "CREDIT", category, desc)
    }

    fun hash(sender: String, body: String, ts: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest("$sender|${body.trim()}|${ts / 1000}".toByteArray()).joinToString("") { "%02x".format(it) }.take(40)
    }
}
