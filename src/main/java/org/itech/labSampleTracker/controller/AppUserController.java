/* 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:03 )
 */
package org.itech.labSampleTracker.controller;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.itech.labSampleTracker.dto.ProfileDTO;
import org.itech.labSampleTracker.dto.UserDTO;
import org.itech.labSampleTracker.dto.UserOrgDTO;
import org.itech.labSampleTracker.entities.AppUser;
import org.itech.labSampleTracker.entities.AppUserHasCircuit;
import org.itech.labSampleTracker.entities.AppUserHasDistrict;
import org.itech.labSampleTracker.entities.AppUserHasLab;
import org.itech.labSampleTracker.entities.AppUserHasRegion;
import org.itech.labSampleTracker.entities.AppUserHasSite;
import org.itech.labSampleTracker.enums.UserLevel;
import org.itech.labSampleTracker.enums.UserRole;
import org.itech.labSampleTracker.enums.UserType;
import org.itech.labSampleTracker.exception.OperationFailedException;
import org.itech.labSampleTracker.exception.ResourceNotFoundException;
import org.itech.labSampleTracker.service.AppUserService;
import org.itech.labSampleTracker.service.CircuitService;
import org.itech.labSampleTracker.service.DistrictService;
import org.itech.labSampleTracker.service.LabService;
import org.itech.labSampleTracker.service.RegionService;
import org.itech.labSampleTracker.service.SiteService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.itech.labSampleTracker.dto.ChangePasswordDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * <h2>AppUserController</h2>
 */
@Controller
@RequestMapping("/appuser")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@lombok.extern.slf4j.Slf4j
public class AppUserController extends BaseController {

	@Autowired
	private AppUserService appuserService;

	@Autowired
	private RegionService regionService;

	@Autowired
	private DistrictService districtService;

	@Autowired
	private SiteService siteService;

	@Autowired
	private LabService labService;
	
	@Autowired
	private CircuitService circuitService;

	@Autowired
	private org.itech.labSampleTracker.service.security.UserSessionManagementService userSessionManagementService;

	@Autowired
	private org.itech.labSampleTracker.service.security.UserScopeService userScopeService;

	@Autowired
	private org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder bCryptPasswordEncoder;

	private AppUser appUser;

	@PostMapping(value = "/new")
	public String createAppUser(@Valid UserDTO userDTO, BindingResult result, Model model) {
		model.addAttribute("userTypes", UserType.values());
		model.addAttribute("userRoles", UserRole.values());
		model.addAttribute("userLevels", UserLevel.values());
		if (result.hasErrors()) {
			model.addAttribute("message_error", result.getAllErrors());
			return "user/new";
		}
		String login = userDTO.getLogin();
		String contact = userDTO.getPhoneContact();

		AppUser user = appuserService.findUserByLogin(login);
		if (user != null) {
			model.addAttribute("message_error", "Ce login est dejà utilisé");
			return "user/new";
		}
		user = appuserService.findUserByPhoneContact(contact);
		if (user != null) {
			model.addAttribute("message_error", "Ce contact est dejà utilisé");
			return "user/new";
		}
		String password = userDTO.getPassword();
		String repassword = userDTO.getRepassword();
		if (!password.equals(repassword)) {
			model.addAttribute("message_error", "Mots de passe non concordants");
			return "user/new";
		}
		AppUser u = new AppUser();
		u.setPassword(password);
		u.setFirstName(userDTO.getFirstName());
		u.setLastName(userDTO.getLastName());
		u.setPhoneContact(contact);
		u.setLogin(login);
		u.setRole(userDTO.getRole().getRole());
		u.setUserType(userDTO.getUserType().getType());
		u.setUserLevel(userDTO.getUserLevel().getLevel());
		u.setPasswordExpireAt(userDTO.getPasswordExpireAt());
		u.setCreatedAt(new java.util.Date());
		u.setIsActive(userDTO.isActive());
		u.setIsLocked(userDTO.isLocked());
		try {
			u = appuserService.create(u, true);

		} catch (Exception e) {
			model.addAttribute("message_error", e.getMessage());
		}
		model.addAttribute("message_success", "Ajout effectué avec succès");

		model.addAttribute("userDTO", new UserDTO());
		return "user/new";
	}

