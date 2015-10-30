package com.primeforce.grouphandler.events;

import java.security.Principal;
import java.util.Map;

import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.NodeType;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;
import javax.jcr.observation.ObservationManager;
import javax.jcr.version.VersionException;

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
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;
import org.apache.jackrabbit.oak.spi.security.user.action.AccessControlAction;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.base.util.AccessControlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.commons.jcr.JcrUtil;

/**
 * Updates ldap groups in /home/groups/ with given e.g. '_ldap' suffix. Config :
 * /apps/aemgrouphandler/config/
 * 
 * @author akos
 *
 */
@Component(immediate = true, metatype = true, label = "Spar Group Event Listener", description = "Updates ldap groups in /home/groups/ with 'ldap_' prefix.")
@Properties({
		@Property(name = GroupEventHandler.GROUP_PREFIX_CONFIG, label = "Prefix", value = "ldap_"),
		@Property(name = GroupEventHandler.GROUP_FOLDER_LDAP_CONFIG, label = "Ldap group folder", value = "ldap"),
		@Property(name = GroupEventHandler.GROUP_FOLDER_SPAR_CONFIG, label = "Spar folder", value = "spar"),
		@Property(name = GroupEventHandler.GROUP_PATH_CONFIG, label = "Path", value = "/home/groups") })
@Service
public class GroupEventHandler implements EventListener {

	private static final Logger log = LoggerFactory
			.getLogger(GroupEventHandler.class);

	private static final String PER = "/";

	public static final String GROUP_FOLDER_LDAP_CONFIG = "group.groupfolderldap";
	private static final String GROUP_FOLDER_LDAP_DEFAULT = "ldap";
	private String groupFolderLdap = "";

	public static final String GROUP_FOLDER_SPAR_CONFIG = "group.groupfolderspar";
	private static final String GROUP_FOLDER_SPAR_DEFAULT = "spar";
	private String groupFolderSpar = "";

