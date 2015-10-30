package com.primeforce.grouphandler.events;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.ObservationManager;

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
	private Session session;
	
	@Mock
	private Workspace workspace;
	
	@Mock
	private ObservationManager observationManager;
	
	@InjectMocks
	private GroupEventHandler groupEventHandler = new GroupEventHandler();

	@SuppressWarnings("deprecation")
	@Test
	public void newGroupAdded_nameCompletedWithLdapMovedToLdapSparGroupCreated() throws RepositoryException {
		
		//Arrange
		
		//prepare session & event activation
		when(repository.loginAdministrative(null)).thenReturn(session);
		when(session.getWorkspace()).thenReturn(workspace);
		when(workspace.getObservationManager()).thenReturn(observationManager);
		
		Node homeGroupsNode = mock(Node.class);
		when(session.getNode("/home/groups/")).thenReturn(homeGroupsNode);
		Node sparNode = mock(Node.class);
		when(sparNode.getNode("spar_testgroup")).thenThrow(new PathNotFoundException());
		when(homeGroupsNode.getNode("spar")).thenReturn(sparNode);
		Node ldapNode = mock(Node.class);
		when(ldapNode.getPath()).thenReturn("/home/groups/ldap");
		when(ldapNode.getNode("ldap_testgroup")).thenThrow(new PathNotFoundException());
		when(homeGroupsNode.getNode("ldap")).thenReturn(ldapNode);
		
//		Node node = mock(Node.class);
//		when(session.getRootNode()).thenReturn(node);
		
		//prepare event
		EventIterator events = mock(EventIterator.class);
		String inputPath = "/home/groups/t/testgroup";
		Event event = createEvent(Event.NODE_ADDED, inputPath);
		when(events.nextEvent()).thenReturn(event);
		when(events.hasNext()).thenReturn(true).thenReturn(false);

		//Act
		//call activate for configs
		Map<String, String> config = createMapWithConfigs();
		groupEventHandler.activate(config);
		//call method to test
		groupEventHandler.onEvent(events);
		
		//Assert
		String expectedPath = "/home/groups/t/ldap_testgroup";
		ArgumentCaptor<String> path1arg = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> path2arg = ArgumentCaptor.forClass(String.class);
		   verify(session).move(path1arg.capture(), path2arg.capture());
		   assertEquals("not as awaited", path1arg.getValue(), inputPath);
		   assertEquals("not as awaited", path2arg.getValue(), expectedPath);

		verify(session).save();
	}

	@SuppressWarnings("deprecation")
//	@Test
	public void newGroupAdded_nameCompletedWithLdap() throws RepositoryException {
		
		//Arrange
		
		//prepare session & event activation
		when(repository.loginAdministrative(null)).thenReturn(session);
		when(session.getWorkspace()).thenReturn(workspace);
		when(workspace.getObservationManager()).thenReturn(observationManager);
		
		Node node = mock(Node.class);
		when(session.getRootNode()).thenReturn(node);
		
		//prepare event
		EventIterator events = mock(EventIterator.class);
		String inputPath = "/home/groups/t/testgroup";
		Event event = createEvent(Event.NODE_ADDED, inputPath);
		when(events.nextEvent()).thenReturn(event);
		when(events.hasNext()).thenReturn(true).thenReturn(false);

		//Act
		//call activate for configs
		Map<String, String> config = createMapWithConfigs();
		groupEventHandler.activate(config);
		//call method to test
		groupEventHandler.onEvent(events);
		
		//Assert
		String expectedPath = "/home/groups/t/ldap_testgroup";
		ArgumentCaptor<String> path1arg = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> path2arg = ArgumentCaptor.forClass(String.class);
		   verify(session).move(path1arg.capture(), path2arg.capture());
		   assertEquals("not as awaited", path1arg.getValue(), inputPath);
		   assertEquals("not as awaited", path2arg.getValue(), expectedPath);

		verify(session).save();
	}

	private Map<String, String> createMapWithConfigs() {
		Map<String, String>  map = new HashMap<String, String>();
		map.put("group.prefixForAGroup", "ldap_");
		map.put("group.pathToListen", "/home/groups/");
		map.put("group.groupfolderldap", "ldap");
		map.put("group.groupfolderspar", "spar");
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
