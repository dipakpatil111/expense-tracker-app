package com.example.expensetracker

import java.security.MessageDigest

data class ParsedSms(val amount: Double, val type: String, val category: String, val description: String)

/**
 * Rules (in this order):
 * 1. Promotional senders (TRAI header ending in "-P", e.g. TX-KOTAKB-P) are ignored completely.
 * 2. Links are removed, then OTP / offer / cashback-offer / limit / failed / "T&C" style messages are ignored.
 * 3. A real transaction SMS must carry proof: a masked account/card number (A/c XX1234, Card XX5678)
 *    or a payment reference word (UPI, VPA, IMPS, NEFT, RTGS, UTR, Ref No).
 * 4. Debit/credit is decided by which keyword appears first; "Avl Bal / limit" amounts are skipped.
 * Words like "cashback", "bank", "card" alone are NOT enough any more.
 */
object SmsParser {
    private val urlRx = Regex("""https?://\S+""")
    private val amountRx = Regex("""(?i)(?:inr|rs\.?|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val debitWords = listOf(
        "debited", "debit", "spent", "withdrawn", "withdrawal", "paid to", "paid ", "sent ",
        "purchase of", "payment of", "deducted", "transferred to", "txn of", "transaction of"
    )
    private val creditWords = listOf("credited", "received", "deposited")
    private val skipWords = listOf(
        "otp", "one time password", "verification code", "will be debited", "will be credited", "request",
        "is due", "due on", "minimum due", "min due", "credit limit", "available limit", "limit of",
        "offer", "congratulations", "pre-approved", "apply now", "reward points", "failed", "declined",
        "unsuccessful", "reversed", "cashback on", "flat rs", "enrol", "festive", "t&c", "eligible", "win ", "loan starting"
    )
    private val accountRx = Regex("""(?i)\b(?:a/c|acct|account|ac|card)[\s.:]*(?:no\.?|number|ending|with)?[\s.:]*(?:[x*]+\s*)?\d{3,}""")
    private val refWords = listOf("upi", "vpa", "imps", "neft", "rtgs", "utr", "ref no", "ref.", "ref:", "txn id", "txn no")
    // Bank of Baroda style: "Rs.19.00 Dr. from A/C XXXXXX4523 and Cr. to abc@upi" (Dr. = debit, Cr. = credit)
    private val drRx = Regex("""(?i)\bdr\b\.?\s*(?:from|to|a/c|ac)\b""")
    private val crRx = Regex("""(?i)\bcr\b\.?\s*(?:to|from|a/c|ac)\b""")
    private val merchantRx = Regex("""(?i)\b(?:to|at|towards|from)\s+(?:vpa\s+)?([A-Za-z0-9][A-Za-z0-9@._& -]{2,60})""")
    private val partyRx = Regex("""(?i)\s(?:to|from|towards|at)\s""")
    private val merchantCut = Regex("""(?i)\s+(on|ref|txn|upi|via|using|avl|bal|date|from|for|is|has)\b.*$""")

    fun parse(rawBody: String, sender: String = ""): ParsedSms? {
        if (sender.trim().uppercase().endsWith("-P")) return null        // promotional sender
        val body = rawBody.replace(urlRx, " ")
        val lower = body.lowercase()
        if (skipWords.any { lower.contains(it) }) return null

        val hasProof = accountRx.containsMatchIn(body) || refWords.any { lower.contains(it) }
        if (!hasProof) return null

        val dIdx = (debitWords.map { lower.indexOf(it) } + listOf(drRx.find(lower)?.range?.first ?: -1)).filter { it >= 0 }.minOrNull()
        val cIdx = (creditWords.map { lower.indexOf(it) } + listOf(crRx.find(lower)?.range?.first ?: -1)).filter { it >= 0 }.minOrNull()
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

        // look for the other party: drop your own masked account text first, then take what follows the last "to" / "from"
        val merchant = merchantRx.findAll(accountRx.replace(body, " "))
            .map { it.groupValues[1].trim().split(partyRx).last().trim() }
            .firstOrNull { m ->
                val l = m.lowercase()
                listOf("your", "a/c", "ac ", "account", "bank").none { l.startsWith(it) }
            }
            ?.replace(merchantCut, "")?.trim()?.trimEnd('.', ' ', '-')?.takeIf { it.length >= 3 }

        val label = if (isDebit) "Paid" else "Received"
        val desc = if (merchant != null) "$label · $merchant" else body.trim().take(60)
        return ParsedSms(amount, if (isDebit) "DEBIT" else "CREDIT", category, desc)
    }

    fun hash(sender: String, body: String, ts: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest("$sender|${body.trim()}|${ts / 1000}".toByteArray()).joinToString("") { "%02x".format(it) }.take(40)
    }
}