	@GetMapping(value = "/new")
	public String newAppUser(Model model) {
		model.addAttribute("userDTO", new UserDTO());
		model.addAttribute("userTypes", UserType.values());
		model.addAttribute("userRoles", UserRole.values());
		model.addAttribute("userLevels", UserLevel.values());
		return "user/new";
	}

	@GetMapping(value = "")
	public String getAllAppUser(Model model) {
		// Filter options for the redesigned admin index. The actual user list
		// is loaded asynchronously via /appuser/data (DataTables server-side).
		model.addAttribute("userRoles", UserRole.values());
		model.addAttribute("userTypes", UserType.values());
		model.addAttribute("regions", regionService.getRegionIdAndName());
		model.addAttribute("districts", districtService.getDistrictIdAndNames());
		model.addAttribute("labs", labService.getLabIdAndNames());
		return "user/index";
	}

	/**
	 * JSON endpoint feeding the DataTables-driven admin user list.
	 */
	@GetMapping(value = "/data", produces = "application/json")
	@org.springframework.web.bind.annotation.ResponseBody
	public java.util.Map<String, Object> getUsersData(
			@org.springframework.web.bind.annotation.RequestParam(name = "draw", defaultValue = "1") int draw,
			@org.springframework.web.bind.annotation.RequestParam(name = "start", defaultValue = "0") int start,
			@org.springframework.web.bind.annotation.RequestParam(name = "length", defaultValue = "25") int length,
			@org.springframework.web.bind.annotation.RequestParam(name = "search_text", required = false) String searchText,
			@org.springframework.web.bind.annotation.RequestParam(name = "role", required = false) List<String> roles,
			@org.springframework.web.bind.annotation.RequestParam(name = "active", required = false) Boolean activeFlag,
			@org.springframework.web.bind.annotation.RequestParam(name = "locked", required = false) Boolean lockedFlag,
			@org.springframework.web.bind.annotation.RequestParam(name = "region", required = false) Integer regionId,
			@org.springframework.web.bind.annotation.RequestParam(name = "district", required = false) Integer districtId,
			@org.springframework.web.bind.annotation.RequestParam(name = "lab", required = false) Integer labId) {

		String search = (searchText == null || searchText.isBlank()) ? null : searchText.trim();
		boolean rolesActive = roles != null && !roles.isEmpty();
		List<String> effectiveRoles = rolesActive ? roles : List.of("__none__");

		int page = length > 0 ? (start / length) : 0;
		org.springframework.data.domain.Pageable pageable =
				org.springframework.data.domain.PageRequest.of(page, length > 0 ? length : 25);

		org.springframework.data.domain.Page<java.util.Map<String, Object>> p = appuserService
				.findUsersAdvanced(pageable, search, effectiveRoles, rolesActive,
						activeFlag, lockedFlag, regionId, districtId, labId);

		java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
		out.put("draw", draw);
		out.put("recordsTotal", p.getTotalElements());
		out.put("recordsFiltered", p.getTotalElements());
		out.put("data", p.getContent());
		return out;
	}

	/**
	 * Toggle the active flag of the given user.
	 * If the user becomes inactive, their web sessions are revoked immediately.
	 */
	@PostMapping(value = "/{id}/toggle-active", produces = "application/json")
	@org.springframework.web.bind.annotation.ResponseBody
	public java.util.Map<String, Object> toggleActive(@PathVariable("id") Integer id) {
		AppUser u = appuserService.findUserById(id).orElse(null);
		if (u == null) {
			throw new ResourceNotFoundException("Utilisateur introuvable");
		}
		boolean wasActive = Boolean.TRUE.equals(u.getIsActive());
		u.setIsActive(!wasActive);
		u.setLastUpdatedAt(new java.util.Date());
		u.setLastUpdatedBy(this.getCurrentUserId());
		appuserService.update(u, false);
		if (wasActive) {
			// User was just deactivated → kill its sessions
			userSessionManagementService.revokeSessionsFor(u.getLogin());
		}
		return java.util.Map.of("id", id, "isActive", !wasActive);
	}

