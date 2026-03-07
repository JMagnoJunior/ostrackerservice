package magnojr.ostrackerservice.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import magnojr.ostrackerservice.model.UserStatus
import magnojr.ostrackerservice.service.JwtService
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.Locale

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.substring(7)
            try {
                val claims = jwtService.parsePrincipal(token)
                val isPending = claims.status == UserStatus.PENDENTE_APROVACAO.name
                val authorities =
                    if (isPending) {
                        listOf(SimpleGrantedAuthority("ROLE_PENDENTE"))
                    } else {
                        listOf(SimpleGrantedAuthority("ROLE_${claims.role.uppercase(Locale.ROOT)}"))
                    }
                val auth = UsernamePasswordAuthenticationToken(claims, null, authorities)
                SecurityContextHolder.getContext().authentication = auth
            } catch (_: Exception) {
                SecurityContextHolder.clearContext()
            }
        }
        filterChain.doFilter(request, response)
    }
}
