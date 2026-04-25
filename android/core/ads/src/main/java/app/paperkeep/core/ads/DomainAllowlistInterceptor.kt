package app.paperkeep.core.ads

/**
 * Domain allowlist enforcer for all outbound network requests (§6.7).
 *
 * Only these Google-owned domains are permitted:
 *   *.doubleclick.net, *.google.com, *.googleapis.com,
 *   *.googleusercontent.com, *.gstatic.com
 *
 * Usage: wrap any OkHttpClient.Builder that a Google SDK exposes with
 * [DomainAllowlistInterceptor] as a network interceptor. Any request to a
 * host outside the allowlist is rejected with an [IllegalStateException]
 * before the TCP connection is opened.
 *
 * This class has no OkHttp compile dependency so it can live in :core:ads
 * without adding a new network library. The actual OkHttp interceptor wiring
 * (Phase 5, when the real AdMob SDK is included) will call [isAllowed] to
 * gate each request.
 */
object DomainAllowlistInterceptor {

    /** Domains (suffix-matched) that Paperkeep is permitted to reach. */
    val ALLOWED_DOMAINS: Set<String> = setOf(
        ".doubleclick.net",
        ".google.com",
        ".googleapis.com",
        ".googleusercontent.com",
        ".gstatic.com",
    )

    /**
     * Returns `true` if [host] is within the allowed domain list.
     *
     * A host is allowed if it equals one of the allowed domains (without leading
     * dot) or if it ends with one of them (subdomain match).
     */
    fun isAllowed(host: String): Boolean {
        val lower = host.lowercase()
        return ALLOWED_DOMAINS.any { domain ->
            lower == domain.trimStart('.') || lower.endsWith(domain)
        }
    }

    /**
     * Assert that [host] is allowed, or throw [BlockedHostException].
     * Call this from an OkHttp interceptor's `intercept()` method.
     */
    fun checkOrThrow(host: String) {
        if (!isAllowed(host)) {
            throw BlockedHostException(
                "Network request to '$host' blocked — not in domain allowlist. " +
                "Paperkeep only permits: $ALLOWED_DOMAINS",
            )
        }
    }
}

/** Thrown when a network request targets a host outside the allowlist. */
class BlockedHostException(message: String) : IllegalStateException(message)
