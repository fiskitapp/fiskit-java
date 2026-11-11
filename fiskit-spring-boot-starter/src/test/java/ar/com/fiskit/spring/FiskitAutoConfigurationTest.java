package ar.com.fiskit.spring;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.fiskit.Fiskit;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FiskitAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FiskitAutoConfiguration.class));

    @Test
    void createsTheClientFromTheProperties() {
        runner.withPropertyValues("fiskit.api-key=sk_test_x", "fiskit.timeout=30s").run(context -> {
            assertThat(context).hasSingleBean(Fiskit.class);
            assertThat(context.getBean(FiskitProperties.class).timeout()).isEqualTo(Duration.ofSeconds(30));
        });
    }

    @Test
    void anApplicationClientWins() {
        Fiskit own = Fiskit.builder().apiKey("sk_test_own").build();
        runner.withBean(Fiskit.class, () -> own)
                .run(context -> assertThat(context.getBean(Fiskit.class)).isSameAs(own));
    }

    @Test
    void withoutAKeyTheContextFailsWithAClearMessage() {
        if (System.getenv("FISKIT_API_KEY") != null) {
            return;
        }
        runner.run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("FISKIT_API_KEY"));
    }
}
