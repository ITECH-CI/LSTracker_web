/* 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:03 )
 */
package org.itech.labSampleTracker.controller;

import org.itech.labSampleTracker.security.AdminRead;
import org.itech.labSampleTracker.security.AdminWrite;
import org.itech.labSampleTracker.entities.CircuitSite;
import org.itech.labSampleTracker.service.CircuitSiteService;


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
* <h2>CircuitSiteController</h2>
*/
@Controller
@RequestMapping("/circuitsite")
// Ancien contrôleur généré, sans écran : réservé aux administrateurs.
@AdminWrite
public class CircuitSiteController {


	@Autowired
	private CircuitSiteService circuitsiteService;

	@PostMapping(value = "")
	public String createCircuitSite(@Valid CircuitSite model) {

   		 CircuitSite data = circuitsiteService.create(model);
    		if (data != null) {
    			return "";
  			  } else {
    			return "";
   			 }
    }

    @GetMapping(value = "")
    public String getAllCircuitSite(Model model) {
        List<CircuitSite> circuitsiteList = circuitsiteService.getAll();

		model.addAttribute("CircuitSites",circuitsiteList);

        return "";
    }

        @GetMapping(value = "/{id}")
    public String getOneCircuitSite(@PathVariable("id") Integer id, Model model) {

            CircuitSite e = circuitsiteService.getOne(id);
            if (e == null) {
            	return "";
            }
			model.addAttribute("circuitsite",e);
            return "";
    }



}
