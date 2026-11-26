/*
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:03 )
 * @author Pascal
*/
package org.itech.labSampleTracker.service_impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.itech.labSampleTracker.dao.AppUserHasCircuitRepository;
import org.itech.labSampleTracker.dao.AppUserHasDistrictRepository;
import org.itech.labSampleTracker.dao.AppUserHasLabRepository;
import org.itech.labSampleTracker.dao.AppUserHasRegionRepository;
import org.itech.labSampleTracker.dao.AppUserHasSiteRepository;
import org.itech.labSampleTracker.dao.AppUserRepository;
import org.itech.labSampleTracker.entities.AppUser;
import org.itech.labSampleTracker.entities.AppUserHasCircuit;
import org.itech.labSampleTracker.entities.AppUserHasDistrict;
import org.itech.labSampleTracker.entities.AppUserHasLab;
import org.itech.labSampleTracker.entities.AppUserHasRegion;
import org.itech.labSampleTracker.entities.AppUserHasSite;
import org.itech.labSampleTracker.exception.OperationFailedException;
import org.itech.labSampleTracker.service.AppUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * <h2>AppUserServiceimpl</h2>
 */
@Service
@Transactional
public class AppUserServiceImpl implements AppUserService, ApplicationListener<AuthenticationSuccessEvent> {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AppUserServiceImpl.class);

	@Autowired
	private AppUserRepository appuserRepo;

	@Autowired
	private AppUserHasRegionRepository appUserHasRegionRepository;

	@Autowired
	private AppUserHasDistrictRepository appUserHasDistrictRepository;

	@Autowired
	private AppUserHasSiteRepository appUserHasSiteRepository;

	@Autowired
	private AppUserHasLabRepository appUserHasLabRepository;

	@Autowired
	private AppUserHasCircuitRepository appUserHasCircuitRepository;

	@Autowired
	private BCryptPasswordEncoder bCryptPasswordEncoder;

	@PersistenceContext
	private EntityManager em;

	@Override
	public void onApplicationEvent(AuthenticationSuccessEvent event) {
		String userName = ((UserDetails) event.getAuthentication().getPrincipal()).getUsername();
		updateLastLogin(userName);
	}

	@Override
	public org.springframework.data.domain.Page<java.util.Map<String, Object>> findUsersAdvanced(
			org.springframework.data.domain.Pageable pageable,
			String searchText, java.util.List<String> roles, boolean rolesActive,
			Boolean activeFlag, Boolean lockedFlag,
			Integer regionId, Integer districtId, Integer labId) {
		try {
			java.util.List<String> effectiveRoles = (rolesActive && roles != null && !roles.isEmpty())
					? roles : java.util.List.of("__none__");
			return appuserRepo.findUsersAdvanced(pageable, searchText, effectiveRoles, rolesActive,
					activeFlag, lockedFlag, regionId, districtId, labId);
		} catch (Exception ex) {
			log.error("findUsersAdvanced failed (search={}, roles={}, active={}, locked={}, region={}, district={}, lab={}): {}",
					searchText, roles, activeFlag, lockedFlag, regionId, districtId, labId, ex.getMessage(), ex);
			return org.springframework.data.domain.Page.empty(pageable);
		}
	}

	@Override
	public AppUser create(AppUser d, boolean mustCryptPassword) {
		AppUser entity;
		try {
			if (mustCryptPassword)
				d.setPassword(bCryptPasswordEncoder.encode(d.getPassword()));
			entity = appuserRepo.save(d);
		} catch (Exception ex) {
			log.error("create failed (login={}, id={}, mustCryptPassword={}): {}",
					d != null ? d.getLogin() : null,
					d != null ? d.getId() : null,
					mustCryptPassword, ex.getMessage(), ex);
			return null;
		}
		return entity;
	}

	@Override
	public AppUser update(AppUser d, boolean mustCryptPassword) {
		AppUser c;
		try {
			if (mustCryptPassword)
				d.setPassword(bCryptPasswordEncoder.encode(d.getPassword()));
			c = appuserRepo.saveAndFlush(d);
		} catch (Exception ex) {
			log.error("update failed (login={}, id={}, mustCryptPassword={}): {}",
					d != null ? d.getLogin() : null,
					d != null ? d.getId() : null,
					mustCryptPassword, ex.getMessage(), ex);
			return null;
		}
		return c;
	}

	@Override
	public AppUser getOne(int id) {
		AppUser t;

		try {
			t = appuserRepo.findById(id).orElse(null);

		} catch (Exception ex) {
			log.error("getOne failed (id={}): {}", id, ex.getMessage(), ex);
			return null;
		}
		return t;
	}

	@Override
	public List<AppUser> getAll() {
		List<AppUser> lst;

		try {
			lst = appuserRepo.findAll();

		} catch (Exception ex) {
			log.error("getAll failed: {}", ex.getMessage(), ex);
			return Collections.emptyList();
		}
		return lst;
	}

	@Override
	public long getTotal() {
		long total;

		try {
			total = appuserRepo.count();
		} catch (Exception ex) {
			log.error("getTotal failed: {}", ex.getMessage(), ex);
			return 0;
		}
		return total;
	}

	@Override
	public boolean delete(int id) {
		try {
			appuserRepo.deleteById(id);
			return true;

		} catch (Exception ex) {
			log.error("delete failed (id={}): {}", id, ex.getMessage(), ex);
			return false;
		}
	}

	@Override
	public AppUser findUserByLogin(String login) {
		AppUser t;
		try {
			t = appuserRepo.findByLogin(login);

		} catch (Exception ex) {
			log.error("findUserByLogin failed (login={}): {}", login, ex.getMessage(), ex);
			return null;
		}
		return t;
	}

	@Override
	public Optional<AppUser> findUserById(int id) {
		return appuserRepo.findById(id);
	}

	@Override
	public AppUser findUserByPhoneContact(String contact) {
		return appuserRepo.findByPhoneContact(contact);
	}

	@Override
	public List<AppUser> findUsers() {
		return appuserRepo.findAll();
	}

	@Override
	public List<AppUser> findActiveUser() {
		return appuserRepo.findByIsActive(true);
	}

	@Override
	public void updateLastLogin(String userName) {
		AppUser u = this.findUserByLogin(userName);
		if (ObjectUtils.isNotEmpty(u)) {
			u.setLastLogin(new Date());
			this.create(u, false);
		}
	}

	@Override
	public void removeAllUserSites(Integer userId) {
		try {
			appUserHasSiteRepository.removeByAppUserId(userId);
		} catch (Exception e) {
			log.error("removeAllUserSites failed (userId={}): {}", userId, e.getMessage(), e);
			throw new OperationFailedException(e.getMessage());
		}

	}

	@Override
	public void addSitesToUser(Integer userId, List<Integer> siteIds) {
		try {
			removeAllUserSites(userId);
			siteIds.forEach(id -> {
				AppUserHasSite appUserHasSite = new AppUserHasSite();
				appUserHasSite.setAppUserId(userId);
				appUserHasSite.setSiteId(id);
				appUserHasSiteRepository.save(appUserHasSite);
			});
		} catch (Exception e) {
			log.error("addSitesToUser failed (userId={}, siteIds={}): {}", userId, siteIds, e.getMessage(), e);
			throw new OperationFailedException(e.getMessage());
		}

	}

	@Override
	public void replaceZoneSupervisorAssignments(Integer userId, org.itech.labSampleTracker.enums.UserRole role,
			List<Integer> zoneIds) {
		if (role == null || !role.isZoneSupervisor()) {
			throw new IllegalArgumentException("Rôle de superviseur de zone attendu : " + role);
		}
		removeAllUserRegions(userId);
		removeAllUserDistricts(userId);
		removeAllUserSites(userId);
		removeAllUserLabs(userId);
		removeAllUserCircuits(userId);
		if (role == org.itech.labSampleTracker.enums.UserRole.SUPERVISEUR_REGION) {
			addRegionsToUser(userId, zoneIds);
		} else {
			addDistrictsToUser(userId, zoneIds);
		}
	}

	@Override
	public void addRegionsToUser(Integer userId, List<Integer> regionIds) {
		try {
			removeAllUserRegions(userId);
			regionIds.forEach(id -> {
				AppUserHasRegion appUserHasRegion = new AppUserHasRegion();
				appUserHasRegion.setAppUserId(userId);
				appUserHasRegion.setRegionId(id);
				appUserHasRegionRepository.save(appUserHasRegion);
			});
		} catch (Exception e) {
			log.error("addRegionsToUser failed (userId={}, regionIds={}): {}", userId, regionIds, e.getMessage(), e);
			throw new OperationFailedException(e.getMessage());
		}

	}

	@Override
	public void removeAllUserRegions(Integer userId) {
		try {
			appUserHasRegionRepository.removeByAppUserId(userId);
		} catch (Exception e) {
			log.error("removeAllUserRegions failed (userId={}): {}", userId, e.getMessage(), e);
			throw new OperationFailedException(e.getMessage());
		}

	}

	@Override
	public void addLabsToUser(Integer userId, List<Integer> labIds) {
		try {
			removeAllUserLabs(userId);
			labIds.forEach(id -> {
				AppUserHasLab appUserHasLab = new AppUserHasLab();
				appUserHasLab.setAppUserId(userId);
				appUserHasLab.setLabId(id);
				appUserHasLabRepository.save(appUserHasLab);
			});
		} catch (Exception e) {
			log.error("addLabsToUser failed (userId={}, labIds={}): {}", userId, labIds, e.getMessage(), e);
			throw new OperationFailedException(e.getMessage());
		}

	}

	@Override
	public void removeAllUserLabs(Integer userId) {
		try {
			appUserHasLabRepository.removeByAppUserId(userId);
		} catch (Exception e) {
			log.error("removeAllUserLabs failed (userId={}): {}", userId, e.getMessage(), e);
			throw new OperationFailedException(e.getMessage());
		}

	}

	@Override
	public void addDistrictsToUser(Integer userId, List<Integer> districtIds) {
		try {
			removeAllUserDistricts(userId);
			districtIds.forEach(id -> {
				AppUserHasDistrict appUserHasDistrict = new AppUserHasDistrict();
				appUserHasDistrict.setAppUserId(userId);
				appUserHasDistrict.setDistrictId(id);
				appUserHasDistrictRepository.save(appUserHasDistrict);
			});
		} catch (Exception e) {
			log.error("addDistrictsToUser failed (userId={}, districtIds={}): {}", userId, districtIds, e.getMessage(), e);
			throw new OperationFailedException(e.getMessage());
		}

	}

	@Override
	public void removeAllUserDistricts(Integer userId) {
		try {
			appUserHasDistrictRepository.removeByAppUserId(userId);
		} catch (Exception e) {
			log.error("removeAllUserDistricts failed (userId={}): {}", userId, e.getMessage(), e);
			throw new OperationFailedException(e.getMessage());
		}

	}

	@Override
	public void addCircuitsToUser(Integer userId, List<Integer> circuitIds) {
		try {
			removeAllUserCircuits(userId);
			circuitIds.forEach(id -> {
				AppUserHasCircuit appUserHasCircuit = new AppUserHasCircuit();
				appUserHasCircuit.setAppUserId(userId);
				appUserHasCircuit.setCircuitId(id);
				appUserHasCircuitRepository.save(appUserHasCircuit);
			});
		} catch (Exception e) {
			log.error("addCircuitsToUser failed (userId={}, circuitIds={}): {}", userId, circuitIds, e.getMessage(), e);
			throw new OperationFailedException(e.getMessage());
		}

	}

	@Override
	public void removeAllUserCircuits(Integer userId) {
		try {
			appUserHasCircuitRepository.removeByAppUserId(userId);
		} catch (Exception e) {
			log.error("removeAllUserCircuits failed (userId={}): {}", userId, e.getMessage(), e);
			throw new OperationFailedException(e.getMessage());
		}

	}

	@Override
	public List<AppUser> findUsersIdName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<AppUserHasSite> getUserSites(Integer userId) {
		return appUserHasSiteRepository.findByAppUserId(userId);
	}

	@Override
	public List<AppUserHasRegion> getUserRegions(Integer userId) {
		return appUserHasRegionRepository.findByAppUserId(userId);

	}

	@Override
	public List<AppUserHasLab> getUserLabs(Integer userId) {
		return appUserHasLabRepository.findByAppUserId(userId);

	}

	@Override
	public List<AppUserHasDistrict> getUserDistricts(Integer userId) {
		return appUserHasDistrictRepository.findByAppUserId(userId);

	}

	@Override
	public List<AppUserHasCircuit> getUserCircuits(Integer userId) {
		return appUserHasCircuitRepository.findByAppUserId(userId);

	}

	@Override
	public List<Map<String, Object>> getRiderIdAndName() {
		String sql = "SELECT id,last_name,first_name FROM app_user WHERE user_type = 'CONVOYEUR'  ORDER BY last_name,first_name ";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("id", o[0]);
				map.put("name", o[1] + " " + o[2]);
				response.add(map);
			}
		} catch (Exception e) {
			log.error("getRiderIdAndName failed: {}", e.getMessage(), e);
		}
		return response;
	}

	@Override
	public String resolveUserName(Integer id) {
		String t = "";

		try {
			AppUser user = appuserRepo.findById(id).orElse(null);
			if (ObjectUtils.isNotEmpty(user))
				t = user.getLastName() + " " + user.getFirstName();
		} catch (Exception ex) {
			log.error("resolveUserName failed (id={}): {}", id, ex.getMessage(), ex);
			return "";
		}
		return t;
	}

}
