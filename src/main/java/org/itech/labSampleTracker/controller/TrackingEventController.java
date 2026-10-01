/* 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 */
package org.itech.labSampleTracker.controller;

import org.itech.labSampleTracker.security.AdminRead;
import org.itech.labSampleTracker.security.AdminWrite;
import org.itech.labSampleTracker.entities.TrackingEvent;
import org.itech.labSampleTracker.service.TrackingEventService;


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
* <h2>TrackingEventController</h2>
*/
@Controller
@RequestMapping("/trackingevent")
// Ancien contrôleur généré, sans écran : réservé aux administrateurs.
@AdminWrite
public class TrackingEventController {


	@Autowired
	private TrackingEventService trackingeventService;

	@PostMapping(value = "")
	public String createTrackingEvent(@Valid TrackingEvent model) {

   		 TrackingEvent data = trackingeventService.create(model);
    		if (data != null) {
    			return "";
  			  } else {
    			return "";
   			 }
    }

    @GetMapping(value = "")
    public String getAllTrackingEvent(Model model) {
        List<TrackingEvent> trackingeventList = trackingeventService.getAll();

		model.addAttribute("TrackingEvents",trackingeventList);

        return "";
    }

        @GetMapping(value = "/{id}")
    public String getOneTrackingEvent(@PathVariable("id") Integer id, Model model) {

            TrackingEvent e = trackingeventService.getOne(id);
            if (e == null) {
            	return "";
            }
			model.addAttribute("trackingevent",e);
            return "";
    }



}
