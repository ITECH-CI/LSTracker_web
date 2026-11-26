/* 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:03 )
 */
package org.itech.labSampleTracker.controller;

import org.itech.labSampleTracker.security.AdminRead;
import org.itech.labSampleTracker.security.AdminWrite;
import org.itech.labSampleTracker.entities.AppUserHasRole;
import org.itech.labSampleTracker.service.AppUserHasRoleService;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import jakarta.validation.Valid;
import java.util.List;



/**
* <h2>AppUserHasRoleController</h2>
*/
@Controller
@RequestMapping("/appuserhasrole")
// Ancien contrôleur généré, sans écran : réservé aux administrateurs.
@AdminWrite
public class AppUserHasRoleController {

	@Autowired
	private AppUserHasRoleService appuserhasroleService;

	@PostMapping(value = "")
	public String createAppUserHasRole(@Valid AppUserHasRole model) {

   		 AppUserHasRole data = appuserhasroleService.create(model);
    		if (data != null) {
    			return "";
  			  } else {
    			return "";
   			 }
    }

    @GetMapping(value = "")
    public String getAllAppUserHasRole(Model model) {
        List<AppUserHasRole> appuserhasroleList = appuserhasroleService.getAll();

		model.addAttribute("AppUserHasRoles",appuserhasroleList);

        return "";
    }

        @GetMapping(value = "/{id}")
    public String getOneAppUserHasRole(@PathVariable("id") Integer id, Model model) {

            AppUserHasRole e = appuserhasroleService.getOne(id);
            if (e == null) {
            	return "";
            }
			model.addAttribute("appuserhasrole",e);
            return "";
    }



}
