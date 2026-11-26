/* 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 */
package org.itech.labSampleTracker.controller;

import org.itech.labSampleTracker.security.AdminRead;
import org.itech.labSampleTracker.security.AdminWrite;
import org.itech.labSampleTracker.entities.SamplePackage;
import org.itech.labSampleTracker.service.SamplePackageService;


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
* <h2>SamplePackageController</h2>
*/
@Controller
@RequestMapping("/samplepackage")
// Ancien contrôleur généré, sans écran : réservé aux administrateurs.
@AdminWrite
public class SamplePackageController {


	@Autowired
	private SamplePackageService samplepackageService;

	@PostMapping(value = "")
	public String createSamplePackage(@Valid SamplePackage model) {

   		 SamplePackage data = samplepackageService.create(model);
    		if (data != null) {
    			return "";
  			  } else {
    			return "";
   			 }
    }

    @GetMapping(value = "")
    public String getAllSamplePackage(Model model) {
        List<SamplePackage> samplepackageList = samplepackageService.getAll();

		model.addAttribute("SamplePackages",samplepackageList);

        return "";
    }

        @GetMapping(value = "/{id}")
    public String getOneSamplePackage(@PathVariable("id") Integer id, Model model) {

            SamplePackage e = samplepackageService.getOne(id);
            if (e == null) {
            	return "";
            }
			model.addAttribute("samplepackage",e);
            return "";
    }



}
