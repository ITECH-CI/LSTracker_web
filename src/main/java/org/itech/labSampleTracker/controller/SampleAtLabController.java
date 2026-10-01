/* 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 */
package org.itech.labSampleTracker.controller;

import org.itech.labSampleTracker.security.AdminRead;
import org.itech.labSampleTracker.security.AdminWrite;
import org.itech.labSampleTracker.entities.SampleAtLab;
import org.itech.labSampleTracker.service.SampleAtLabService;


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
* <h2>SampleAtLabController</h2>
*/
@Controller
@RequestMapping("/sampleatlab")
// Ancien contrôleur généré, sans écran : réservé aux administrateurs.
@AdminWrite
public class SampleAtLabController {


	@Autowired
	private SampleAtLabService sampleatlabService;

	@PostMapping(value = "")
	public String createSampleAtLab(@Valid SampleAtLab model) {

   		 SampleAtLab data = sampleatlabService.create(model);
    		if (data != null) {
    			return "";
  			  } else {
    			return "";
   			 }
    }

    @GetMapping(value = "")
    public String getAllSampleAtLab(Model model) {
        List<SampleAtLab> sampleatlabList = sampleatlabService.getAll();

		model.addAttribute("SampleAtLabs",sampleatlabList);

        return "";
    }

        @GetMapping(value = "/{id}")
    public String getOneSampleAtLab(@PathVariable("id") Integer id, Model model) {

            SampleAtLab e = sampleatlabService.getOne(id);
            if (e == null) {
            	return "";
            }
			model.addAttribute("sampleatlab",e);
            return "";
    }



}
