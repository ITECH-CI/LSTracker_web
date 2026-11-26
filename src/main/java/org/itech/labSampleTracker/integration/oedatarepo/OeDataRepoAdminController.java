package org.itech.labSampleTracker.integration.oedatarepo;

import org.itech.labSampleTracker.security.AdminRead;
import org.itech.labSampleTracker.security.AdminWrite;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.itech.labSampleTracker.controller.BaseController;
import org.itech.labSampleTracker.dao.SampleRepository;
import org.itech.labSampleTracker.entities.OeSyncRun;
import org.itech.labSampleTracker.entities.Sample;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Page d'administration de l'intégration oedatarepo : état, liste des
 * échantillons éligibles avec prévisualisation à la demande, déclenchement
 * manuel (lot + ciblé), historique des exécutions.
 *
 * Accès réservé aux rôles ADMIN / SUPER_ADMIN (méthode-security active).
 */
@Controller
@RequestMapping("/sync-openelis")
@AdminWrite
public class OeDataRepoAdminController extends BaseController {

    private final SampleRepository sampleRepository;
    private final OeDataRepoClient client;
    private final OeAnalysisSyncService syncService;
    private final OeAnalysisBatchService batchService;
    private final OeSyncTrackingService trackingService;

    @Value("${lstracker.oedatarepo.enabled:false}")
    private boolean enabled;
    @Value("${lstracker.oedatarepo.base-url:}")
    private String baseUrl;
    @Value("${lstracker.oedatarepo.sync.batch-size:200}")
    private int batchSize;
    @Value("${lstracker.oedatarepo.sync.interval-ms:1800000}")
    private long intervalMs;
    @Value("${lstracker.oedatarepo.sync.max-attempts:5}")
    private int maxAttempts;

    public OeDataRepoAdminController(SampleRepository sampleRepository, OeDataRepoClient client,
            OeAnalysisSyncService syncService, OeAnalysisBatchService batchService,
            OeSyncTrackingService trackingService) {
        this.sampleRepository = sampleRepository;
        this.client = client;
        this.syncService = syncService;
        this.batchService = batchService;
        this.trackingService = trackingService;
    }

    // --- Page ------------------------------------------------------------

    @AdminRead
    @GetMapping("")
    public String index(Model model) {
        model.addAttribute("enabled", enabled);
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("batchSize", batchSize);
        model.addAttribute("intervalMs", intervalMs);
        model.addAttribute("maxAttempts", maxAttempts);
        model.addAttribute("ready", client.isReady());
        return "oedatarepo/index";
    }

    // --- État (panneau) --------------------------------------------------

    @AdminRead
    @GetMapping(value = "/state", produces = "application/json")
    @ResponseBody
    public Map<String, Object> state() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", enabled);
        out.put("ready", client.isReady());
        out.put("baseUrl", baseUrl);
        out.put("connectionOk", client.ping());
        out.put("running", batchService.isRunning());
        out.put("batchSize", batchSize);
        out.put("intervalMs", intervalMs);
        out.put("maxAttempts", maxAttempts);

