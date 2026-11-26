package org.itech.labSampleTracker.controller;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.itech.labSampleTracker.entities.AppUser;
import org.itech.labSampleTracker.service.AppUserService;
import org.itech.labSampleTracker.service.SiteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 
 * @author Pascal
 *
 */
@Controller
public class BaseController {

	@Autowired
	protected AppUserService accountService;

	@Autowired
	protected SiteService siteService;

	@Autowired
	private MessageSource messageSource;

	protected int getCurrentUserId() {
		return this.accountService.findUserByLogin(this.getUsername()).getId();
	}

	protected Map<String, Object> getCurrentUserInfos() {
		return this.userInfos(this.getCurrentUserId());
	}

	protected Map<String, Object> userInfos(Integer id) {
		AppUser user = this.accountService.findUserById(id).orElse(null);
		Map<String, Object> userMap = new HashMap<String, Object>();
		if (ObjectUtils.isNotEmpty(user)) {
			userMap.put("userId", user.getId());
			userMap.put("userLogin", user.getLogin());
			userMap.put("userPhone", user.getPhoneContact());
			userMap.put("userFirstName", user.getFirstName());
			userMap.put("userLastName", user.getLastName());
			userMap.put("userType", user.getUserType());
			userMap.put("lastLogin", user.getLastLogin());
			userMap.put("roles", user.getAuthorities());
			ArrayList<Map<String, Object>> sites = new ArrayList<>();
			// user.getListOfSite().forEach(s -> {
			// Map<String, Object> map = new HashMap<>();
			// map.put("id", s.getId());
			// map.put("name", s.getName());
			// sites.add(map);
			// });
			userMap.put("sites", sites);
		}
		return userMap;
	}

	protected List<Order> getSortOrder(String[] sorts) {
		List<Order> orders = new ArrayList<>();
		if (sorts[0].contains(",")) {
			for (String sortOrder : sorts) {
				String[] sort = sortOrder.split(",");
				orders.add(new Order(getSortDirection(sort[1]), sort[0]));
			}
		} else {
			orders.add(new Order(getSortDirection(sorts[1]), sorts[0]));
		}

		return orders;
	}

	private Sort.Direction getSortDirection(String direction) {
		if (direction.equals("asc")) {
			return Sort.Direction.ASC;
		} else if (direction.equals("desc")) {
			return Sort.Direction.DESC;
		}

		return Sort.Direction.ASC;
	}

	protected AppUser getUserById(Integer id) {
		return ObjectUtils.isEmpty(id) ? new AppUser() : accountService.getOne(id);
	}

	protected String getUserLoginById(Integer id) {
		return ObjectUtils.isEmpty(getUserById(id)) ? "--" : getUserById(id).getLogin();
	}

	protected String getUserFirstNameById(Integer id) {
		return ObjectUtils.isEmpty(getUserById(id)) ? "--" : getUserById(id).getFirstName();
	}

	protected String getUserLastNameById(Integer id) {
		return ObjectUtils.isEmpty(getUserById(id)) ? "--" : getUserById(id).getLastName();
	}

	/***
	 * Retrieve translated message from Message ressources
	 * 
	 * @param key    messageKey to translate
	 * @param params
	 * @return String: the translated message
	 */
	protected String trans(String key, Object[] params) {
		return messageSource.getMessage(key, params, key, LocaleContextHolder.getLocale());
	}

	/***
	 * Retrieve translated message from Message ressources
	 * 
	 * @param key messageKey to translate
	 * @return String: the translated message
	 */
	protected String trans(String key) {
		return this.trans(key, null);
	}

	/**
	 * check if a current user has one of admin or support roles
	 * 
	 * @return boolean
	 */
	protected Boolean isAdminOrSupport() {
		List<String> userRoles = this.getUserRoles();
		return userRoles.contains("ADMIN") || userRoles.contains("SUPPORT");
	}

	/**
	 * Peut modifier le profil d'un autre utilisateur : administrateurs
	 * seulement (le MANAGER consulte l'administration sans rien modifier).
	 */
	protected Boolean isGlobalRole() {
		List<String> userRoles = this.getUserRoles();
		return userRoles.contains("ROLE_ADMIN") || userRoles.contains("ROLE_SUPER_ADMIN");
	}

	/**
	 * @return String comma separated IDs for the connected user
	 */
	protected String getUserSiteIDs() {
		List<Integer> userSiteIds = siteService.getSitesIdsByUser(this.getCurrentUserId());
		String siteIds = userSiteIds.stream().map(e -> e.toString()).collect(Collectors.joining(","));
		// si c'est un admin ou support alors tous les sites
		String roles = this.getCurrentUserInfos().get("roles").toString();
		if ((roles.contains("ADMIN") || roles.contains("SUPPORT") || roles.contains("MANAGER"))) {
			siteIds = null;
		}
		return siteIds;
	}

	/**
	 * check if a current user own the site
	 * 
	 * @return boolean
	 */
	protected Boolean userHasSite(Integer siteId) {
		String roles = this.getCurrentUserInfos().get("roles").toString();
		if ((roles.contains("ADMIN") || roles.contains("SUPPORT") || roles.contains("MANAGER"))) {
			return true;
		}
		return this.isNullOrZero(siteId) ? true
				: (getUserSiteIDs().isEmpty() ? false : getUserSiteIDs().contains(siteId + ""));

	}

	public String getUsername() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}

	public List<String> getUserRoles() {
		return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
				.map(s -> s.getAuthority()).collect(Collectors.toList());
	}

	public boolean isNullOrZero(Integer i) {
		return i == null || i.intValue() == 0;
	}

	public String getUserAgent(HttpServletRequest request) {
		return (request != null) ? request.getHeader("User-Agent") : "";
	}

	public LocalDate toLocalDate(Date date) {
		return date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
	}
}
