package com.primeforce.grouphandler.events;

import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;
import javax.jcr.observation.ObservationManager;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.commons.jcr.JcrConstants;

/**
 * Updates ldap groups in /home/groups/ with given e.g. '_ldap' suffix.
 * Config : /apps/aemgrouphandler/config/
 * 
 * @author akos
 *
 */
@Component(immediate = true)
@Service
public class GroupEventHandler implements EventListener {

	private static final Logger log = LoggerFactory.getLogger(GroupEventHandler.class);

	private static final String PER = "/";

	private static final String GROUP_SUFFIX_CONFIG = "group.suffixForAGroup";
	private static final String SUFFIX_DEFAULT = "_ldap";
	private String suffix = "";

	private static final String GROUP_PATH_CONFIG = "group.pathToListen";
	private static final String GROUP_PATH_DEFAULT = "/home/groups";
	private String absPath = "";
	private final boolean isDeep = true;

	private final int events = Event.NODE_ADDED;

	// a local change shouldn't fire an event; if true the event happens
	private final boolean noLocal = false;

	private final String[] uuids = null;

	private final String[] nodeTypes = new String[] {
			UserConstants.NT_REP_AUTHORIZABLE_FOLDER,
			UserConstants.NT_REP_GROUP };

	private Session observationSession = null;

	@Reference
	private SlingRepository repository;

	@Override
	public void onEvent(final EventIterator events) {

		while (events.hasNext()) {
			try {
				Event event = events.nextEvent();
				if (groupHasBeenAdded(event)) {

					final String path = event.getPath();
					completePathWithLdapAndMoveToNewPath(path);

				}
			} catch (RepositoryException ex) {
				log.error("Events cannot be processed.", ex);
			}
		}
	}

	/** group will be processed if : Event.NODE_ADDED and jcr:primaryType == rep:Group */
	private boolean groupHasBeenAdded(Event event) throws RepositoryException {
		return Event.NODE_ADDED == event.getType()
				&& event.getInfo().get(JcrConstants.JCR_PRIMARYTYPE)
						.equals(UserConstants.NT_REP_GROUP);
	}

	@SuppressWarnings("deprecation")
	private void completePathWithLdapAndMoveToNewPath(String path) {

		Session localSession = null; 
		
		try {

			localSession = repository.loginAdministrative(null);

			log.debug("Try to complete groupname with " + suffix + " and move node on path: " + path);

			String name = path.substring(path.lastIndexOf(PER));
			if(name.contains(suffix)) {
				throw new RepositoryException("Group with suffix:" + suffix + " created an Event! Shouldn't be happened.");
			}

			// cut path
			String pathWithoutName = path.substring(0, path.lastIndexOf(name));

			// change name
			name = name.concat(suffix);

			localSession.move(path, pathWithoutName.concat(name));
			localSession.save();

			log.debug("Group(" + path + ") moved to new path: " + pathWithoutName.concat(name));

		} catch (RepositoryException ex) {
			log.error("Group(" + path + ") cannot be completed with " + suffix + " and cannot be moved.", ex);
		} finally {
			if (localSession != null) {
				localSession.logout();
				localSession = null;
			}
		}
	}

	@Activate
	public void activate(final Map<String, String> config) throws RepositoryException {

		readConfigs(config);
		
		getAdminSessionAndAddListenerToObservationManager();
	}

	@SuppressWarnings("deprecation")
	private void getAdminSessionAndAddListenerToObservationManager() throws RepositoryException {

		observationSession = repository.loginAdministrative(null);

		final Workspace workspace = observationSession.getWorkspace();
		final ObservationManager observationManager = workspace.getObservationManager();

		observationManager.addEventListener(this, events, absPath, isDeep, uuids, nodeTypes, noLocal);
	}

	private void readConfigs(final Map<String, String> config) {
		suffix = PropertiesUtil.toString(config.get(GROUP_SUFFIX_CONFIG), SUFFIX_DEFAULT);
		absPath = PropertiesUtil.toString(config.get(GROUP_PATH_CONFIG), GROUP_PATH_DEFAULT);
	}

	@Deactivate
	public void deactivate(final Map<String, String> config) throws RepositoryException {
		try {
			final Workspace workspace = observationSession.getWorkspace();
			final ObservationManager observationManager = workspace.getObservationManager();

			if (observationManager != null) {
				observationManager.removeEventListener(this);
			}
		} finally {
			if (observationSession != null) {
				observationSession.logout();
				observationSession = null;
			}
		}
	}
}