        trackingService.lastRun().ifPresent(run -> {
            out.put("lastRun", runToMap(run));
            if (run.getFinishedAt() != null) {
                out.put("nextRunApprox", run.getFinishedAt().getTime() + intervalMs);
            }
        });
        return out;
    }

    // --- Liste des éligibles (DataTables server-side) --------------------

    @AdminRead
    @GetMapping(value = "/eligible/data", produces = "application/json")
    @ResponseBody
    public Map<String, Object> eligibleData(
            @RequestParam(name = "draw", defaultValue = "1") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "search_text", required = false) String searchText) {

        int page = length > 0 ? start / length : 0;
        Pageable pageable = PageRequest.of(page, length > 0 ? length : 25);
        String search = (searchText == null || searchText.trim().isEmpty()) ? null : searchText.trim();
        Page<Map<String, Object>> p = sampleRepository.findEligibleWithSyncMeta(pageable, search);

        List<Map<String, Object>> rows = p.getContent().stream()
                .map(this::decorateEligibleRow)
                .collect(Collectors.toList());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("draw", draw);
        out.put("recordsTotal", p.getTotalElements());
        out.put("recordsFiltered", p.getTotalElements());
        out.put("data", rows);
        return out;
    }

    // --- Historique des exécutions --------------------------------------

    @AdminRead
    @GetMapping(value = "/runs/data", produces = "application/json")
    @ResponseBody
    public Map<String, Object> runsData() {
        List<Map<String, Object>> rows = trackingService.recentRuns().stream()
                .map(this::runToMap)
                .collect(Collectors.toList());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", rows);
        return out;
    }

    // --- Prévisualisation (sans appliquer) ------------------------------

    @PostMapping(value = "/preview/{sampleId}", produces = "application/json")
    @ResponseBody
    public ResponseEntity<?> preview(@PathVariable("sampleId") int sampleId) {
        if (!client.isReady()) {
            return ResponseEntity.status(503).body(Map.of("error", "Intégration oedatarepo non configurée/activée"));
        }
        Sample sample = findSample(sampleId);
        if (sample == null) {
            return ResponseEntity.notFound().build();
        }
        if (isBlankLabno(sample)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Échantillon sans numéro de laboratoire"));
        }
        PreviewResult preview = syncService.previewSample(sample);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sampleId", sampleId);
        body.put("labNumber", sample.getLabNumber());
        body.put("outcome", preview.getOutcome().name());
        body.put("currentStatusId", preview.getCurrentStatusId());
        body.put("proposedStatusId", preview.getProposedStatusId());
        body.put("proposedStatus", preview.getProposedStatus());
        body.put("completedDate", preview.getCompletedDate());
        body.put("releasedDate", preview.getReleasedDate());
        body.put("errorMessage", preview.getErrorMessage());
        return ResponseEntity.ok(body);
    }

    // --- Déclenchement manuel : lot complet ------------------------------

    @PostMapping(value = "/run-now", produces = "application/json")
    @ResponseBody
    public ResponseEntity<?> runNow() {
        if (!client.isReady()) {
            return ResponseEntity.status(503).body(Map.of("error", "Intégration oedatarepo non configurée/activée"));
        }
        OeAnalysisBatchService.RunSummary summary = batchService.runBatch("MANUAL:" + getUsername());
        if (summary.isBusy()) {
            return ResponseEntity.status(409).body(Map.of("busy", true,
                    "message", "Une synchronisation est déjà en cours"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("busy", false);
        body.put("examined", summary.getExamined());
        body.put("updated", summary.getUpdated());
        body.put("errors", summary.getErrors());
        return ResponseEntity.ok(body);
    }

    // --- Déclenchement manuel : refresh ciblé (respecte max-attempts) ----

    @PostMapping(value = "/refresh/{sampleId}", produces = "application/json")
    @ResponseBody
    public ResponseEntity<?> refresh(@PathVariable("sampleId") int sampleId) {
        if (!client.isReady()) {
            return ResponseEntity.status(503).body(Map.of("error", "Intégration oedatarepo non configurée/activée"));
        }
        Sample sample = findSample(sampleId);
        if (sample == null) {
            return ResponseEntity.notFound().build();
        }
        if (isBlankLabno(sample)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Échantillon sans numéro de laboratoire"));
        }
        if (isExhausted(sampleId)) {
            return ResponseEntity.status(409).body(Map.of("exhausted", true,
                    "message", "Échantillon épuisé (labno introuvable), réinitialiser avant de relancer"));
        }
        OeSyncOutcome outcome = syncService.syncSampleTracked(sample);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sampleId", sampleId);
        body.put("labNumber", sample.getLabNumber());
        body.put("outcome", outcome.name());
        body.put("updated", outcome == OeSyncOutcome.UPDATED);
        body.put("sampleStatusId", sample.getSampleStatusId());
        return ResponseEntity.ok(body);
    }

    // --- Réinitialisation du compteur -----------------------------------

    @PostMapping(value = "/reset/{sampleId}", produces = "application/json")
    @ResponseBody
    public ResponseEntity<?> reset(@PathVariable("sampleId") int sampleId) {
        trackingService.resetSample(sampleId);
        return ResponseEntity.ok(Map.of("sampleId", sampleId, "reset", true));
    }

    // --- Helpers ---------------------------------------------------------

    private Sample findSample(int sampleId) {
        try {
            return sampleRepository.findById(sampleId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isBlankLabno(Sample sample) {
        return sample.getLabNumber() == null || sample.getLabNumber().trim().isEmpty();
    }

    private boolean isExhausted(int sampleId) {
        return trackingService.findBySampleId(sampleId)
                .map(s -> s.getAttempts() >= maxAttempts
                        && OeSyncOutcome.NOT_FOUND.name().equals(s.getLastOutcome()))
                .orElse(false);
    }

    private Map<String, Object> runToMap(OeSyncRun run) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", run.getId());
        m.put("startedAt", run.getStartedAt());
        m.put("finishedAt", run.getFinishedAt());
        m.put("triggeredBy", run.getTriggeredBy());
        m.put("examined", run.getExamined());
        m.put("updated", run.getUpdated());
        m.put("errors", run.getErrors());
        m.put("durationMs", run.getDurationMs());
        return m;
    }

    /** Ajoute le flag "exhausted" à une ligne d'éligible déjà décorée par la requête. */
    private Map<String, Object> decorateEligibleRow(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>(row);
        Object attemptsObj = row.get("attempts");
        Object outcomeObj = row.get("last_outcome");
        int attempts = attemptsObj == null ? 0 : ((Number) attemptsObj).intValue();
        boolean exhausted = attempts >= maxAttempts
                && OeSyncOutcome.NOT_FOUND.name().equals(outcomeObj);
        m.put("exhausted", exhausted);
        return m;
    }
}
