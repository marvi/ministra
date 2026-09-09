package ministra.poll;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TokensTest {

    @Test
    void is_128_bits_of_base64url_without_padding() {
        var token = Tokens.generate();

        // 16 byte blir 22 tecken base64 utan padding.
        assertThat(token).hasSize(22);
        assertThat(token).matches("[A-Za-z0-9_-]{22}");
        assertThat(token).doesNotContain("=");
    }

    @Test
    void does_not_repeat() {
        var seen = new HashSet<String>();
        IntStream.range(0, 10_000).forEach(unused -> seen.add(Tokens.generate()));

        assertThat(seen).hasSize(10_000);
    }
}
