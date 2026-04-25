package com.cinetrack.audit;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.Interceptor;
import org.hibernate.type.Type;

import java.util.Arrays;

/**
 * Hibernate {@link Interceptor} that logs every dirty-entity flush without
 * altering state.
 *
 * <h3>How it works</h3>
 * <p>Hibernate calls {@link #onFlushDirty} for each entity that has changed
 * during a session flush.  The interceptor receives both the
 * <em>previous</em> and <em>current</em> state arrays, indexed by
 * {@code propertyNames}, which makes it straightforward to record exactly
 * which fields changed — useful for change-data-capture or coarse audit logs
 * without the overhead of Envers.</p>
 *
 * <p>Returning {@code false} signals that the interceptor did not modify
 * {@code currentState}; returning {@code true} would tell Hibernate to
 * re-read the modified array.</p>
 *
 * <h3>Registration</h3>
 * <p>Registered as a <em>factory-scoped</em> interceptor via
 * {@link com.cinetrack.config.HibernateInterceptorConfig} so it applies to
 * every session opened by the {@code SessionFactory}.</p>
 */
@Slf4j
public class AuditInterceptor implements Interceptor {

    @Override
    public boolean onFlushDirty(Object entity,
                                Object id,
                                Object[] currentState,
                                Object[] previousState,
                                String[] propertyNames,
                                Type[] types) {
        log.info("[AUDIT] Entity {} id={} modified", entity.getClass().getSimpleName(), id);

        if (previousState != null) {
            for (int i = 0; i < propertyNames.length; i++) {
                Object prev = previousState[i];
                Object curr = currentState[i];
                if (!java.util.Objects.deepEquals(prev, curr)) {
                    log.info("[AUDIT]   field='{}' old='{}' new='{}'",
                            propertyNames[i], prev, curr);
                }
            }
        }

        return false; // we did not modify currentState
    }

    @Override
    public boolean onSave(Object entity,
                          Object id,
                          Object[] state,
                          String[] propertyNames,
                          Type[] types) {
        log.info("[AUDIT] Entity {} id={} created with state={}",
                entity.getClass().getSimpleName(), id, Arrays.toString(state));
        return false;
    }

    @Override
    public void onDelete(Object entity,
                         Object id,
                         Object[] state,
                         String[] propertyNames,
                         Type[] types) {
        log.info("[AUDIT] Entity {} id={} deleted", entity.getClass().getSimpleName(), id);
    }
}
