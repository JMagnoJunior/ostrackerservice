package magnojr.ostrackerservice.service

data class AuthPrincipalClaims(
    val userId: String,
    val email: String,
    val role: String,
    val status: String,
)
