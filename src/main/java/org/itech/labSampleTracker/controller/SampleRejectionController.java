/* 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 */
package org.itech.labSampleTracker.controller;

import org.itech.labSampleTracker.security.AdminRead;
import org.itech.labSampleTracker.security.AdminWrite;
import org.itech.labSampleTracker.entities.SampleRejection;
import org.itech.labSampleTracker.service.SampleRejectionService;


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
* <h2>SampleRejectionController</h2>
*/
@Controller
@RequestMapping("/samplerejection")
// Ancien contrôleur généré, sans écran : réservé aux administrateurs.
@AdminWrite
public class SampleRejectionController {


	@Autowired
	private SampleRejectionService samplerejectionService;

	@PostMapping(value = "")
	public String createSampleRejection(@Valid SampleRejection model) {

   		 SampleRejection data = samplerejectionService.create(model);
    		if (data != null) {
    			return "";
  			  } else {
    			return "";
   			 }
    }

    @GetMapping(value = "")
    public String getAllSampleRejection(Model model) {
        List<SampleRejection> samplerejectionList = samplerejectionService.getAll();

		model.addAttribute("SampleRejections",samplerejectionList);

        return "";
    }

        @GetMapping(value = "/{id}")
    public String getOneSampleRejection(@PathVariable("id") Integer id, Model model) {

            SampleRejection e = samplerejectionService.getOne(id);
            if (e == null) {
            	return "";
            }
			model.addAttribute("samplerejection",e);
            return "";
    }



}
