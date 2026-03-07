package magnojr.ostrackerservice.service

class GoogleTokenVerificationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