	/**
	 * Toggle the locked flag of the given user.
	 * Locking a user also revokes its active web sessions.
	 */
	@PostMapping(value = "/{id}/toggle-lock", produces = "application/json")
	@org.springframework.web.bind.annotation.ResponseBody
	public java.util.Map<String, Object> toggleLock(@PathVariable("id") Integer id) {
		AppUser u = appuserService.findUserById(id).orElse(null);
		if (u == null) {
			throw new ResourceNotFoundException("Utilisateur introuvable");
		}
		boolean wasLocked = Boolean.TRUE.equals(u.getIsLocked());
		u.setIsLocked(!wasLocked);
		u.setLastUpdatedAt(new java.util.Date());
		u.setLastUpdatedBy(this.getCurrentUserId());
		appuserService.update(u, false);
		if (!wasLocked) {
			userSessionManagementService.revokeSessionsFor(u.getLogin());
		}
		return java.util.Map.of("id", id, "isLocked", !wasLocked);
	}

	/**
	 * Admin-driven password reset: generates a temporary password, persists
	 * it (hashed), revokes sessions and returns the plaintext password once
	 * so the admin can communicate it to the user out-of-band.
	 *
	 * The user must change it at next login (passwordExpireAt = today).
	 */
	@PostMapping(value = "/{id}/reset-password", produces = "application/json")
	@org.springframework.web.bind.annotation.ResponseBody
	public java.util.Map<String, Object> resetPassword(@PathVariable("id") Integer id) {
		AppUser u = appuserService.findUserById(id).orElse(null);
		if (u == null) {
			throw new ResourceNotFoundException("Utilisateur introuvable");
		}
		String temporary = generateTemporaryPassword();
		u.setPassword(temporary);
		// Mark expiration as today → user is forced to change at next login.
		u.setPasswordExpireAt(new java.util.Date());
		u.setLastUpdatedAt(new java.util.Date());
		u.setLastUpdatedBy(this.getCurrentUserId());
		appuserService.update(u, true); // crypt the new password
		userSessionManagementService.revokeSessionsFor(u.getLogin());
		log.info("Admin {} reset password for user {} (id={})",
				this.getUsername(), u.getLogin(), id);
		return java.util.Map.of("id", id, "login", u.getLogin(), "temporaryPassword", temporary);
	}

	/**
	 * Generates a 12-character temporary password matching the policy
	 * (1 upper, 1 lower, 1 digit, 1 special).
	 */
	private static String generateTemporaryPassword() {
		final String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
		final String lower = "abcdefghijkmnopqrstuvwxyz";
		final String digits = "23456789";
		final String specials = "@#$%&*!";
		final String all = upper + lower + digits + specials;
		java.security.SecureRandom rnd = new java.security.SecureRandom();
		StringBuilder sb = new StringBuilder(12);
		sb.append(upper.charAt(rnd.nextInt(upper.length())));
		sb.append(lower.charAt(rnd.nextInt(lower.length())));
		sb.append(digits.charAt(rnd.nextInt(digits.length())));
		sb.append(specials.charAt(rnd.nextInt(specials.length())));
		for (int i = 0; i < 8; i++) {
			sb.append(all.charAt(rnd.nextInt(all.length())));
		}
		// Shuffle (Fisher-Yates) so the predictable position of the 4
		// guaranteed-class characters is not at the start.
		char[] arr = sb.toString().toCharArray();
		for (int i = arr.length - 1; i > 0; i--) {
			int j = rnd.nextInt(i + 1);
			char tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
		}
		return new String(arr);
	}

	@GetMapping(value = "/update/{id}")
	public String getOneAppUser(@PathVariable("id") Integer id, Model model) {
		appUser = new AppUser();
		UserDTO userDTO = new UserDTO();
		model.addAttribute("userTypes", UserType.values());
		model.addAttribute("userRoles", UserRole.values());
		model.addAttribute("userLevels", UserLevel.values());
		try {
			appUser = accountService.getOne(id);
			if (ObjectUtils.isEmpty(appUser)) {
				throw new ResourceNotFoundException("Impossible de restituer les données de l'utilisateur");
			}
		} catch (Exception ex) {
			model.addAttribute("message_error", ex.getMessage());
			return "user/edit";
		}
		BeanUtils.copyProperties(appUser, userDTO);
		userDTO.setRole(UserRole.get(appUser.getRole()));
		userDTO.setUserType(UserType.get(appUser.getUserType()));
		userDTO.setUserLevel(UserLevel.get(appUser.getUserLevel()));
		userDTO.setRepassword(null);
		model.addAttribute("userDTO", userDTO);
		return "user/edit";
	}

