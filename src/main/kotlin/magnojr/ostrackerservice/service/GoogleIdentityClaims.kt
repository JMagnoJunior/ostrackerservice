package magnojr.ostrackerservice.service

data class GoogleIdentityClaims(
    val sub: String,
    val email: String,
    val emailVerified: Boolean,
    val name: String?,
)
