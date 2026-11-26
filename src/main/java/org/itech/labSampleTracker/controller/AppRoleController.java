/* 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:03 )
 */
package org.itech.labSampleTracker.controller;

import org.itech.labSampleTracker.security.AdminRead;
import org.itech.labSampleTracker.security.AdminWrite;
import org.itech.labSampleTracker.entities.AppRole;
import org.itech.labSampleTracker.service.AppRoleService;


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
* <h2>AppRoleController</h2>
*/
@Controller
@RequestMapping("/approle")
// Ancien contrôleur généré, sans écran : réservé aux administrateurs.
@AdminWrite
public class AppRoleController {


	@Autowired
	private AppRoleService approleService;

	@PostMapping(value = "")
	public String createAppRole(@Valid AppRole model) {

   		 AppRole data = approleService.create(model);
    		if (data != null) {
    			return "";
  			  } else {
    			return "";
   			 }
    }

    @GetMapping(value = "")
    public String getAllAppRole(Model model) {
        List<AppRole> approleList = approleService.getAll();

		model.addAttribute("AppRoles",approleList);

        return "";
    }

        @GetMapping(value = "/{id}")
    public String getOneAppRole(@PathVariable("id") Integer id, Model model) {

            AppRole e = approleService.getOne(id);
            if (e == null) {
            	return "";
            }
			model.addAttribute("approle",e);
            return "";
    }



}