	public static final String GROUP_PREFIX_CONFIG = "group.prefixForAGroup";
	private static final String PREFIX_DEFAULT = "ldap_";
	private String prefix = "";

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
		return Event.NODE_ADDED == event.getType()
				&& event.getInfo().get(JcrConstants.JCR_PRIMARYTYPE)
						.equals(UserConstants.NT_REP_GROUP);
				//TODO einschalten-> && ((String)event.getInfo().get(UserConstants.REP_PRINCIPAL_NAME)).startsWith("cn=");
	}

	private void completePathWithLdapAndMoveToNewPath(String path) {
		try {
			log.debug("Try to complete groupname with " + prefix
					+ " and move node on path: " + path);

			String name = path.substring(path.lastIndexOf(PER) + 1);
			// a loop because of the events is not possible.
			if (name.contains(prefix)) {
				throw new RepositoryException(
						"Group with prefix:"
								+ prefix
								+ " created an Event! Should not happen. Check noLocal property.");
			}
			// change name
			String groupNameCompletedWithPrefix = prefix.concat(name);

			Node groupNode = null;
			Node homeGroupNode = null;
			try {
				homeGroupNode = observationSession.getNode(homeGroupsFolder);
				groupNode = observationSession.getNode(groupNameCompletedWithPrefix);
			} catch (PathNotFoundException pnfe) {
				JackrabbitSession js = (JackrabbitSession) observationSession;
				final UserManager userManager = js.getUserManager();
//				UserManager userManager = AccessControlUtil.getUserManager(observationSession);
				Group sparGroup = userManager.createGroup(groupNameCompletedWithPrefix);
				Authorizable ldapGroupAsAuthorizable = userManager.getAuthorizable(name);
				sparGroup.addMember(ldapGroupAsAuthorizable);
			}
			
			observationSession.save();

		} catch (RepositoryException ex) {
			log.error("Group(" + path + ") cannot be completed with " + prefix
					+ " and cannot be moved.", ex);
		}
	}
	
	private void completePathWithLdapAndMoveToNewPath1(String path) {
		try {
			log.debug("Try to complete groupname with " + prefix
					+ " and move node on path: " + path);

			String name = path.substring(path.lastIndexOf(PER) + 1);
			// a loop because of the events is not possible.
			if (name.contains(prefix)) {
				throw new RepositoryException(
						"Group with prefix:"
								+ prefix
								+ " created an Event! Should not happen. Check noLocal property.");
			}

			Node ldapFolderNode = getOrCreateOurGroupFolder(homeGroupsFolder,
					groupFolderLdap);

			// change name
			String groupNameCompletedWithPrefix = prefix.concat(name);

			Node ldapGroupNode = null;
			try {
				ldapGroupNode = ldapFolderNode
						.getNode(groupNameCompletedWithPrefix);
				ldapGroupNode.remove();
			} catch (PathNotFoundException pnfe) {
				// it is okay, if that doesn't exist, we would like to remove
				// it.
			}

			// String pathForLdapChildGroup =
			// ldapFolderNode.getPath().concat(PER)
			// .concat(groupNameCompletedWithPrefix);

			String newLdapPath = ldapFolderNode.getPath().concat(PER)
					.concat(groupNameCompletedWithPrefix);
			observationSession.move(path, newLdapPath);
			
			observationSession.save();
			Node sparFolderNode = getOrCreateOurGroupFolder(homeGroupsFolder,
					groupFolderSpar);

			// change name
			String groupNameCompletedWithSpar = "spar_".concat(name);

			Node sparGroupNode = null;
			try {
				sparGroupNode = sparFolderNode.getNode(groupNameCompletedWithSpar);
				sparGroupNode.remove();
			} catch (PathNotFoundException pnfe) {
				// it is okay, if that doesn't exist.
			}

			observationSession.save();

			UserManager userManager = AccessControlUtil.getUserManager(observationSession);
			Group sparGroup = userManager.createGroup(groupNameCompletedWithSpar);
			Authorizable ldapGroupAsAuthorizable = userManager.getAuthorizable(name);
			sparGroup.addMember(ldapGroupAsAuthorizable);

			String sparGroupPathCompletedWithSparGroupName = sparFolderNode
					.getPath().concat(PER).concat(groupNameCompletedWithSpar);
			observationSession.move(sparGroup.getPath(),
					sparGroupPathCompletedWithSparGroupName);

			// Node inputNode = observationSession.getNode(path);
			// Node copiedNode = JcrUtil.copy(inputNode, sparFolderNode, name,
			// true);
			// copiedNode.setProperty(UserConstants.REP_PRINCIPAL_NAME, name);
			// copiedNode.setProperty(UserConstants.REP_AUTHORIZABLE_ID, name);

			observationSession.save();

			// observationSession.save();
			//
			// String pathForLdapChildGroup =
			// ldapFolderNode.getPath().concat(PER)
			// .concat(groupNameCompletedWithPrefix);
			// observationSession.move(path, pathForLdapChildGroup);
			// observationSession.save();

			// TODO : most pedig csinalni kell egy parent-et neki a
			// /home/groups/spar/alatt

			// definialhato kene legyen a spar-os group-ok helye, meg az ldap-os
			// group-ok helye is. Tolem lehet egymas alatt is.

			// /home/groups/ldap megvan.

			// /home/groups/g/gragraGroup -> /home/groups/g/ldap_gragraGroup
			// -> /home/groups/ldap/

		} catch (RepositoryException ex) {
			log.error("Group(" + path + ") cannot be completed with " + prefix
					+ " and cannot be moved.", ex);
		}
	}

	private Node copy(Node src, Node dstParent, String name, boolean bestEffort)
			throws RepositoryException {

		if (name == null) {
			name = src.getName();
		}

		if (dstParent.hasNode(name)) {
			dstParent.getNode(name).remove();
		}

		Node dst = dstParent.addNode(name, src.getPrimaryNodeType().getName());
		for (NodeType mix : src.getMixinNodeTypes()) {
			try {
				dst.addMixin(mix.getName());
			} catch (RepositoryException e) {
				if (!bestEffort) {
					throw e;
				}
			}
		}

		for (PropertyIterator iter = src.getProperties(); iter.hasNext();) {
			try {
				copy(iter.nextProperty(), dst, null);
			} catch (RepositoryException e) {
				if (!bestEffort) {
					throw e;
				}
			}
		}

		for (NodeIterator iter = src.getNodes(); iter.hasNext();) {
			Node n = iter.nextNode();
			// if (!n.getDefinition().isProtected()) {
			try {
				copy(n, dst, null, bestEffort);
			} catch (RepositoryException e) {
				if (!bestEffort) {
					throw e;
				}
			}
			// }
		}
		// copiedNode.setProperty(UserConstants.REP_PRINCIPAL_NAME, name);
		// copiedNode.setProperty(UserConstants.REP_AUTHORIZABLE_ID, name);

		return dst;

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

	private void completePathWithLdapAndMoveToNewPath2(String path) {

		try {
			log.debug("Try to complete groupname with " + prefix
					+ " and move node on path: " + path);

			String name = path.substring(path.lastIndexOf(PER) + 1);
			// a loop because of the events is not possible.
			if (name.contains(prefix)) {
				throw new RepositoryException(
						"Group with prefix:"
								+ prefix
								+ " created an Event! Should not happen. Check noLocal property.");
			}

			// cut path
			String pathWithoutName = path.substring(0, path.lastIndexOf(name));

			// change name
			String ldapName = prefix.concat(name);

			Node ldapGroup = JcrUtils.getOrCreateByPath(
					pathWithoutName.concat(ldapName),
					UserConstants.NT_REP_GROUP, observationSession);
			// observationSession.getNode(path)

			// if ldapGroup exists and has already a group, we remove that group
			String pathForChildGroup = ldapGroup.getPath().concat(PER)
					.concat(name);
			if (ldapGroup.hasNode(pathForChildGroup)) {
				Node node = ldapGroup.getNode(pathForChildGroup);
				node.remove();
			}

			observationSession.move(path, pathForChildGroup);
			observationSession.save();

			log.debug("Group(" + path + ") moved to new path: "
					+ pathWithoutName.concat(name));

		} catch (RepositoryException ex) {
			log.error("Group(" + path + ") cannot be completed with " + prefix
					+ " and cannot be moved.", ex);
		}
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
		prefix = PropertiesUtil.toString(config.get(GROUP_PREFIX_CONFIG),
				PREFIX_DEFAULT);
		homeGroupsFolder = PropertiesUtil.toString(config.get(GROUP_PATH_CONFIG),
				GROUP_PATH_DEFAULT);
		groupFolderLdap = PropertiesUtil.toString(config.get(GROUP_FOLDER_LDAP_CONFIG),
				GROUP_FOLDER_LDAP_DEFAULT);
		groupFolderSpar = PropertiesUtil.toString(config.get(GROUP_FOLDER_SPAR_CONFIG),
				GROUP_FOLDER_SPAR_DEFAULT);
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