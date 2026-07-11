package com.redcoal.redmedia.mobile.download

private val obfuscatedClassName = Regex("^[a-z][0-9]+(?:\\.[a-z][0-9]*)+$")

fun userFacingError(error: Throwable, fallback: String): String {
    val message = generateSequence(error) { it.cause }
        .mapNotNull { cause ->
            cause.message
                ?.lineSequence()
                ?.map(String::trim)
                ?.lastOrNull(String::isNotEmpty)
        }
        .firstOrNull { !obfuscatedClassName.matches(it) }

    return (message ?: if (error.message?.trim()?.matches(obfuscatedClassName) == true) {
        "The media engine could not start. Reinstall or update the app and try again."
    } else {
        fallback
    }).take(260)
}