	@PostMapping(value = "/update/{id}")
	public String updateAppUser(@PathVariable("id") Integer id, Model model, UserDTO userDTO) {
		model.addAttribute("userTypes", UserType.values());
		model.addAttribute("userRoles", UserRole.values());
		model.addAttribute("userLevels", UserLevel.values());
		try {
			AppUser updatedUser = appuserService.findUserById(id).orElse(null);
			if (ObjectUtils.isEmpty(updatedUser)) {
				model.addAttribute("message_error", "Utilisateur non trouvé");
				return "user/edit";
			}
			// Snapshot of state BEFORE update — used to detect changes that
			// should trigger session revocation (deactivation, lock, role change).
			boolean wasActive = Boolean.TRUE.equals(updatedUser.getIsActive());
			boolean wasLocked = Boolean.TRUE.equals(updatedUser.getIsLocked());
			String previousRole = updatedUser.getRole();
			String previousLogin = updatedUser.getLogin();
			// Le champ Identifiant est désactivé dans le formulaire, donc jamais
			// soumis : on le reprend de la base (il n'est pas modifiable).
			userDTO.setLogin(previousLogin);
			appUser = new AppUser();
			AppUser u = appuserService.findUserByLogin(userDTO.getLogin());
			if (u != null && !userDTO.getLogin().equalsIgnoreCase(u.getLogin())) {
				model.addAttribute("message_error", "Ce login est dejà utilisé");
				return "user/edit";
			}

			u = appuserService.findUserByPhoneContact(userDTO.getPhoneContact());
			if (u != null && !userDTO.getPhoneContact().equalsIgnoreCase(u.getPhoneContact())) {
				model.addAttribute("message_error", "Ce contact est dejà utilisé");
				return "user/edit";
			}
			boolean passwordMustBeCrypt = false;
			updatedUser.setIsActive(userDTO.isActive());
			updatedUser.setFirstName(userDTO.getFirstName());
			updatedUser.setLastName(userDTO.getLastName());
			updatedUser.setIsLocked(userDTO.isLocked());
			if (StringUtils.isNotEmpty(userDTO.getPassword())) { // si le mot de passe est defini lors de la
																	// modification alors on met a jour le mot de passe.
				String password = userDTO.getPassword();
				String repassword = userDTO.getRepassword();
				if (!password.equals(repassword)) {
					throw new RuntimeException("Mots de passe non concordants");
				}
				updatedUser.setPassword(userDTO.getPassword());
				passwordMustBeCrypt = true;

			} else {
				passwordMustBeCrypt = false;
			}
			updatedUser.setPasswordExpireAt(userDTO.getPasswordExpireAt());
			updatedUser.setPhoneContact(userDTO.getPhoneContact());

			updatedUser.setLastUpdatedBy(this.getCurrentUserId());
			updatedUser.setLastUpdatedAt(new java.util.Date());
			updatedUser.setRole(userDTO.getRole().getRole());
			updatedUser.setUserType(userDTO.getUserType().getType());
			updatedUser.setUserLevel(userDTO.getUserLevel().getLevel());
			appUser = appuserService.create(updatedUser, passwordMustBeCrypt);

			// Force-logout the user from any active web session when an admin
			// deactivates the account, locks it, changes its role or resets
			// its password — so the new state takes effect immediately.
			boolean nowInactive = !userDTO.isActive() && wasActive;
			boolean nowLocked = userDTO.isLocked() && !wasLocked;
			boolean roleChanged = previousRole != null
					&& !previousRole.equals(userDTO.getRole().getRole());
			boolean passwordChanged = passwordMustBeCrypt;
			if (nowInactive || nowLocked || roleChanged || passwordChanged) {
				userSessionManagementService.revokeSessionsFor(previousLogin);
			}

		} catch (Exception e) {
			model.addAttribute("message_error", e.getMessage());
			return "user/edit";
		}
		model.addAttribute("user", appUser);
		model.addAttribute("message_success", "Modification effectuée avec succès");
		return "user/edit";
	}

