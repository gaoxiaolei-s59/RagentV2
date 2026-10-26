package org.puregxl.ragent.infra;

import org.junit.jupiter.api.Test;
import org.puregxl.ragent.infra.model.ProviderClientRegistry;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ProviderClientRegistryTest {
    interface Client { String provider(); }
    record Builtin(String provider) implements Client {}
    record Custom(String provider) implements Client {}

    @Test void customImplementationWinsInBothRegistrationOrders() {
        Client builtin = new Builtin("test");
        Client custom = new Custom("test");
        assertThat(ProviderClientRegistry.index(List.of(builtin, custom), Client::provider, Builtin.class).get("test"))
            .isSameAs(custom);
        assertThat(ProviderClientRegistry.index(List.of(custom, builtin), Client::provider, Builtin.class).get("test"))
            .isSameAs(custom);
    }

    @Test void ambiguousCustomImplementationsAreRejected() {
        assertThatThrownBy(() -> ProviderClientRegistry.index(
            List.of(new Custom("test"), new Custom("test")), Client::provider, Builtin.class))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("test");
    }

    @Test void blankProviderIsRejected() {
        assertThatThrownBy(() -> ProviderClientRegistry.index(List.of(new Custom(" ")), Client::provider))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
