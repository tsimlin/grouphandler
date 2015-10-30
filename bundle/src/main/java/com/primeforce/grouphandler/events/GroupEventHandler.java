package com.primeforce.grouphandler.events;

import java.util.Map;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
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
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.commons.jcr.JcrConstants;

/**
 * Updates ldap groups in /home/groups/ with given e.g. '_ldap' suffix. Config :
 * /apps/aemgrouphandler/config/
 * 
 * @author akos
 *
 */
@Component(immediate = true, metatype = true, label = "Ldap Group Event Listener", description = "Creates a crx group for the input ldap groups in /home/groups/crx with 'crx_' prefix.")
@Properties({
		@Property(name = "test", label = "Test with 'normal' group creation", boolValue = false),
		@Property(name = GroupEventHandler.GROUP_PREFIX_LDAP_CONFIG, label = "Prefix", value = "ldap_", description="prefix for the input ldap group"),
		@Property(name = GroupEventHandler.GROUP_FOLDER_CRX_CONFIG, label = "Crx folder", value = "crx", description="Name of the crx folder"),
		@Property(name = GroupEventHandler.GROUP_PREFIX_CRX_CONFIG, label = "Crx prefix", value = "crx_", description="Prefix for the crx group"),
		@Property(name = GroupEventHandler.GROUP_PATH_CONFIG, label = "Path", value = "/home/groups", description="Path to be checked by the Group Event Handler") })
@Service
public class GroupEventHandler implements EventListener {

	private static final String PER = "/";

	private static final Logger log = LoggerFactory
			.getLogger(GroupEventHandler.class);

	private boolean test = false;

	public static final String GROUP_FOLDER_CRX_CONFIG = "group.groupFolderCrx";
	private static final String GROUP_FOLDER_CRX_DEFAULT = "crx";
	private String groupFolderCrx = "";

	public static final String GROUP_PREFIX_CRX_CONFIG = "group.groupPrefixCrx";
	private static final String GROUP_PREFIX_CRX_CONFIG_DEFAULT = "crx_";
	private String groupPrefixCrx = "";

	public static final String GROUP_PREFIX_LDAP_CONFIG = "group.groupPrefixLdap";
	private static final String GROUP_PREFIX_LDAP_DEFAULT = "ldap_";
	private String groupPrefixLdap = "";

	public static final String GROUP_PATH_CONFIG = "group.pathToListen";
	private static final String GROUP_PATH_DEFAULT = "/home/groups";
	private String homeGroupsFolder = "";
	private final boolean isDeep = true;

	private final int events = Event.NODE_ADDED;

	// true means that a local change shouldn't fire an event; if false the
	// event happens
	private final boolean noLocal = true;

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
				if (ldapGroupHasBeenAdded(event)) {

					final String path = event.getPath();
					completePathWithLdapAndMoveToNewPath(path);

				}
			} catch (RepositoryException ex) {
				log.error("Events cannot be processed.", ex);
			}
		}
	}

	/**
	 * group will be processed if : Event.NODE_ADDED and jcr:primaryType ==
	 * rep:Group
	 */
	private boolean ldapGroupHasBeenAdded(Event event) throws RepositoryException {
		@SuppressWarnings("rawtypes")
		Map info = event.getInfo();
		if (test) {
			//for testing from /useradmin
			return Event.NODE_ADDED == event.getType()
					&& info.get(JcrConstants.JCR_PRIMARYTYPE).equals(UserConstants.NT_REP_GROUP);
		} else {
			//for real ldap groups
			return info != null && info.containsKey("rep:fullname") && ((String)info.get("rep:fullname")).startsWith(groupPrefixLdap);
		}
	}

	private void completePathWithLdapAndMoveToNewPath(String path) {
		try {
			log.debug("Try to complete groupname with " + groupPrefixLdap
					+ " and move node on path: " + path);

			//input e.g.:
			// /home/groups/l/ldap_group1
			// name : group1
			String name = path.substring(path.lastIndexOf(groupPrefixLdap)+groupPrefixLdap.length());

			// change name
			// alte e.g.: group1
			// neu : crx_group1
			String groupNameCompletedWithPrefix = groupPrefixCrx.concat(name);
			
			// create or get /home/groups/crx
			getOrCreateOurGroupFolder(homeGroupsFolder, groupFolderCrx);
			
			JackrabbitSession session = (JackrabbitSession) observationSession;
			final UserManager userManager = session.getUserManager();
			// get group /home/groups/crx/crx_group1
			Group crxGroup = (Group) userManager.getAuthorizable(groupNameCompletedWithPrefix);
			if(crxGroup == null) {
				// createGroup /home/groups/crx/crx_group1
				crxGroup = userManager.createGroup(groupNameCompletedWithPrefix);
			}
			
			// add ldap_group1 as member to /home/groups/crx/crx_group1
			Authorizable ldapGroupAsAuthorizable = userManager.getAuthorizableByPath(path);
			crxGroup.addMember(ldapGroupAsAuthorizable);
			
			observationSession.save();

		} catch (RepositoryException ex) {
			log.error("Group(" + path + ") cannot be completed with " + groupPrefixLdap
					+ " and cannot be moved.", ex);
		}
		
	}