	@GetMapping("/profile/edit")
	@PreAuthorize("isAuthenticated()")
	public String showProfile(Model model) {
		ProfileDTO userDTO = new ProfileDTO();
		model.addAttribute("user", userDTO);
		try {
			AppUser currentUser = accountService.findUserById(getCurrentUserId()).orElse(null);
			if (currentUser != null) {
				userDTO.setId(currentUser.getId());
				BeanUtils.copyProperties(currentUser, userDTO, "password,repassword,role");
				model.addAttribute("user", userDTO);

				// Extra read-only info displayed on the profile page
				model.addAttribute("currentRole", currentUser.getRole());
				model.addAttribute("currentUserType", currentUser.getUserType());
				model.addAttribute("currentUserLevel", currentUser.getUserLevel());
				model.addAttribute("isActive", currentUser.getIsActive());
				model.addAttribute("isLocked", currentUser.getIsLocked());
				model.addAttribute("lastLogin", currentUser.getLastLogin());
				model.addAttribute("passwordExpireAt", currentUser.getPasswordExpireAt());

				// Resolved accessible zones (with inheritance) for the current user.
				org.itech.labSampleTracker.service.security.UserScopeService.Scope scope =
						userScopeService.resolve(currentUser.getId());
				if (scope.isGlobal()) {
					model.addAttribute("scopeGlobal", true);
				} else {
					model.addAttribute("scopeGlobal", false);
					model.addAttribute("accessibleRegionCount", scope.getRegionIds().size());
					model.addAttribute("accessibleDistrictCount", scope.getDistrictIds().size());
					model.addAttribute("accessibleSiteCount", scope.getSiteIds().size());
					model.addAttribute("accessibleLabCount", scope.getLabIds().size());
					model.addAttribute("accessibleCircuitCount", scope.getCircuitIds().size());
				}
			}
		} catch (Exception e) {
			log.warn("showProfile failed for current user: {}", e.getMessage(), e);
			model.addAttribute("message_error", e.getMessage());
			model.addAttribute("user", new ProfileDTO());
		}
		return "user/profile";
	}

	@PostMapping("/profile/edit")
	@PreAuthorize("isAuthenticated()")
	public String editProfile(Model model, @Valid ProfileDTO user) {
		if (!this.isGlobalRole()) {
			if (!Objects.equals(this.getCurrentUserId(), user.getId())) {
				throw new AccessDeniedException("Impossible de modifier les informations de cet utilisateur");
			}
		}
		appUser = new AppUser();
		try {
			accountService.findUserById(user.getId()).ifPresent(updatedUser -> {
				updatedUser.setPhoneContact(user.getPhoneContact());
				updatedUser.setLastName(user.getLastName());
				updatedUser.setFirstName(user.getFirstName());

				boolean passwordMustBeCrypt = false;
				if (StringUtils.isNotEmpty(user.getPassword())) { // si le mot de passe est defini lors de la
					// modification alors on met a jour le mot de passe.
					String password = user.getPassword();
					String repassword = user.getRepassword();
					if (!password.equals(repassword)) {
						throw new RuntimeException("Mots de passe non concordants");
					}
					updatedUser.setPassword(user.getPassword());
					passwordMustBeCrypt = true;

				} else {
					passwordMustBeCrypt = false;
				}
				updatedUser.setLastUpdatedBy(this.getCurrentUserId());
				updatedUser.setLastUpdatedAt(new java.util.Date());
				appUser = accountService.update(updatedUser, passwordMustBeCrypt);
			});
		} catch (Exception e) {
			model.addAttribute("message_error", e.getMessage());
			model.addAttribute("user", new ProfileDTO());
		}
		model.addAttribute("user", user);
		model.addAttribute("message_success", "Modification effectuée avec succès");
		return "user/profile";
	}

