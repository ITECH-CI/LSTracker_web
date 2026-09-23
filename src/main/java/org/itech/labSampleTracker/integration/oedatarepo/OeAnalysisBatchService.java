package org.itech.labSampleTracker.integration.oedatarepo;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.itech.labSampleTracker.config.ClusterLock;
import org.itech.labSampleTracker.dao.SampleRepository;
import org.itech.labSampleTracker.entities.OeSyncRun;
import org.itech.labSampleTracker.entities.Sample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.Getter;

/**
 * Exécution d'un lot de synchronisation oedatarepo, partagée par le job
 * planifié et le déclenchement manuel (même logique → parité garantie).
 *
 * Deux gardes empêchent deux exécutions simultanées ; un second déclenchement
 * renvoie immédiatement un résumé "occupé" sans écrire de run :
 * <ul>
 * <li>{@link AtomicBoolean} : dans la même instance (job vs manuel) ;</li>
 * <li>{@link ClusterLock} (verrou consultatif PostgreSQL) : entre instances,
 * pour une montée en charge horizontale (plusieurs conteneurs derrière le
 * répartiteur) sans lot en double.</li>
 * </ul>
 */
@Service
public class OeAnalysisBatchService {

    private static final Logger log = LoggerFactory.getLogger(OeAnalysisBatchService.class);

    /** Garde-fou absolu contre une boucle de vagues infinie (ne devrait jamais être atteint). */
    private static final int MAX_WAVES_PER_CYCLE = 10000;

    private final SampleRepository sampleRepository;
    private final OeAnalysisSyncService syncService;
    private final OeSyncTrackingService trackingService;
    private final int batchSize;
    private final int maxAttempts;

    /** Nom du verrou partagé entre instances. */
    static final String CLUSTER_LOCK = "lstracker.oedatarepo.sync";

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ClusterLock clusterLock;

    // Métriques de supervision (cahier IX.2) : volume traité par lot, et heure
    // du dernier lot terminé, pour alerter si la synchronisation s'arrête.
    private final Counter examinedCounter;
    private final Counter updatedCounter;
    private final Counter errorCounter;
    private final AtomicLong lastRunEpochSeconds = new AtomicLong(0);

