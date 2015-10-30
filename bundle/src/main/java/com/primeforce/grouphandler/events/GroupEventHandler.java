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

/**
 * Updates ldap groups in /home/groups/ with given e.g. '_ldap' suffix. Config :
 * /apps/aemgrouphandler/config/
 * 
 * @author akos
 *
 */
@Component(immediate = true, metatype = true, label = "Ldap Group Event Listener", description = "Updates ldap groups in /home/groups/ with 'ldap_' prefix.")
@Properties({
		@Property(name = GroupEventHandler.GROUP_PREFIX_LDAP_CONFIG, label = "Prefix", value = "ldap_"),
		@Property(name = GroupEventHandler.GROUP_FOLDER_CRX_CONFIG, label = "Crx folder", value = "crx"),
		@Property(name = GroupEventHandler.GROUP_PREFIX_CRX_CONFIG, label = "Crx prefix", value = "crx_"),
		@Property(name = GroupEventHandler.GROUP_PATH_CONFIG, label = "Path", value = "/home/groups") })
@Service
public class GroupEventHandler implements EventListener {

	private static final Logger log = LoggerFactory
			.getLogger(GroupEventHandler.class);

	private static final String PER = "/";

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
		Map info = event.getInfo();
		return /*Event.NODE_ADDED == event.getType()
				&& event.getInfo().get(JcrConstants.JCR_PRIMARYTYPE)
						.equals(UserConstants.NT_REP_GROUP)*/
		//
				info != null && info.containsKey("rep:fullname") && ((String)info.get("rep:fullname")).startsWith(groupPrefixLdap);
	}

	private void completePathWithLdapAndMoveToNewPath(String path) {
		try {
			log.debug("Try to complete groupname with " + groupPrefixLdap
					+ " and move node on path: " + path);

			String name = path.substring(path.lastIndexOf(groupPrefixLdap)+groupPrefixLdap.length());
			// a loop because of the events is not possible.
			//TODO : vielleicht ist es nicht notig
//			if (name.contains(ldapGroupPrefix)) {
//				throw new RepositoryException(
//						"Group with prefix:"
//								+ ldapGroupPrefix
//								+ " created an Event! Should not happen. Check noLocal property.");
//			}
			
			// /home/groups/s/ldap_sa1
			// ldap_sa1
			// neu : spar_sa1
			// change name
			String groupNameCompletedWithPrefix = groupPrefixCrx.concat(name);
			
			// ldap_sa1
			// /home/groups/ + crx
			Node groupFolderCrxNode = getOrCreateOurGroupFolder(homeGroupsFolder, groupFolderCrx);
			// /home/groups/crx
			
			Node crxGroupNode = null;
			Group crxGroup = null;
			JackrabbitSession session = (JackrabbitSession) observationSession;
			final UserManager userManager = session.getUserManager();
			try {
				crxGroupNode = groupFolderCrxNode.getNode(groupNameCompletedWithPrefix);
				crxGroup = (Group) userManager.getAuthorizable(crxGroupNode.getPath());
			} catch (PathNotFoundException pnfe) {
//				UserManager userManager = AccessControlUtil.getUserManager(observationSession);
				String neueCrxGroupPath = groupFolderCrxNode.getPath().concat(PER).concat(groupNameCompletedWithPrefix);
				// createGroup /home/groups/crx/spar_sa1
				crxGroup = userManager.createGroup(neueCrxGroupPath);
			}
			Authorizable ldapGroupAsAuthorizable = userManager.getAuthorizable(path);
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
	private void completePathWithLdapAndMoveToNewPath1(String path) {
	}

	public static javax.jcr.Property copy(javax.jcr.Property src,
			Node dstParent, String name) throws RepositoryException {
		if (!src.getDefinition().isProtected()) {
			if (name == null) {
				name = src.getName();
			}

			if (dstParent.hasProperty(name)) {
				dstParent.getProperty(name).remove();
			}

			if (src.getDefinition().isMultiple()) {
				return dstParent.setProperty(name, src.getValues());
			}
			return dstParent.setProperty(name, src.getValue());
		}

		return null;
	}

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