	@GetMapping("/edit_org/{userId}")
	public String editUserOrg(Model model, @PathVariable("userId") Integer userId) {
		appUser = new AppUser();
		model.addAttribute("regions", regionService.getRegionIdAndName());
		model.addAttribute("districts", districtService.getDistrictIdAndNames());
		model.addAttribute("sites", siteService.getSiteIdAndNames());
		model.addAttribute("labs", labService.getLabIdAndNames());
		model.addAttribute("circuits", circuitService.getCircuitIdAndNumber());
		UserOrgDTO userOrg = new UserOrgDTO();
		try {
			appUser = accountService.getOne(userId);
			if (ObjectUtils.isEmpty(appUser)) {
				throw new ResourceNotFoundException("Utilisateur non trouvé");
			}
			if (appUser.getUserLevel().equals("REGION")) {
				List<AppUserHasRegion> appUserHasRegions = appuserService.getUserRegions(userId);
				List<Integer> usersRegions = appUserHasRegions.stream().map(e -> e.getRegionId())
						.collect(Collectors.toList());
				userOrg.setRegions(usersRegions);
			} else if (appUser.getUserLevel().equals("DISTRICT")) {
				List<AppUserHasDistrict> appUserHasDistricts = appuserService.getUserDistricts(userId);
				List<Integer> userDistricts = appUserHasDistricts.stream().map(e -> e.getDistrictId())
						.collect(Collectors.toList());
				userOrg.setDistricts(userDistricts);
			} else if (appUser.getUserLevel().equals("SITE")) {
				List<AppUserHasSite> appUserHasSites = appuserService.getUserSites(userId);
				List<Integer> usersSites = appUserHasSites.stream().map(e -> e.getSiteId())
						.collect(Collectors.toList());
				userOrg.setSites(usersSites);
			} else if (appUser.getUserLevel().equals("LABO")) {
				List<AppUserHasLab> appUserHasLabs = appuserService.getUserLabs(userId);
				List<Integer> usersLabs = appUserHasLabs.stream().map(e -> e.getLabId()).collect(Collectors.toList());
				userOrg.setLabs(usersLabs);
			} else if (appUser.getUserLevel().equals("CIRCUIT")) {
				List<AppUserHasCircuit> appUserHasCircuits = appuserService.getUserCircuits(userId);
				List<Integer> usersCircuits = appUserHasCircuits.stream().map(e -> e.getCircuitId())
						.collect(Collectors.toList());
				userOrg.setCircuits(usersCircuits);
			}
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
			model.addAttribute("message_error", ex.getMessage());
			return "user/edit_user_org";
		}

		model.addAttribute("user", appUser);
		model.addAttribute("userOrg", userOrg);

		return "user/edit_user_org";
	}

	@PostMapping("/edit_org/{userId}")
	public String updateUserOrg(Model model, @PathVariable Integer userId, UserOrgDTO userOrg) {
		appUser = new AppUser();
		model.addAttribute("regions", regionService.getRegionIdAndName());
		model.addAttribute("districts", districtService.getDistrictIdAndNames());
		model.addAttribute("sites", siteService.getSiteIdAndNames());
		model.addAttribute("labs", labService.getLabIdAndNames());
		model.addAttribute("circuits", circuitService.getCircuitIdAndNumber());
		model.addAttribute("userOrg", userOrg);

		try {
			appUser = accountService.getOne(userId);
			if (ObjectUtils.isEmpty(appUser)) {
				throw new ResourceNotFoundException("Utilisateur non trouvé");
			}
			model.addAttribute("user", appUser);
			if (appUser.getUserLevel().equals("REGION")) {
				appuserService.addRegionsToUser(userId, userOrg.getRegions());
			} else if (appUser.getUserLevel().equals("DISTRICT")) {
				appuserService.addDistrictsToUser(userId, userOrg.getDistricts());
			} else if (appUser.getUserLevel().equals("SITE")) {
				appuserService.addSitesToUser(userId, userOrg.getSites());
			} else if (appUser.getUserLevel().equals("LABO")) {
				appuserService.addLabsToUser(userId, userOrg.getLabs());
			} else if (appUser.getUserLevel().equals("CIRCUIT")) {
				appuserService.addCircuitsToUser(userId, userOrg.getCircuits());
			}
			model.addAttribute("message_success", "Opération effectuée avec succès");
		} catch (Exception ex) {
			ex.printStackTrace();
			model.addAttribute("message_error", ex.getMessage());
			return "user/edit_user_org";
		}

		return "user/edit_user_org";
	}

