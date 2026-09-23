package org.itech.labSampleTracker.api.sync;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;

import org.itech.labSampleTracker.api.sync.dto.SampleDto;
import org.itech.labSampleTracker.api.sync.dto.SamplePullResponse;
import org.itech.labSampleTracker.api.sync.dto.SamplePushRequest;
import org.itech.labSampleTracker.api.sync.dto.SamplePushResponse;
import org.itech.labSampleTracker.entities.AppUser;
import org.itech.labSampleTracker.service.AppUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping(path = "/api_v2/sync/samples", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SampleSyncController {

	private static final Logger log = LoggerFactory.getLogger(SampleSyncController.class);

	private final SampleSyncService service;

	private final AppUserService appUserService;

	@PostMapping(value = "/push", consumes = MediaType.APPLICATION_JSON_VALUE)
	public SamplePushResponse push(@Valid @RequestBody SamplePushRequest req, Principal principal) {
		int incoming = (req != null && req.getSamples() != null) ? req.getSamples().size() : 0;
		log.info("POST /api_v2/sync/samples/push by user={} with {} item(s)",
				principal != null ? principal.getName() : "anonymous", incoming);
		AppUser user = principal != null ? appUserService.findUserByLogin(principal.getName()) : null;
		final List<SamplePushResponse.MappedId> mapped = service
				.upsertFromMobile(req != null ? req.getSamples() : List.of(), user);
		return new SamplePushResponse(mapped);
	}

	@GetMapping("/pull")
	public SamplePullResponse pull(
			@RequestParam(value = "since", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
			Principal principal) {

		String userName = principal.getName();
		log.info("GET /api_v2/sync/samples/pull by user={} since={}", userName, since);

		AppUser user = appUserService.findUserByLogin(userName);
		List<SampleDto> samples = service.pullSince(since, user);
		log.info("GET /api_v2/sync/samples/pull responded user={} count={}", userName, samples.size());
		return new SamplePullResponse(samples);
	}
}
