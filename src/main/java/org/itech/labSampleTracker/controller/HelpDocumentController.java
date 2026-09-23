package org.itech.labSampleTracker.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.itech.labSampleTracker.dao.HelpDocumentRepository;
import org.itech.labSampleTracker.entities.HelpDocument;
import org.itech.labSampleTracker.enums.HelpDocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Manuels d'aide (observation 3.2) : téléversement par l'administrateur,
 * consultation par tous les utilisateurs connectés depuis le menu Aide. Évite
 * de modifier le code et de redéployer pour publier une nouvelle version.
 */
@Controller
public class HelpDocumentController extends BaseController {

	private static final Logger log = LoggerFactory.getLogger(HelpDocumentController.class);

	/** Taille maximale d'un manuel (aligné sur spring.servlet.multipart). */
	static final long MAX_SIZE_BYTES = 25L * 1024 * 1024;

	private final HelpDocumentRepository repository;

	public HelpDocumentController(HelpDocumentRepository repository) {
		this.repository = repository;
	}

	// --- Consultation (menu Aide) -------------------------------------------

	/**
	 * Sert la version téléversée ; à défaut, le fichier livré avec
	 * l'application (manuel utilisateur), ou une page « non publié ».
	 */
	@GetMapping("/aide/{slug}")
	@Transactional(readOnly = true)
	public ModelAndView show(@PathVariable String slug, HttpServletResponse response) throws IOException {
		HelpDocumentType type = HelpDocumentType.fromSlug(slug).orElse(null);
		if (type == null) {
			ModelAndView notFound = new ModelAndView("error/404");
			notFound.setStatus(HttpStatus.NOT_FOUND);
			return notFound;
		}
		HelpDocument doc = repository.findById(type.name()).orElse(null);
		if (doc == null) {
			if (type.getFallback() != null) {
				return new ModelAndView("redirect:" + type.getFallback());
			}
			ModelAndView missing = new ModelAndView("helpdocument/missing");
			missing.addObject("documentLabel", type.getLabel());
			return missing;
		}
		response.setContentType(MediaType.APPLICATION_PDF_VALUE);
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.inline().filename(doc.getFileName(), StandardCharsets.UTF_8).build().toString());
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
		response.setContentLengthLong(doc.getContent().length);
		response.getOutputStream().write(doc.getContent());
		return null; // réponse déjà écrite
	}

	// --- Administration ------------------------------------------------------

	@GetMapping("/manuels")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String index(Model model) {
		Map<String, HelpDocumentRepository.Info> uploaded = repository.findAllInfo().stream()
				.collect(Collectors.toMap(HelpDocumentRepository.Info::getCode, Function.identity()));
		Map<HelpDocumentType, HelpDocumentRepository.Info> slots = new LinkedHashMap<>();
		Arrays.stream(HelpDocumentType.values()).forEach(t -> slots.put(t, uploaded.get(t.name())));
		model.addAttribute("slots", slots);
		model.addAttribute("maxSizeMb", MAX_SIZE_BYTES / (1024 * 1024));
		return "helpdocument/index";
	}

	@PostMapping("/manuels/{slug}")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String upload(@PathVariable String slug, @RequestParam("file") MultipartFile file,
			RedirectAttributes redirect) {
		HelpDocumentType type = HelpDocumentType.fromSlug(slug).orElse(null);
		String error = validate(type, file);
		if (error != null) {
			redirect.addFlashAttribute("message_error", error);
			return "redirect:/manuels";
		}
		try {
			HelpDocument doc = repository.findById(type.name()).orElseGet(HelpDocument::new);
			doc.setCode(type.name());
			doc.setFileName(safeFileName(file.getOriginalFilename(), type));
			doc.setContentType(MediaType.APPLICATION_PDF_VALUE);
			doc.setContent(file.getBytes());
			doc.setSizeBytes(file.getSize());
			doc.setUploadedAt(new Date());
			doc.setUploadedBy(getUsername());
			repository.save(doc);
			log.info("Manuel « {} » publié par {} ({} octets)", type.getLabel(), getUsername(), file.getSize());
			redirect.addFlashAttribute("message_success", type.getLabel() + " publié. Il est disponible dans le menu Aide.");
		} catch (IOException e) {
			redirect.addFlashAttribute("message_error", "Lecture du fichier impossible : " + e.getMessage());
		}
		return "redirect:/manuels";
	}

	/** Retire la version téléversée (retour au fichier livré, s'il existe). */
	@PostMapping("/manuels/{slug}/delete")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String delete(@PathVariable String slug, RedirectAttributes redirect) {
		HelpDocumentType.fromSlug(slug).ifPresent(type -> {
			repository.deleteById(type.name());
			log.info("Manuel « {} » retiré par {}", type.getLabel(), getUsername());
			redirect.addFlashAttribute("message_success", type.getLabel() + " retiré.");
		});
		return "redirect:/manuels";
	}

	/** Contrôles : type connu, fichier non vide, taille, et vrai PDF (signature %PDF-). */
	static String validate(HelpDocumentType type, MultipartFile file) {
		if (type == null) {
			return "Type de document inconnu.";
		}
		if (file == null || file.isEmpty()) {
			return "Aucun fichier sélectionné.";
		}
		if (file.getSize() > MAX_SIZE_BYTES) {
			return "Fichier trop volumineux (" + MAX_SIZE_BYTES / (1024 * 1024) + " Mo au maximum).";
		}
		try {
			byte[] head = Arrays.copyOf(file.getBytes(), 5);
			if (!new String(head, StandardCharsets.US_ASCII).equals("%PDF-")) {
				return "Le fichier n'est pas un PDF.";
			}
		} catch (IOException e) {
			return "Lecture du fichier impossible : " + e.getMessage();
		}
		return null;
	}

	/** Nom de fichier conservé pour l'affichage, sans chemin ni caractère de contrôle. */
	static String safeFileName(String original, HelpDocumentType type) {
		String name = original == null ? "" : original.replaceAll(".*[/\\\\]", "").replaceAll("[\\p{Cntrl}\"]", "").trim();
		if (name.isEmpty()) {
			name = type.getSlug() + ".pdf";
		}
		return name.length() > 200 ? name.substring(0, 200) : name;
	}
}
