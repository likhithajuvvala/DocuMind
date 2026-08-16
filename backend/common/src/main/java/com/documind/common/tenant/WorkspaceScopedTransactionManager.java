package com.documind.common.tenant;

import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Enables the {@code workspaceFilter} Hibernate filter (declared on the entity package, see
 * {@code package-info.java} under {@code common.persistence.entity}) on every transaction's
 * Session as soon as it begins, scoped to whatever workspace {@link WorkspaceContext} currently
 * holds. This is what turns the filter from an opt-in convenience into an enforced boundary: every
 * query against a filtered entity within the transaction — including ones that never mention a
 * workspace, like an inherited {@code findById} — is scoped, because Hibernate rewrites the
 * generated SQL itself instead of relying on each call site to remember to add one.
 *
 * <p>doBegin is overridden rather than enabling the filter via a Spring AOP aspect around
 * {@code @Transactional} methods, because that would be a real ordering hazard: Spring Data JPA's
 * transactional advisor already runs at the lowest possible advisor precedence by default, so a
 * same-target aspect has no reliable way to guarantee it executes after the transaction (and
 * therefore the Session) actually exists. Overriding doBegin runs exactly once, exactly when the
 * EntityManager is bound to the thread, with no such ambiguity.
 *
 * <p>Requests with no bound workspace (registration, login, refresh — anything before a
 * workspace is known) simply get no filter enabled, which is correct: those flows only touch
 * unfiltered entities (users, workspaces, refresh tokens) in the first place.
 *
 * <p><b>This only protects calls made inside a transaction.</b> Spring Data JPA only auto-wraps
 * the base {@code CrudRepository} methods ({@code findById}, {@code save}, {@code delete}, ...)
 * in a transaction, via {@code SimpleJpaRepository}'s own class-level {@code @Transactional} — so
 * those are covered even when called bare. A custom derived-query method declared directly on a
 * repository interface (e.g. {@code findByDocumentId}) gets no transaction of its own; it only
 * runs inside this filter's protection when the caller already wraps it in a {@code @Transactional}
 * boundary — which every current call site in this codebase already does. A brand new call site
 * that queries a filtered entity by a custom method with no surrounding {@code @Transactional}
 * would bypass the filter, same as it would bypass any hand-written workspace check today.
 */
public class WorkspaceScopedTransactionManager extends JpaTransactionManager {

    private static final String FILTER_NAME = "workspaceFilter";
    private static final String FILTER_PARAMETER = "workspaceId";

    public WorkspaceScopedTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        WorkspaceContext.find().ifPresent(this::enableWorkspaceFilter);
    }

    private void enableWorkspaceFilter(UUID workspaceId) {
        Object resource = TransactionSynchronizationManager.getResource(getEntityManagerFactory());
        if (resource instanceof EntityManagerHolder holder) {
            holder.getEntityManager()
                    .unwrap(Session.class)
                    .enableFilter(FILTER_NAME)
                    .setParameter(FILTER_PARAMETER, workspaceId);
        }
    }
}
