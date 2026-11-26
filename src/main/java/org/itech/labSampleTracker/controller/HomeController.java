package org.itech.labSampleTracker.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping(value = { "/", "/home" })
public class HomeController {

	@GetMapping(value = "")
	public String analysisIndex(Model model,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
			@RequestParam(required = false, defaultValue = "0") Integer region,
			@RequestParam(required = false, defaultValue = "0") Integer district,
			@RequestParam(required = false, defaultValue = "0") Integer site,
			@RequestParam(required = false, defaultValue = "0") Integer circuit,
			@RequestParam(required = false, defaultValue = "0") Integer sampleType,
			RedirectAttributes redirectAttributes) {
		
		redirectAttributes.addAttribute("startDate", startDate);
		redirectAttributes.addAttribute("endDate", endDate);
		redirectAttributes.addAttribute("region", region);
		redirectAttributes.addAttribute("district", district);
		redirectAttributes.addAttribute("site", site);
		redirectAttributes.addAttribute("circuit", circuit);
		redirectAttributes.addAttribute("sampleType", sampleType);		

		return "redirect:/dashboard";

	}

	@GetMapping("/login")
	public String login(Model model, jakarta.servlet.http.HttpServletRequest request) {
		// Message du dernier échec (tentatives restantes, blocage), affiché une fois.
		jakarta.servlet.http.HttpSession session = request.getSession(false);
		if (session != null) {
			Object message = session.getAttribute(org.itech.labSampleTracker.config.SecurityConfig.LOGIN_ERROR_ATTRIBUTE);
			session.removeAttribute(org.itech.labSampleTracker.config.SecurityConfig.LOGIN_ERROR_ATTRIBUTE);
			model.addAttribute("loginError", message);
		}
		return "login";
	}

	@GetMapping("/403")
	public String error403() {
		return "error";
	}

}
