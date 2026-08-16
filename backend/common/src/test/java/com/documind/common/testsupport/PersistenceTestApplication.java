package com.documind.common.testsupport;

import com.documind.common.config.PersistenceConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Minimal Spring Boot app used only to bootstrap persistence-layer integration tests in this
 * module — {@code common} itself is a library, not a deployable service. Deliberately lives in its
 * own package with nothing else in it and imports {@link PersistenceConfiguration} directly rather
 * than component-scanning the whole {@code com.documind.common} tree, so it doesn't drag in
 * unrelated beans (JWT, object storage, etc.) that need configuration this test has no reason to
 * provide.
 */
@SpringBootApplication
@Import(PersistenceConfiguration.class)
public class PersistenceTestApplication {
}
