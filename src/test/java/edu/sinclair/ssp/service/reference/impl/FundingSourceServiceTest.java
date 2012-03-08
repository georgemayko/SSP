package edu.sinclair.ssp.service.reference.impl;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.springframework.security.core.GrantedAuthority;

import edu.sinclair.ssp.dao.reference.FundingSourceDao;
import edu.sinclair.ssp.model.ObjectStatus;
import edu.sinclair.ssp.model.Person;
import edu.sinclair.ssp.model.reference.FundingSource;
import edu.sinclair.ssp.security.MockUser;
import edu.sinclair.ssp.security.SspUser;
import edu.sinclair.ssp.service.ObjectNotFoundException;
import edu.sinclair.ssp.service.SecurityService;

public class FundingSourceServiceTest {

	private FundingSourceServiceImpl service;
	private FundingSourceDao dao;
	private SecurityService securityService;
	
	private SspUser testUser;
	
	@Before
	public void setup(){
		service = new FundingSourceServiceImpl();
		dao = createMock(FundingSourceDao.class);
		securityService = createMock(SecurityService.class);

		service.setDao(dao);
		service.setSecurityService(securityService);
		
		testUser = new MockUser(new Person(), "testuser", new ArrayList<GrantedAuthority>());
		
	}
	
	@Test
	public void testGetAll() {
		List<FundingSource> daoAll = new ArrayList<FundingSource>();
		daoAll.add(new FundingSource());
		
		expect(dao.getAll(ObjectStatus.ACTIVE)).andReturn(daoAll);
		
		replay(dao);
		
		List<FundingSource> all = service.getAll(ObjectStatus.ACTIVE);
		assertTrue(all.size()>0);
		verify(dao);
	}

	@Test
	public void testGet() throws ObjectNotFoundException {
		UUID id = UUID.randomUUID();
		FundingSource daoOne = new FundingSource(id);
		
		expect(dao.get(id)).andReturn(daoOne);
		
		replay(dao);
		
		assertNotNull(service.get(id));
		verify(dao);
	}

	@Test
	public void testSave() throws ObjectNotFoundException {
		UUID id = UUID.randomUUID();
		FundingSource daoOne = new FundingSource(id);
		
		expect(dao.get(id)).andReturn(daoOne);
		expect(dao.save(daoOne)).andReturn(daoOne);
		expect(securityService.currentlyLoggedInSspUser()).andReturn(testUser);
		
		replay(dao);
		replay(securityService);
		
		assertNotNull(service.save(daoOne));
		verify(dao);
		verify(securityService);
	}

	@Test
	public void testDelete() throws ObjectNotFoundException {
		UUID id = UUID.randomUUID();
		FundingSource daoOne = new FundingSource(id);
		
		expect(dao.get(id)).andReturn(daoOne).times(2);
		expect(dao.save(daoOne)).andReturn(daoOne);
		expect(dao.get(id)).andReturn(null);
		expect(securityService.currentlyLoggedInSspUser()).andReturn(testUser);
		
		replay(dao);
		replay(securityService);
		
		service.delete(id);
		
		boolean found = true;
		try{
			service.get(id);
		}catch(ObjectNotFoundException e){
			found = false;
		}
		
		assertFalse(found);
		verify(dao);
		verify(securityService);
	}

}
