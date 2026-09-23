package org.itech.labSampleTracker.api.sync;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.itech.labSampleTracker.api.sync.dto.SampleDto;
import org.itech.labSampleTracker.api.sync.dto.SamplePushResponse;
import org.itech.labSampleTracker.api.sync.dto.SampleUpsertItem;
import org.itech.labSampleTracker.dao.SampleRepository;
import org.itech.labSampleTracker.dao.UserScopeRepository;
import org.itech.labSampleTracker.dao.SampleStatusRepository;
import org.itech.labSampleTracker.dao.SampleTypeRepository;
import org.itech.labSampleTracker.entities.AppUser;
import org.itech.labSampleTracker.entities.Sample;
import org.itech.labSampleTracker.entities.SampleRejection;
import org.itech.labSampleTracker.entities.SampleRetrieving;
import org.itech.labSampleTracker.entities.SampleStatus;
import org.itech.labSampleTracker.entities.SampleType;
import org.itech.labSampleTracker.entities.Site;
import org.itech.labSampleTracker.enums.UserType;
import org.itech.labSampleTracker.helper.DateUtils;
import org.itech.labSampleTracker.service.SampleRejectionService;
import org.itech.labSampleTracker.service.SampleRetrievingService;
import org.itech.labSampleTracker.service.security.UserScopeService;
import org.itech.labSampleTracker.service.security.UserScopeService.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SampleSyncService {

	private static final Logger log = LoggerFactory.getLogger(SampleSyncService.class);

	@Autowired
	private SampleRepository sampleRepo;
	@Autowired
	private SampleTypeRepository typeRepo;
	@Autowired
	private SampleStatusRepository statusRepo;
	@Autowired
	private SampleRejectionService rejectionService;

	@Autowired
	private SampleRetrievingService retrievingService;

	@Autowired
	private UserScopeService userScopeService;

	@Autowired
	private UserScopeRepository userScopeRepo;

	@Transactional
	public List<SamplePushResponse.MappedId> upsertFromMobile(List<SampleUpsertItem> items, AppUser user) {
		if (items == null || items.isEmpty()) {
			log.info("sync.push received empty batch");
			return List.of();
		}
		final int total = items.size();
		log.info("sync.push starting batch of {} item(s)", total);
		final List<SamplePushResponse.MappedId> mapped = new ArrayList<>();
		int skippedNoUuid = 0;
		int created = 0;
		int updated = 0;
		// Labos autorisés pour un convoyeur (null = pas de contrôle). Un labo hors
		// périmètre est accepté mais journalisé : une saisie faite hors ligne avec
		// d'anciennes métadonnées ne doit pas être perdue.
		final Set<Integer> riderLabIds = isRider(user)
				? new HashSet<>(userScopeRepo.findRiderLabIds(user.getId()))
				: null;

		for (SampleUpsertItem it : items) {
			if (it.getUuid() == null || it.getUuid().isBlank()) {
				skippedNoUuid++;
				log.warn("sync.push skipping item without uuid (external_id={})", it.getExternal_id());
				continue;
			}

			Sample s = resolveExisting(it);
			boolean isNew = (s == null);
			if (isNew) {
				s = new Sample();
				s.setUuid(it.getUuid());
				s.setCreatedAt(new Date());
				created++;
			} else {
				updated++;
			}

			// sample_retrieving to support legacy method
			SampleRetrieving sr;
			final Long sampleRetrievingId = ObjectUtils.isNotEmpty(s.getSampleRetrievingId())
					? s.getSampleRetrievingId().longValue()
					: null;

			if (ObjectUtils.isNotEmpty(sampleRetrievingId)) {
				sr = retrievingService.getOne(sampleRetrievingId.intValue());

				if (ObjectUtils.isEmpty(sr)) {
					sr = new SampleRetrieving();
				}
			} else {
				sr = new SampleRetrieving();
			}

			if (ObjectUtils.isNotEmpty(it.getFrom_site_id()))
				sr.setSiteId(it.getFrom_site_id().intValue());

			if (ObjectUtils.isNotEmpty(it.getPickup_date()))
				sr.setSampleRetrieveDate(DateUtils.parseIsoDateTimeOrNull(it.getPickup_date()));
			else
				sr.setSampleRetrieveDate(DateUtils.parseIsoDateTimeOrNull(it.getCreated_at()));

			sr.setEnteredDate(DateUtils.parseIsoDateTimeOrNull(it.getCreated_at()));
			if (ObjectUtils.isNotEmpty(it.getSample_conveyor()))
				sr.setAppUserId(it.getSample_conveyor().intValue());

			sr = retrievingService.create(sr);
			s.setSampleRetrieving(sr);
			s.setSampleRetrievingId(sr.getId());

			// Identifiants textuels
			s.setSampleIdentifier(nullIfBlank(it.getSample_identifier()));
			s.setPatientIdentifier(nullIfBlank(it.getPatient_identifier()));

			if (notBlank(it.getSample_type())) {
				SampleType st = typeRepo.findByName(it.getSample_type().trim());
				if (ObjectUtils.isNotEmpty(st)) {
					s.setSampleTypeId(st.getId());
				}
			}
			if (notBlank(it.getSample_status())) {
				SampleStatus st = statusRepo.findByStatus(it.getSample_status().trim());
				if (ObjectUtils.isNotEmpty(st)) {
					s.setSampleStatusId(st.getId());
				}
			}

			if (it.getDestination_lab_id() != null) {
				Integer labId = it.getDestination_lab_id().intValue();
				warnIfLabOutOfScope(riderLabIds, labId, s.getDestinationLabId(), "destination", it, user);
				s.setDestinationLabId(labId);
			}
			if (it.getDelivered_lab_id() != null) {
				Integer labId = it.getDelivered_lab_id().intValue();
				warnIfLabOutOfScope(riderLabIds, labId, s.getLabId(), "dépôt", it, user);
				s.setLabId(labId);
			}

			if (it.getFrom_site_id() != null)
				s.setRequesterSiteId(it.getFrom_site_id().longValue());
			if (it.getFrom_site_name() != null)
				s.setRequesterSiteName(it.getFrom_site_name());
			if (it.getFrom_site_code() != null)
				s.setRequesterSiteCode(it.getFrom_site_code());

			// Kilométrages
			s.setCollectionStartMileage(it.getStart_mileage());
			s.setCollectionEndMileage(it.getEnd_mileage());
			s.setResultStartMileage(it.getResult_start_mileage());
			s.setResultEndMileage(it.getResult_end_mileage());

			// Natures / num labo
			s.setLabNumber(nullIfBlank(it.getLab_number()));

			if (it.getSample_nature() != null)
				s.setSampleNature(it.getSample_nature());

			s.setPickupDate(DateUtils.parseIsoDateTimeOrNull(it.getPickup_date()));
			s.setCollectionDate(DateUtils.parseIsoDateTimeOrNull(it.getCollection_date()));
			s.setDeliverAtLabDate(DateUtils.parseIsoDateTimeOrNull(it.getDelivered_date()));
			s.setAcceptedAtLabDate(DateUtils.parseIsoDateTimeOrNull(it.getAccepted_date()));
			s.setResultCollectionDate(DateUtils.parseIsoDateTimeOrNull(it.getResult_collection_date()));
			s.setResultDeliveryDate(DateUtils.parseIsoDateTimeOrNull(it.getResult_delivered_date()));
			s.setAnalysisCompletedDate(DateUtils.parseIsoDateTimeOrNull(it.getAnalysis_completed_date()));
			s.setAnalysisReleasedDate(DateUtils.parseIsoDateTimeOrNull(it.getAnalysis_released_date()));

			s.setLastupdatedAt(new Date());
			s = sampleRepo.save(s);

			SampleRejection existingRejection = rejectionService.getOneBySampleId(s.getId());

			boolean hasRejectionData = it.getRejection_type_id() != null;

			if (existingRejection != null && hasRejectionData) {
				if (ObjectUtils.isNotEmpty(it.getRejection_type_id()))
					existingRejection.setSampleRejectionTypeId(it.getRejection_type_id().intValue());
				existingRejection.setComment(it.getRejection_comment());

				existingRejection.setRejectionDate(DateUtils.parseIsoDateTimeOrNull(it.getRejection_date()));

				rejectionService.update(existingRejection);
			} else if (existingRejection != null && !hasRejectionData) {
				rejectionService.delete(existingRejection.getId());
			} else if (existingRejection == null && hasRejectionData) {
				SampleRejection newRejection = new SampleRejection();
				newRejection.setSample(s);
				newRejection.setSampleId(s.getId());
				if (ObjectUtils.isNotEmpty(it.getRejection_type_id()))
					newRejection.setSampleRejectionTypeId(it.getRejection_type_id().intValue());
				newRejection.setComment(it.getRejection_comment());
				newRejection.setRejectionDate(DateUtils.parseIsoDateTimeOrNull(it.getRejection_date()));
				newRejection.setCreatedAt(new Date());

				rejectionService.create(newRejection);
			}

			mapped.add(SamplePushResponse.MappedId.builder().uuid(it.getUuid()).external_id(String.valueOf(s.getId()))
					.build());
			log.debug("sync.push item processed (uuid={}, id={}, isNew={})", it.getUuid(), s.getId(), isNew);
		}
		log.info("sync.push done : total={}, created={}, updated={}, skippedNoUuid={}, mapped={}",
				total, created, updated, skippedNoUuid, mapped.size());
		return mapped;
	}

	@Transactional(readOnly = true)
	public List<SampleDto> pullSince(OffsetDateTime sinceOrNull, AppUser user) {
		final List<Sample> list;
		final Date since = sinceOrNull == null ? null : Date.from(sinceOrNull.toInstant());

		if (ObjectUtils.isEmpty(user)) {
			// Fail-closed : un utilisateur inconnu ne doit voir AUCUN échantillon.
			// On préfère retourner une liste vide plutôt que de risquer une
			// fuite de données par contournement d'authentification.
			log.warn("sync.pull denied : authenticated user not resolved");
			return List.of();
		}
		if ("ADMIN".equals(user.getRole())) {
			list = since == null ? sampleRepo.findAll()
					: sampleRepo.findAllForAdminSinceNative(since);
		} else {
			// Non-admin: widen the existing user filter (circuits + labs) with the
			// sites the user can reach through Region/District direct assignments.
			Scope scope = userScopeService.resolve(user.getId());
			List<Integer> extraSites = scope.getSiteIds() == null ? List.of() : scope.getSiteIds();
			boolean hasExtra = !extraSites.isEmpty();
			// Use placeholder when empty so PostgreSQL IN() does not blow up.
			List<Integer> paramSites = hasExtra ? extraSites : List.of(-1);
			list = sampleRepo.findAllForUserSinceWithScopeNative(since, user.getId(), paramSites, hasExtra);
			log.info("sync.pull user={} since={} totalSamples={} extraSiteIds={}",
					user.getId(), since, list.size(), extraSites.size());
		}

		return list.stream().map(this::toDto).collect(Collectors.toList());
	}

	private boolean isRider(AppUser user) {
		if (user == null || "ADMIN".equalsIgnoreCase(user.getRole())) {
			return false;
		}
		String rider = UserType.RIDER.getType();
		return rider.equalsIgnoreCase(user.getRole()) || rider.equalsIgnoreCase(user.getUserType());
	}

	// Ne signale que les valeurs nouvelles : un labo déjà en base (posé par un
	// admin sur le web, par exemple) n'est pas du fait du convoyeur.
	private void warnIfLabOutOfScope(Set<Integer> riderLabIds, Integer labId, Integer currentLabId, String role,
			SampleUpsertItem it, AppUser user) {
		if (riderLabIds == null || riderLabIds.contains(labId) || labId.equals(currentLabId)) {
			return;
		}
		log.warn("sync.push labo de {} hors périmètre du convoyeur (user={}, uuid={}, lab={})", role,
				user.getLogin(), it.getUuid(), labId);
	}

	private Sample resolveExisting(SampleUpsertItem it) {
		if (notBlank(it.getExternal_id())) {
			try {
				Integer id = Integer.valueOf(it.getExternal_id().trim());
				return sampleRepo.findById(id).orElse(null);
			} catch (NumberFormatException ignored) {
			}
		}
		if (notBlank(it.getUuid())) {
			try {
				return sampleRepo.findByUuid(it.getUuid().trim()).orElse(null);
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	private SampleDto toDto(Sample s) {
		String typeCode = null;
		if (s.getSampleType() != null) {
			typeCode = nullIfBlank(s.getSampleType().getName());
		}
		String statusCode = null;
		if (s.getSampleStatusId() != null) {
			SampleStatus sampleStatus = statusRepo.getReferenceById(s.getSampleStatusId());
			if (ObjectUtils.isNotEmpty(sampleStatus))
				statusCode = sampleStatus.getStatus();
		}

		String uuid = s.getUuid();
		if (uuid == null) {
			uuid = UUID.randomUUID().toString();
		}
		Long rejectionType = null;
		String rejectionComment = null;
		Date rejectionDate = null;

		SampleRejection existingRejection = rejectionService.getOneBySampleId(s.getId());

		if (ObjectUtils.isNotEmpty(existingRejection)) {
			rejectionComment = existingRejection.getComment();
			rejectionDate = ObjectUtils.isNotEmpty(existingRejection.getRejectionDate())
					? existingRejection.getRejectionDate()
					: existingRejection.getCreatedAt();
			rejectionType = existingRejection.getSampleRejectionTypeId() != null
					? existingRejection.getSampleRejectionTypeId().longValue()
					: null;
		}

		s.getRequesterSiteId();

		String fromSiteName = s.getRequesterSiteName();
		String fromSiteCode = s.getRequesterSiteCode();
		Long fromSiteId = s.getRequesterSiteId();

		SampleRetrieving sampleRetrieving = null;

		if (ObjectUtils.isNotEmpty(s.getSampleRetrievingId())) {
			sampleRetrieving = retrievingService.getOne(s.getSampleRetrievingId());
		}

		if (ObjectUtils.isNotEmpty(sampleRetrieving)) {
			Site requesterSite = sampleRetrieving.getSite();
			if (ObjectUtils.isNotEmpty(requesterSite)) {
				if (ObjectUtils.isNotEmpty(fromSiteName)) {
					fromSiteName = requesterSite.getName();
				}
				if (ObjectUtils.isNotEmpty(fromSiteCode)) {
					fromSiteCode = requesterSite.getDhisCode();
				}
				if (ObjectUtils.isNotEmpty(fromSiteId)) {
					fromSiteId = requesterSite.getId().longValue();
				}
			}
		}
		String pickupDate = DateUtils.toIso(
				ObjectUtils.isEmpty(s.getPickupDate()) ? s.getPickupDate() : sampleRetrieving.getSampleRetrieveDate());

		return SampleDto.builder().external_id(String.valueOf(s.getId())).uuid(uuid)
				.sample_conveyor(s.getSampleConveyorId()).referring_sample_id(s.getSampleReferringId())
				.start_mileage(s.getCollectionStartMileage()).end_mileage(s.getCollectionEndMileage())
				.result_start_mileage(s.getResultStartMileage()).result_end_mileage(s.getResultEndMileage())
				.from_site_name(fromSiteName).from_site_code(fromSiteCode).from_site_id(fromSiteId)
				.destination_lab_id(s.getDestinationLabId() != null ? s.getDestinationLabId().longValue() : null)
				.delivered_lab_id(s.getLabId() != null ? s.getLabId().longValue() : null)
				.sample_identifier(s.getSampleIdentifier()).patient_identifier(s.getPatientIdentifier())
				.sample_type(typeCode).sample_nature(s.getSampleNature())
				.collection_date(DateUtils.toIso(s.getCollectionDate())).pickup_date(pickupDate)
				.delivered_date(DateUtils.toIso(s.getDeliverAtLabDate()))
				.accepted_date(DateUtils.toIso(s.getAcceptedAtLabDate())).lab_number(s.getLabNumber())
				.sample_status(statusCode).analysis_completed_date(DateUtils.toIso(s.getAnalysisCompletedDate()))
				.analysis_released_date(DateUtils.toIso(s.getAnalysisReleasedDate()))
				.result_collection_date(DateUtils.toIso(s.getResultCollectionDate()))
				.result_delivered_date(DateUtils.toIso(s.getResultDeliveryDate()))
				.created_at(DateUtils.toIso(s.getCreatedAt())).lastupdated_at(DateUtils.toIso(s.getLastupdatedAt()))
				.rejection_type_id(rejectionType).rejection_comment(rejectionComment)
				.rejection_date(DateUtils.toIso(rejectionDate)).build();
	}

	private static String nullIfBlank(String s) {
		return (s == null || s.isBlank()) ? null : s;
	}

	private static boolean notBlank(String s) {
		return s != null && !s.isBlank();
	}
}
