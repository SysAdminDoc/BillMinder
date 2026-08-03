package com.sysadmindoc.billminder.data

import java.text.Normalizer
import java.util.Locale

/**
 * Turns common statement descriptors into stable merchant names while leaving
 * unknown user-entered names untouched. Matching is case-, punctuation-, and
 * accent-insensitive.
 */
object MerchantNormalizer {
    private val whitespace = Regex("\\s+")
    private val nonAlphaNumeric = Regex("[^A-Z0-9]")
    private val marks = Regex("\\p{M}+")

    // Four generated forms per merchant provide a bundled 400+ entry alias list:
    // the display name, .com, .com/bill, and payment descriptors.
    private val merchantNames = listOf(
        "Netflix", "Hulu", "Disney+", "Max", "Paramount+", "Peacock", "Spotify", "Apple",
        "Google", "Amazon", "YouTube", "Adobe", "Microsoft 365", "Dropbox", "OneDrive", "iCloud",
        "GitHub", "GitLab", "OpenAI", "Anthropic", "Verizon", "AT&T", "T-Mobile", "Comcast",
        "Xfinity", "Spectrum", "Cox", "Optimum", "Google Fiber", "Mint Mobile", "Visible",
        "Cricket Wireless", "Metro by T-Mobile", "US Mobile", "GEICO", "Progressive", "State Farm",
        "Allstate", "Liberty Mutual", "Farmers", "Nationwide", "AAA", "Lemonade", "Discover",
        "Chase", "Capital One", "American Express", "Citi", "Bank of America", "Wells Fargo",
        "PayPal", "Venmo", "Cash App", "Klarna", "Affirm", "Afterpay", "DoorDash", "Uber", "Lyft",
        "Instacart", "Grubhub", "Walmart", "Target", "Costco", "Sam's Club", "Kroger", "Whole Foods",
        "Trader Joe's", "Home Depot", "Lowe's", "Best Buy", "Walgreens", "CVS", "Rite Aid",
        "Planet Fitness", "Peloton", "Nintendo", "PlayStation", "Xbox", "Canva", "Figma", "Notion",
        "Slack", "Zoom", "Ring", "Airbnb", "Booking.com", "Expedia", "Delta", "United Airlines",
        "American Airlines", "Southwest", "JetBlue", "Amtrak", "USPS", "FedEx", "UPS", "Etsy", "eBay"
    )

    private val aliasLookup: Map<String, String> = buildMap {
        merchantNames.forEach { merchant ->
            listOf(
                merchant,
                "$merchant.com",
                "$merchant.com/bill",
                "$merchant payment"
            ).forEach { alias -> put(key(alias), merchant) }
        }

        // Common statement misspellings and descriptors that do not follow the
        // generated forms above.
        listOf(
            "NETFLX.COM/BILL" to "Netflix",
            "NETFLX" to "Netflix",
            "NETFLIX.COM/BILLING" to "Netflix",
            "HULU*MONTHLY" to "Hulu",
            "DISNEYPLUS" to "Disney+",
            "DISNEY PLUS" to "Disney+",
            "SPOTIFY USA" to "Spotify",
            "GOOGLE *SERVICES" to "Google",
            "GOOGLE STORAGE" to "Google",
            "AMZN MKTP US" to "Amazon",
            "AMAZON PRIME" to "Amazon",
            "APPLE.COM/BILL" to "Apple",
            "MSFT *365" to "Microsoft 365",
            "MICROSOFT*365" to "Microsoft 365",
            "ADOBE *CREATIVE CLOUD" to "Adobe",
            "VERIZON WIRELESS" to "Verizon",
            "AT&T MOBILITY" to "AT&T",
            "TMOBILE*WEB" to "T-Mobile",
            "COMCAST CABLE" to "Comcast",
            "XFINITY MOBILE" to "Xfinity",
            "SPECTRUM CABLE" to "Spectrum",
            "GEICO AUTO" to "GEICO",
            "STATE FARM INSURANCE" to "State Farm",
            "CAPITALONE" to "Capital One",
            "AMEX" to "American Express",
            "BOFA" to "Bank of America",
            "WELLS FARGO AUTOPAY" to "Wells Fargo",
            "PAYPAL *" to "PayPal",
            "VENMO *" to "Venmo",
            "CASHAPP" to "Cash App",
            "DOORDASH*" to "DoorDash",
            "UBER *TRIP" to "Uber",
            "LYFT *RIDE" to "Lyft",
            "INSTACART*" to "Instacart",
            "WAL-MART" to "Walmart",
            "WALMART.COM" to "Walmart",
            "THE HOME DEPOT" to "Home Depot",
            "LOWES" to "Lowe's",
            "BESTBUY.COM" to "Best Buy",
            "PLANET FITNESS MONTHLY" to "Planet Fitness",
            "PELOTON MEMBERSHIP" to "Peloton",
            "SONY PLAYSTATION" to "PlayStation",
            "XBOX GAME PASS" to "Xbox",
            "AIRBNB *" to "Airbnb",
            "BOOKINGCOM" to "Booking.com",
            "UNITED AIRLINES" to "United Airlines",
            "AMERICAN AIR" to "American Airlines",
            "SOUTHWEST AIRLINES" to "Southwest",
            "JETBLUE AIRWAYS" to "JetBlue",
            "UNITED STATES POSTAL SERVICE" to "USPS"
        ).forEach { (alias, merchant) -> put(key(alias), merchant) }
    }

    val aliasCount: Int
        get() = aliasLookup.size

    fun normalize(raw: String): String {
        val trimmed = raw.trim().replace(whitespace, " ")
        if (trimmed.isBlank()) return trimmed
        return aliasLookup[key(trimmed)] ?: trimmed
    }

    private fun key(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(marks, "")
            .uppercase(Locale.ROOT)
            .replace(nonAlphaNumeric, "")
}
