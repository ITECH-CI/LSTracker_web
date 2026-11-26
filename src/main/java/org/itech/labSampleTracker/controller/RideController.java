/* 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 */
package org.itech.labSampleTracker.controller;

import org.itech.labSampleTracker.security.AdminRead;
import org.itech.labSampleTracker.security.AdminWrite;
import org.itech.labSampleTracker.entities.Ride;
import org.itech.labSampleTracker.service.RideService;


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
* <h2>RideController</h2>
*/
@Controller
@RequestMapping("/ride")
// Ancien contrôleur généré, sans écran : réservé aux administrateurs.
@AdminWrite
public class RideController {


	@Autowired
	private RideService rideService;

	@PostMapping(value = "")
	public String createRide(@Valid Ride model) {

   		 Ride data = rideService.create(model);
    		if (data != null) {
    			return "";
  			  } else {
    			return "";
   			 }
    }

    @GetMapping(value = "")
    public String getAllRide(Model model) {
        List<Ride> rideList = rideService.getAll();

		model.addAttribute("Rides",rideList);

        return "";
    }

        @GetMapping(value = "/{id}")
    public String getOneRide(@PathVariable("id") Integer id, Model model) {

            Ride e = rideService.getOne(id);
            if (e == null) {
            	return "";
            }
			model.addAttribute("ride",e);
            return "";
    }



}
