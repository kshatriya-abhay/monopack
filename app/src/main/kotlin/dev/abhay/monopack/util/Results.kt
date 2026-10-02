package dev.abhay.monopack.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but lets coroutine cancellation through. `runCatching` around a suspend
 * call turns a cancellation into a failure, so cancelled work carries on with defaults (that
 * flashed onboarding once). Use this whenever [block] suspends.
 */
inline fun <T> catching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
