package com.primeforce.grouphandler.events;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import javax.jcr.AccessDeniedException;
import javax.jcr.InvalidItemStateException;
import javax.jcr.ItemExistsException;
import javax.jcr.LoginException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.Value;
import javax.jcr.Workspace;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.ObservationManager;
import javax.jcr.version.VersionException;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.AuthorizableExistsException;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;
import org.apache.jackrabbit.value.StringValue;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.day.cq.commons.jcr.JcrConstants;

@RunWith(MockitoJUnitRunner.class)
public class GroupEventHandlerTest {

	@Mock
	private SlingRepository repository;

	@Mock
	private JackrabbitSession session;
	
	@Mock
	private Workspace workspace;
	
	@Mock
	private ObservationManager observationManager;
	
	@Mock
	private UserManager userManager;
	
	@InjectMocks
	private GroupEventHandler groupEventHandler = new GroupEventHandler();

	@Test
	public void newLdapGroupAddedTestflagFalse_crxGroupCreatedInCrxFolderAndLdapGroupAdded() throws RepositoryException {
		boolean crxFolderExists = false;
		newLdapGroupAddedTestflagFalse_LdapGroupAddedToCrxFolderWhatExistsOrNot(crxFolderExists);
	}

	
	@Test
	public void newLdapGroupAdded_crxGroupCreatedInCrxFolderAndLdapGroupAdded() throws RepositoryException {
		boolean crxFolderExists = false;
		newLdapGroupAddedTestflagTrue_LdapGroupAddedToCrxFolderWhatExistsOrNot(crxFolderExists);
	}
	
	@Test
	public void newLdapGroupAdded_crxGroupExistsInCrxFolderAndLdapGroupAdded() throws RepositoryException {

		boolean crxFolderExists = true;
		newLdapGroupAddedTestflagTrue_LdapGroupAddedToCrxFolderWhatExistsOrNot(crxFolderExists);
		
	}

	private void newLdapGroupAddedTestflagFalse_LdapGroupAddedToCrxFolderWhatExistsOrNot(boolean crxFolderExists)
			throws RepositoryException {
		newLdapGroupAdded_LdapGroupAddedToCrxFolderWhatExistsOrNot(crxFolderExists, "false");
	}

	private void newLdapGroupAddedTestflagTrue_LdapGroupAddedToCrxFolderWhatExistsOrNot(boolean crxFolderExists)
			throws RepositoryException {
		newLdapGroupAdded_LdapGroupAddedToCrxFolderWhatExistsOrNot(crxFolderExists, "true");
	}
	
	@SuppressWarnings("deprecation")
	private void newLdapGroupAdded_LdapGroupAddedToCrxFolderWhatExistsOrNot(boolean crxFolderExists, String testflag)
			throws RepositoryException {
		//Arrange
		String homeGroupsPath = "/home/groups/";
		String ldapGroupName = "ldap_testgroup";
		String inputPath = "/home/groups/t/" + ldapGroupName ;
		String expectedCrxGroup = "crx_testgroup";
		String crx = "crx";
		String homeGroupsCrxPath = "/home/groups/" + crx;
		
		//prepare session & event activation
		when(repository.loginAdministrative(null)).thenReturn(session);
		when(session.getWorkspace()).thenReturn(workspace);
		when(workspace.getObservationManager()).thenReturn(observationManager);
	
		//prepare event
		EventIterator events = mock(EventIterator.class);
		Event event = createEvent(Event.NODE_ADDED, inputPath, ldapGroupName);
		when(events.nextEvent()).thenReturn(event);
		when(events.hasNext()).thenReturn(true).thenReturn(false);

		Node homeGroupsNode = mock(Node.class);
		when(session.getNode(homeGroupsPath)).thenReturn(homeGroupsNode);
		when(session.getUserManager()).thenReturn(userManager);
		Group group = mock(Group.class);
		when(userManager.createGroup(expectedCrxGroup)).thenReturn(group);
		Authorizable ldapGroup = mock(Group.class);
		when(userManager.getAuthorizableByPath(inputPath)).thenReturn(ldapGroup);
		when(userManager.getAuthorizable(ldapGroupName)).thenReturn(ldapGroup);
		when(userManager.getAuthorizable(expectedCrxGroup)).thenReturn(group);
		when(ldapGroup.getID()).thenReturn(ldapGroupName);
		Value[] values = createValue(ldapGroupName);
		when(ldapGroup.getProperty("rep:fullname")).thenReturn(values);
		
		Node crxFolderNode = mock(Node.class);
		when(crxFolderNode.getPath()).thenReturn(homeGroupsCrxPath);
		when(crxFolderNode.getNode(expectedCrxGroup)).thenThrow(new PathNotFoundException());

		if (crxFolderExists) {
			when(homeGroupsNode.getNode(crx)).thenReturn(crxFolderNode);
		} else {
			when(homeGroupsNode.getNode(crx)).thenThrow(new PathNotFoundException());
			when(homeGroupsNode.addNode(crx,
					UserConstants.NT_REP_AUTHORIZABLE_FOLDER)).thenReturn(crxFolderNode);
		}
			
		
		when(homeGroupsNode.getNode(expectedCrxGroup)).thenThrow(new PathNotFoundException());
		
		//Act
		//call activate for configs
		Map<String, String> config = createMapWithConfigs(testflag);
		groupEventHandler.activate(config);
		//call method to test
		groupEventHandler.onEvent(events);

		//Assert
		verify(group).addMember(ldapGroup);
		verify(session).save();
	}

	private Value[] createValue(String ldapGroupName) {
		return new Value[]{new StringValue(ldapGroupName)};
	}


	private Map<String, String> createMapWithConfigs(String testFlagValue) {
		Map<String, String>  map = new HashMap<String, String>();
		map.put("group.pathToListen", "/home/groups/");
		map.put("group.groupPrefixLdap", "ldap_");
		map.put("group.groupPrefixCrx", "crx_");
		map.put("group.groupCheckProperty", "rep:fullname");
		map.put("test", testFlagValue);
		return map;
	}

	private Event createEvent(final int type, final String path, final String ldapGroupName) {
		return new Event() {
			
			@Override
			public String getUserID() {
				return null;
			}
			
			@Override
			public String getUserData() throws RepositoryException {
				return null;
			}
			
			@Override
			public int getType() {
				return type;
			}
			
			@Override
			public String getPath() throws RepositoryException {
				return path;
			}
			
			@SuppressWarnings({ "rawtypes", "unchecked" })
			@Override
			public Map getInfo() throws RepositoryException {
				Map map = new HashMap();
				map.put(JcrConstants.JCR_PRIMARYTYPE, UserConstants.NT_REP_GROUP);
				map.put("rep:fullname", ldapGroupName);
				return map ;
			}
			
			@Override
			public String getIdentifier() throws RepositoryException {
				return null;
			}
			
			@Override
			public long getDate() throws RepositoryException {
				return 0;
			}
		};
	}
}
