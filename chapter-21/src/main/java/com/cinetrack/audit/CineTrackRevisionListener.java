package com.cinetrack.audit;

import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Populates the custom fields on {@link CineTrackRevision} at the moment
 * Envers creates a new revision (i.e. just before the transaction commits).
 *
 * <p>Envers instantiates this listener directly (not via Spring), so it must
 * not declare Spring-managed constructor dependencies. It reads the Security
 * context and the current HTTP request via static holders instead.
 */
public class CineTrackRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        CineTrackRevision revision = (CineTrackRevision) revisionEntity;

        // Resolve the authenticated username
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            revision.setUsername(auth.getName());
        } else {
            revision.setUsername("system");
        }

        // Resolve the remote IP (only available in web request scope)
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                revision.setIpAddress(attrs.getRequest().getRemoteAddr());
            }
        } catch (Exception ignored) {
            // Non-web context: leave ipAddress null
        }
    }
}
