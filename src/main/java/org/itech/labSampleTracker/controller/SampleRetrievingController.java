/* 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 */
package org.itech.labSampleTracker.controller;

import org.itech.labSampleTracker.security.AdminRead;
import org.itech.labSampleTracker.security.AdminWrite;
import org.itech.labSampleTracker.entities.SampleRetrieving;
import org.itech.labSampleTracker.service.SampleRetrievingService;


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
* <h2>SampleRetrievingController</h2>
*/
@Controller
@RequestMapping("/sampleretrieving")
// Ancien contrôleur généré, sans écran : réservé aux administrateurs.
@AdminWrite
public class SampleRetrievingController {


	@Autowired
	private SampleRetrievingService sampleretrievingService;

	@PostMapping(value = "")
	public String createSampleRetrieving(@Valid SampleRetrieving model) {

   		 SampleRetrieving data = sampleretrievingService.create(model);
    		if (data != null) {
    			return "";
  			  } else {
    			return "";
   			 }
    }

    @GetMapping(value = "")
    public String getAllSampleRetrieving(Model model) {
        List<SampleRetrieving> sampleretrievingList = sampleretrievingService.getAll();

		model.addAttribute("SampleRetrievings",sampleretrievingList);

        return "";
    }

        @GetMapping(value = "/{id}")
    public String getOneSampleRetrieving(@PathVariable("id") Integer id, Model model) {

            SampleRetrieving e = sampleretrievingService.getOne(id);
            if (e == null) {
            	return "";
            }
			model.addAttribute("sampleretrieving",e);
            return "";
    }



}
