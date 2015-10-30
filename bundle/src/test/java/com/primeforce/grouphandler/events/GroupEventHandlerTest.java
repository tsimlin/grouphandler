package com.primeforce.grouphandler.events;

import static org.junit.Assert.assertEquals;
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
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
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
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
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
	public void newLdapGroupAdded_crxGroupCreatedInCrxFolderAndLdapGroupAdded() throws RepositoryException {
		boolean crxFolderExists = false;
		newLdapGroupAdded_LdapGroupAddedToCrxFolderWhatExistsOrNot(crxFolderExists);
	}
	
	@Test
	public void newLdapGroupAdded_crxGroupExistsInCrxFolderAndLdapGroupAdded() throws RepositoryException {

		boolean crxFolderExists = true;
		newLdapGroupAdded_LdapGroupAddedToCrxFolderWhatExistsOrNot(crxFolderExists);
		
	}

	private void newLdapGroupAdded_LdapGroupAddedToCrxFolderWhatExistsOrNot(boolean crxFolderExists)
			throws LoginException, RepositoryException,
			UnsupportedRepositoryOperationException, PathNotFoundException,
			AccessDeniedException, AuthorizableExistsException,
			ItemExistsException, ReferentialIntegrityException,
			ConstraintViolationException, InvalidItemStateException,
			VersionException, LockException, NoSuchNodeTypeException {
		//Arrange
		
		//prepare session & event activation
		when(repository.loginAdministrative(null)).thenReturn(session);
		when(session.getWorkspace()).thenReturn(workspace);
		when(workspace.getObservationManager()).thenReturn(observationManager);
	
		//prepare event
		EventIterator events = mock(EventIterator.class);
		String ldapGroupName = "ldap_testgroup";
		String inputPath = "/home/groups/t/" + ldapGroupName ;
		Event event = createEvent(Event.NODE_ADDED, inputPath);
		when(events.nextEvent()).thenReturn(event);
		when(events.hasNext()).thenReturn(true).thenReturn(false);

		Node homeGroupsNode = mock(Node.class);
		when(session.getNode("/home/groups/")).thenReturn(homeGroupsNode);
		when(session.getUserManager()).thenReturn(userManager);
		Group group = mock(Group.class);
		when(userManager.createGroup("crx_testgroup")).thenReturn(group);
		Authorizable ldapGroup = mock(Group.class);
		when(userManager.getAuthorizableByPath("/home/groups/t/ldap_testgroup")).thenReturn(ldapGroup);
		when(userManager.getAuthorizable("crx_testgroup")).thenReturn(group);
		
		Node crxFolderNode = mock(Node.class);
		when(crxFolderNode.getPath()).thenReturn("/home/groups/crx");
		when(crxFolderNode.getNode("crx_testgroup")).thenThrow(new PathNotFoundException());

		if (crxFolderExists) {
			when(homeGroupsNode.getNode("crx")).thenReturn(crxFolderNode);
		} else {
			when(homeGroupsNode.getNode("crx")).thenThrow(new PathNotFoundException());
			when(homeGroupsNode.addNode("crx",
					UserConstants.NT_REP_AUTHORIZABLE_FOLDER)).thenReturn(crxFolderNode);
		}
			
		
		when(homeGroupsNode.getNode("crx_testgroup")).thenThrow(new PathNotFoundException());
		
		//Act
		//call activate for configs
		Map<String, String> config = createMapWithConfigs();
		groupEventHandler.activate(config);
		//call method to test
		groupEventHandler.onEvent(events);

		//Assert
		verify(group).addMember(ldapGroup);
		verify(session).save();
	}

	private Map<String, String> createMapWithConfigs() {
		Map<String, String>  map = new HashMap<String, String>();
		map.put("group.pathToListen", "/home/groups/");
		map.put("group.groupPrefixLdap", "ldap_");
		map.put("group.groupFolderCrx", "crx");
		map.put("group.groupPrefixCrx", "crx_");
		return map;
	}

	private Event createEvent(final int type, final String path) {
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
				map.put("rep:fullname", "ldap_testgroup");
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
