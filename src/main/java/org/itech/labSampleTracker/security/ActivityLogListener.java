package org.itech.labSampleTracker.security;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManagerFactory;

/**
 * Capte toute création, modification et suppression d'entité faite par JPA
 * (formulaires web, synchronisation mobile, synchronisation OpenELIS, suppressions
 * de périmètre) et la transmet au journal d'activité. Toutes les écritures de
 * l'application passent par JPA : aucune n'échappe au journal.
 */
@Component
public class ActivityLogListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

	private static final long serialVersionUID = 1L;

	private final transient ActivityLogService activityLog;

	public ActivityLogListener(ActivityLogService activityLog, EntityManagerFactory emf) {
		this.activityLog = activityLog;
		EventListenerRegistry registry = emf.unwrap(SessionFactoryImplementor.class).getServiceRegistry()
				.getService(EventListenerRegistry.class);
		registry.appendListeners(EventType.POST_INSERT, this);
		registry.appendListeners(EventType.POST_UPDATE, this);
		registry.appendListeners(EventType.POST_DELETE, this);
	}

	private static String type(EntityPersister persister) {
		String name = persister.getEntityName();
		return name.substring(name.lastIndexOf('.') + 1);
	}

	@Override
	public void onPostInsert(PostInsertEvent event) {
		EntityPersister p = event.getPersister();
		activityLog.add(ActivityLogService.CREATE, type(p), event.getId(),
				ActivityLogService.snapshot(p.getPropertyNames(), event.getState(), true));
	}

	@Override
	public void onPostUpdate(PostUpdateEvent event) {
		EntityPersister p = event.getPersister();
		activityLog.add(ActivityLogService.UPDATE, type(p), event.getId(),
				ActivityLogService.diff(p.getPropertyNames(), event.getOldState(), event.getState()));
	}

	@Override
	public void onPostDelete(PostDeleteEvent event) {
		EntityPersister p = event.getPersister();
		activityLog.add(ActivityLogService.DELETE, type(p), event.getId(),
				ActivityLogService.snapshot(p.getPropertyNames(), event.getDeletedState(), false));
	}

	@Override
	public boolean requiresPostCommitHandling(EntityPersister persister) {
		return false; // la validation est suivie par ActivityLogService (synchronisation de transaction)
	}
}
