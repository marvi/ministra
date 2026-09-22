package ministra.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class ErrorPageResolverTest {

    private final ErrorPageResolver resolver = new ErrorPageResolver();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    void resolves_not_found_to_404_page() {
        var view = resolver.resolveErrorView(request, HttpStatus.NOT_FOUND, Map.of());

        assertThat(view).isNotNull();
        assertThat(view.getViewName()).isEqualTo("error/404");
        assertThat(view.getModel()).isEmpty();
    }

    @Test
    void resolves_server_errors_to_500_page() {
        var view = resolver.resolveErrorView(request, HttpStatus.INTERNAL_SERVER_ERROR, Map.of());

        assertThat(view).isNotNull();
        assertThat(view.getViewName()).isEqualTo("error/500");
        assertThat(view.getModel()).isEmpty();
    }

    @Test
    void leaves_other_errors_to_spring_boot() {
        assertThat(resolver.resolveErrorView(request, HttpStatus.BAD_REQUEST, Map.of())).isNull();
    }
}