    public OeAnalysisBatchService(SampleRepository sampleRepository,
            OeAnalysisSyncService syncService, OeSyncTrackingService trackingService, ClusterLock clusterLock,
            MeterRegistry meterRegistry,
            @Value("${lstracker.oedatarepo.sync.batch-size:200}") int batchSize,
            @Value("${lstracker.oedatarepo.sync.max-attempts:5}") int maxAttempts) {
        this.sampleRepository = sampleRepository;
        this.syncService = syncService;
        this.trackingService = trackingService;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.clusterLock = clusterLock;
        this.examinedCounter = Counter.builder("lstracker.oedatarepo.sync.samples").tag("outcome", "examined")
                .description("Échantillons examinés par la synchronisation OpenELIS").register(meterRegistry);
        this.updatedCounter = Counter.builder("lstracker.oedatarepo.sync.samples").tag("outcome", "updated")
                .description("Échantillons mis à jour par la synchronisation OpenELIS").register(meterRegistry);
        this.errorCounter = Counter.builder("lstracker.oedatarepo.sync.samples").tag("outcome", "error")
                .description("Erreurs de la synchronisation OpenELIS").register(meterRegistry);
        Gauge.builder("lstracker.oedatarepo.sync.last.run", lastRunEpochSeconds, AtomicLong::get)
                .description("Fin du dernier lot de synchronisation OpenELIS (epoch, secondes)")
                .baseUnit("seconds").register(meterRegistry);
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Lance un lot. Renvoie un résumé ; {@code busy=true} si un lot était déjà en
     * cours (aucun run n'est alors écrit).
     *
     * @param triggeredBy origine (ex. {@code SCHEDULED} ou {@code MANUAL:login}).
     */
    public RunSummary runBatch(String triggeredBy) {
        if (!running.compareAndSet(false, true)) {
            log.info("Sync oedatarepo déjà en cours, déclenchement '{}' ignoré", triggeredBy);
            return RunSummary.busy();
        }
        Optional<ClusterLock.Held> lock;
        try {
            lock = clusterLock.tryAcquire(CLUSTER_LOCK);
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
        if (lock.isEmpty()) {
            running.set(false);
            log.info("Sync oedatarepo en cours sur une autre instance, déclenchement '{}' ignoré", triggeredBy);
            return RunSummary.busy();
        }
        try (ClusterLock.Held held = lock.get()) {
            return runLocked(triggeredBy);
        } finally {
            running.set(false);
        }
    }

    private RunSummary runLocked(String triggeredBy) {
        OeSyncRun run = trackingService.startRun(triggeredBy);
        int examined = 0;
        int updated = 0;
        int errors = 0;
        try {
            // Un cycle couvre TOUS les éligibles par vagues successives. Chaque
            // vague prend les batchSize éligibles "checkés il y a le plus
            // longtemps" (curseur last_at, cf. findEligibleForAnalysisSync) ;
            // recordOutcome avançant leur last_at à now(), ils tombent en fin de
            // file et la vague suivante prend les suivants.
            Set<Integer> seen = new HashSet<>();
            int wave = 0;
            while (true) {
                wave++;
                List<Sample> batch = sampleRepository.findEligibleForAnalysisSync(batchSize, maxAttempts);
                if (batch.isEmpty()) {
                    break; // plus aucun éligible → fin du cycle
                }
                int fresh = 0;
                for (Sample s : batch) {
                    if (!seen.add(s.getId())) {
                        continue; // déjà traité ce cycle → ne pas re-checker
                    }
                    fresh++;
                    examined++;
                    try {
                        OeSyncOutcome outcome = syncService.syncSampleTracked(s);
                        if (outcome == OeSyncOutcome.UPDATED) {
                            updated++;
                        } else if (outcome == OeSyncOutcome.ERROR) {
                            errors++;
                        }
                    } catch (Exception e) {
                        // Un échec sur un échantillon ne doit pas stopper le cycle.
                        errors++;
                        log.error("Sync oedatarepo échouée pour sample id={} labno={}: {}",
                                s.getId(), s.getLabNumber(), e.getMessage());
                    }
                }
                log.info("Sync oedatarepo ({}) vague {} : {} examiné(s), {} nouveau(x)",
                        triggeredBy, wave, batch.size(), fresh);

                // Curseur stagnant : la vague n'a ramené que des IDs déjà vus
                // (ex. samples SKIPPED dont last_at n'avance pas) → fin de cycle.
                if (fresh == 0) {
                    break;
                }
                // Garde-fou anti-boucle (ne devrait jamais se déclencher).
                if (wave >= MAX_WAVES_PER_CYCLE) {
                    log.warn("Sync oedatarepo ({}) : plafond de {} vagues atteint, arrêt de sécurité "
                            + "({} examinés)", triggeredBy, MAX_WAVES_PER_CYCLE, examined);
                    break;
                }
            }
            log.info("Sync oedatarepo ({}) terminée : {} examiné(s), {} mis à jour, {} erreur(s) sur {} vague(s)",
                    triggeredBy, examined, updated, errors, wave);
            return new RunSummary(false, examined, updated, errors);
        } finally {
            trackingService.finishRun(run, examined, updated, errors);
            examinedCounter.increment(examined);
            updatedCounter.increment(updated);
            errorCounter.increment(errors);
            lastRunEpochSeconds.set(System.currentTimeMillis() / 1000);
        }
    }

    /** Résumé d'un lot, renvoyé aux appelants (job/contrôleur). */
    @Getter
    public static final class RunSummary {
        private final boolean busy;
        private final int examined;
        private final int updated;
        private final int errors;

        public RunSummary(boolean busy, int examined, int updated, int errors) {
            this.busy = busy;
            this.examined = examined;
            this.updated = updated;
            this.errors = errors;
        }

        static RunSummary busy() {
            return new RunSummary(true, 0, 0, 0);
        }
    }
}
