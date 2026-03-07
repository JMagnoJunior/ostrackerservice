package magnojr.ostrackerservice.service

data class OAuthAuthenticationResult(
    val token: String,
    val status: String,
    val isPending: Boolean,
)
