package ministra.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

/** Väljer Ministras egna felsidor utan att skicka tekniska feldetaljer till vyn. */
@Component
public class ErrorPageResolver implements ErrorViewResolver {

    @Override
    public @Nullable ModelAndView resolveErrorView(
            HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
        if (status == HttpStatus.NOT_FOUND) {
            return new ModelAndView("error/404");
        }
        if (status.is5xxServerError()) {
            return new ModelAndView("error/500");
        }
        return null;
    }
}
