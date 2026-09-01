package com.sysadmindoc.billminder.data

import android.net.Uri

/**
 * The payment link a bill is allowed to open.
 *
 * A bill's payment URL is typed by hand, mapped in from a CSV, or restored from a backup file, so by
 * the time anything launches it the text is not necessarily something the user chose. Handing an
 * arbitrary string to `ACTION_VIEW` lets any installed app claim whatever scheme it contains, which
 * turns a bill record into a way to start another app's intent. Only web addresses are accepted, and
 * the host is published separately so the destination can be shown before leaving the app.
 */
object PaymentLink {

    private val ALLOWED_SCHEMES = setOf("http", "https")

    /**
     * The address to open, or null when the stored text is not a web address this app will launch.
     *
     * A bare `example.com/pay` is treated as `https://example.com/pay`, because that is what someone
     * typing a payment site means and rejecting it would only push them to paste a scheme.
     */
    fun parse(raw: String): Uri? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        // Anything with a scheme keeps it, so a hostile "javascript:" or "intent:" is judged as
        // written rather than being rescued by the https default below.
        val candidate = if (trimmed.contains("://") || trimmed.substringBefore('/').contains(':')) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in ALLOWED_SCHEMES) return null
        if (uri.host.isNullOrBlank()) return null
        return uri
    }

    /** True when [raw] is blank or a link the app would open; false only for a rejected address. */
    fun isAcceptable(raw: String): Boolean = raw.isBlank() || parse(raw) != null

    /** The host to show before leaving the app, or null when there is no link to open. */
    fun host(raw: String): String? = parse(raw)?.host
}