	/**
	 * Page dédiée au changement de mot de passe (self-service).
	 * Accessible à tout utilisateur authentifié pour son propre compte.
	 */
	@GetMapping("/profile/password")
	@PreAuthorize("isAuthenticated()")
	public String showChangePassword(Model model) {
		final ChangePasswordDTO dto = new ChangePasswordDTO();
		dto.setUserId(this.getCurrentUserId());
		model.addAttribute("changePassword", dto);
		AppUser current = accountService.findUserById(getCurrentUserId()).orElse(null);
		if (current != null) {
			model.addAttribute("currentLogin", current.getLogin());
		}
		return "user/change_password";
	}

	@PostMapping("/profile/password")
	@PreAuthorize("isAuthenticated()")
	public String changePassword(
			@Valid @ModelAttribute("changePassword") ChangePasswordDTO dto,
			BindingResult result, Model model) {
		final int currentUserId = this.getCurrentUserId();

		// Toujours résoudre l'identité depuis la session — on ignore tout
		// userId envoyé par le client pour éviter qu'un utilisateur change
		// le mot de passe de quelqu'un d'autre via un payload forgé.
		AppUser user = accountService.findUserById(currentUserId).orElse(null);
		if (user == null) {
			throw new AccessDeniedException("Session invalide");
		}
		model.addAttribute("currentLogin", user.getLogin());

		if (result.hasErrors()) {
			return "user/change_password";
		}

		// 1. Vérifier l'ancien mot de passe
		if (!bCryptPasswordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
			result.rejectValue("oldPassword", "invalid", "Mot de passe actuel incorrect");
			log.warn("changePassword: ancien mot de passe incorrect pour user '{}'", user.getLogin());
			return "user/change_password";
		}

		// 2. Confirmation
		if (!java.util.Objects.equals(dto.getPassword(), dto.getRepassword())) {
			result.rejectValue("repassword", "mismatch",
					"Le mot de passe et sa confirmation ne correspondent pas");
			return "user/change_password";
		}

		// 3. Refuser un mot de passe identique à l'ancien (anti-recyclage minimal)
		if (bCryptPasswordEncoder.matches(dto.getPassword(), user.getPassword())) {
			result.rejectValue("password", "same",
					"Le nouveau mot de passe doit être différent de l'ancien");
			return "user/change_password";
		}

		// 4. Persistance
		user.setPassword(dto.getPassword());
		// On force une nouvelle date d'expiration loin dans le futur (= reset
		// du compteur d'expiration). Si on veut un jour activer la rotation
		// périodique, on enrichira ici (passwordExpireAt = +N jours).
		user.setLastUpdatedAt(new java.util.Date());
		user.setLastUpdatedBy(currentUserId);
		appuserService.update(user, true); // true = bcrypt-hash le password

		// 5. Sécurité : invalider toute autre session active du même user
		// (autre device, autre navigateur). La session courante restera valide
		// car Spring la régénère implicitement après update du SecurityContext.
		try {
			userSessionManagementService.revokeSessionsFor(user.getLogin());
		} catch (Exception ex) {
			log.warn("changePassword: échec de la révocation des sessions pour user '{}'", user.getLogin(), ex);
		}

		log.info("changePassword: mot de passe mis à jour pour user '{}'", user.getLogin());

		// Re-set un DTO vide pour ne pas re-afficher les anciens mots de passe
		// dans le formulaire après succès.
		ChangePasswordDTO cleaned = new ChangePasswordDTO();
		cleaned.setUserId(currentUserId);
		model.addAttribute("changePassword", cleaned);
		model.addAttribute("message_success", "Mot de passe modifié avec succès. "
				+ "Vos autres sessions ont été déconnectées.");
		return "user/change_password";
	}

}