//	/**
//	 * input
//	 */home/groups/s/ldap_testgroup1
//	 * result
//	 * /home/groups/crx/crx_testgroup1 -> mit member : ldap_testgroup1
//	 * 1. mit neu angelegten -> erwartete result: wie oben.
//	 *
//	 * 2. wenn die Crx Gruppe schon vorhanden ist, Logik durchlaufen lassen -> erwartete result: das gleiche wie beim 1. 
//	 */

	private Node getOrCreateOurGroupFolder(String groupFolder,
			String ourGroupFolder) throws RepositoryException {
		Node folderNode = null;
		Node homeGroupNode = null;
		try {
			homeGroupNode = observationSession.getNode(groupFolder);
			folderNode = homeGroupNode.getNode(ourGroupFolder);
		} catch (PathNotFoundException pnfe) {
			folderNode = homeGroupNode.addNode(ourGroupFolder,
					UserConstants.NT_REP_AUTHORIZABLE_FOLDER);
		}
		return folderNode;
	}

	@Activate
	public void activate(final Map<String, String> config)
			throws RepositoryException {

		readConfigs(config);

		getAdminSessionAndAddListenerToObservationManager();
	}

	@SuppressWarnings("deprecation")
	private void getAdminSessionAndAddListenerToObservationManager()
			throws RepositoryException {

		observationSession = repository.loginAdministrative(null);

		final Workspace workspace = observationSession.getWorkspace();
		final ObservationManager observationManager = workspace
				.getObservationManager();

		observationManager.addEventListener(this, events, homeGroupsFolder, isDeep,
				uuids, nodeTypes, noLocal);
	}

	private void readConfigs(final Map<String, String> config) {
		groupPrefixLdap = PropertiesUtil.toString(config.get(GROUP_PREFIX_LDAP_CONFIG),
				GROUP_PREFIX_LDAP_DEFAULT);
		homeGroupsFolder = PropertiesUtil.toString(config.get(GROUP_PATH_CONFIG),
				GROUP_PATH_DEFAULT);
		groupFolderCrx = PropertiesUtil.toString(config.get(GROUP_FOLDER_CRX_CONFIG),
				GROUP_FOLDER_CRX_DEFAULT);
		groupPrefixCrx = PropertiesUtil.toString(config.get(GROUP_PREFIX_CRX_CONFIG),
				GROUP_PREFIX_CRX_CONFIG_DEFAULT);
		test = PropertiesUtil.toBoolean(config.get("test"),
				true);
	}

	@Deactivate
	public void deactivate(final Map<String, String> config)
			throws RepositoryException {
		try {
			final Workspace workspace = observationSession.getWorkspace();
			final ObservationManager observationManager = workspace
					.getObservationManager();

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
