package ministra.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Håller sidorna utanför sökmotorerna.
 *
 * <p>Församlingens listor ska inte gå att googla. Headern sätts på allt, inte bara på
 * förfrågnings-URL:erna, eftersom ingenting i appen är tänkt att indexeras.
 */
@Component
public class NoIndexFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
        chain.doFilter(request, response);
    }
}
