package org.itech.labSampleTracker.security;

import java.text.SimpleDateFormat;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.itech.labSampleTracker.config.ClusterLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Journal d'activité : qui a créé, modifié ou supprimé quoi, quand, par quel
 * canal, avec la valeur antérieure (cahier V).
 *
 * <p>Les écritures en base sont capturées par {@link ActivityLogListener}
 * (Hibernate) ; les actions sans écriture (exports) passent par
 * {@link #recordAction}. Les entrées d'une transaction sont gardées jusqu'à
 * sa validation : une transaction annulée ne laisse aucune trace, et un
 * retrait suivi d'un ajout identique (périmètre d'un utilisateur réenregistré
 * à l'identique) s'annulent au lieu d'encombrer le journal.
 *
 * <p>Jamais stocké : mot de passe et identifiant patient (seul le fait qu'ils
 * aient changé est noté). Aucune erreur de journalisation ne bloque
 * l'opération journalisée.
 */
@Service
public class ActivityLogService {

	private static final Logger log = LoggerFactory.getLogger(ActivityLogService.class);

	public static final String CREATE = "CREATE";
	public static final String UPDATE = "UPDATE";
	public static final String DELETE = "DELETE";
	public static final String EXPORT = "EXPORT";
	public static final String RUN = "RUN";

	/** Valeur remplaçant un champ sensible. */
	public static final String MASKED = "***";
	static final int RETENTION_MONTHS = 12;

	/** Entités techniques non journalisées (elles sont elles-mêmes des journaux). */
	static final Set<String> UNTRACKED = Set.of("OeSampleSync", "OeSyncRun", "TrackingEvent", "RefreshToken");
	/** Champs techniques ignorés : ils changent à chaque enregistrement ou connexion. */
	static final Set<String> IGNORED = Set.of("version", "lastupdatedAt", "lastUpdatedAt", "lastUpdatedBy",
			"lastLogin", "createdAt", "updatedAt", "content");
	/** Champs sensibles : changement noté, valeur jamais stockée. */
	static final Set<String> MASKED_FIELDS = Set.of("password", "patientIdentifier");

	private static final Object SKIP = new Object();
	private static final Object BUFFER_KEY = new Object();

	/** Une ligne du journal. changes : champ → [avant, après]. */
	public record Entry(String action, String entityType, String entityId, Map<String, Object[]> changes,
			String summary, String login, String channel, String ip) {
	}

	private final NamedParameterJdbcTemplate jdbc;
	private final TransactionTemplate newTransaction;
	private final ClusterLock clusterLock;
	private final ObjectMapper json;

	public ActivityLogService(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager txManager,
			ClusterLock clusterLock, ObjectMapper json) {
		this.jdbc = jdbc;
		this.newTransaction = new TransactionTemplate(txManager);
		this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.clusterLock = clusterLock;
		this.json = json;
	}

	// --- Construction des entrées (fonctions pures) --------------------------

	static boolean tracked(String entityType) {
		return !UNTRACKED.contains(entityType);
	}

	/** Valeur journalisable, ou {@link #SKIP} (associations, collections…). */
	static Object normalize(Object v) {
		if (v == null || v instanceof String || v instanceof Number || v instanceof Boolean) {
			return v;
		}
		if (v instanceof Date d) {
			return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(d);
		}
		if (v instanceof Temporal || v instanceof Enum<?>) {
			return v.toString();
		}
		return SKIP;
	}

	/** Champs d'une création (avant = null) ou d'une suppression (après = null). */
	static Map<String, Object[]> snapshot(String[] names, Object[] state, boolean created) {
		Map<String, Object[]> out = new LinkedHashMap<>();
		if (state == null) {
			return out;
		}
		for (int i = 0; i < names.length; i++) {
			Object v = normalize(state[i]);
			if (v == SKIP || v == null || IGNORED.contains(names[i])) {
				continue;
			}
			if (MASKED_FIELDS.contains(names[i])) {
				v = MASKED;
			}
			out.put(names[i], created ? new Object[] { null, v } : new Object[] { v, null });
		}
		return out;
	}

	/** Champs réellement modifiés, avec l'ancienne et la nouvelle valeur. */
	static Map<String, Object[]> diff(String[] names, Object[] oldState, Object[] newState) {
		Map<String, Object[]> out = new LinkedHashMap<>();
		if (newState == null) {
			return out;
		}
		for (int i = 0; i < names.length; i++) {
			if (IGNORED.contains(names[i])) {
				continue;
			}
			Object after = normalize(newState[i]);
			Object before = oldState == null ? null : normalize(oldState[i]);
			if (after == SKIP || before == SKIP || Objects.equals(before, after)) {
				continue;
			}
			if (MASKED_FIELDS.contains(names[i])) {
				out.put(names[i], new Object[] { MASKED, MASKED });
			} else {
				out.put(names[i], new Object[] { before, after });
			}
		}
		return out;
	}

	/**
	 * Annule les paires retrait + ajout identiques d'une même transaction
	 * (hors identifiant technique) : un périmètre réenregistré à l'identique
	 * ne laisse aucune trace, seules les vraies différences restent.
	 */
	static List<Entry> net(List<Entry> entries) {
		List<Entry> out = new ArrayList<>(entries);
		search: while (true) {
			for (Entry del : out) {
				if (!DELETE.equals(del.action())) {
					continue;
				}
				String key = content(del, 0);
				for (Entry add : out) {
					if (CREATE.equals(add.action()) && add.entityType().equals(del.entityType())
							&& content(add, 1).equals(key)) {
						out.removeIf(e -> e == del || e == add);
						continue search;
					}
				}
			}
			return out;
		}
	}

	/** Contenu d'une entrée sans l'identifiant, trié, pour comparer retrait et ajout. */
	private static String content(Entry e, int side) {
		Map<String, Object> m = new TreeMap<>();
		e.changes().forEach((k, v) -> {
			if (!"id".equals(k)) {
				m.put(k, v[side]);
			}
		});
		return m.toString();
	}

	// --- Enregistrement -------------------------------------------------------

	/** Entrée venant d'une écriture Hibernate ; auteur et canal pris dans le contexte courant. */
	void add(String action, String entityType, Object entityId, Map<String, Object[]> changes) {
		if (!tracked(entityType) || (UPDATE.equals(action) && changes.isEmpty())) {
			return;
		}
		Entry e = withContext(action, entityType, entityId == null ? null : entityId.toString(), changes, null);
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			bufferedEntries().add(e);
		} else {
			write(List.of(e));
		}
	}

	/** Action sensible sans écriture en base (export, lancement manuel…). */
	public void recordAction(String action, String entityType, String entityId, String summary) {
		write(List.of(withContext(action, entityType, entityId, Map.of(), summary)));
	}

	@SuppressWarnings("unchecked")
	private List<Entry> bufferedEntries() {
		List<Entry> buffer = (List<Entry>) TransactionSynchronizationManager.getResource(BUFFER_KEY);
		if (buffer == null) {
			List<Entry> created = new ArrayList<>();
			TransactionSynchronizationManager.bindResource(BUFFER_KEY, created);
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					write(net(created));
				}

				@Override
				public void afterCompletion(int status) {
					TransactionSynchronizationManager.unbindResourceIfPossible(BUFFER_KEY);
				}
			});
			buffer = created;
		}
		return buffer;
	}

	private static Entry withContext(String action, String entityType, String entityId,
			Map<String, Object[]> changes, String summary) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		String login = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
				? auth.getName() : null;
		HttpServletRequest req = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra
				? sra.getRequest() : null;
		String channel = req == null ? "système" : ConnectionLogService.channelOf(req.getServletPath());
		return new Entry(action, entityType, entityId, changes, summary, login, channel,
				req == null ? null : req.getRemoteAddr());
	}

	/** Écriture dans sa propre transaction : l'appelant est déjà validé (ou n'en a pas). */
	private void write(List<Entry> entries) {
		if (entries.isEmpty()) {
			return;
		}
		try {
			SqlParameterSource[] batch = entries.stream().map(e -> {
				try {
					return new MapSqlParameterSource().addValue("login", truncate(e.login(), 100))
							.addValue("channel", e.channel()).addValue("action", e.action())
							.addValue("type", e.entityType()).addValue("id", truncate(e.entityId(), 64))
							.addValue("changes", e.changes().isEmpty() ? null : json.writeValueAsString(e.changes()))
							.addValue("summary", truncate(e.summary(), 500)).addValue("ip", truncate(e.ip(), 64));
				} catch (Exception ex) {
					throw new IllegalStateException(ex);
				}
			}).toArray(SqlParameterSource[]::new);
			newTransaction.executeWithoutResult(s -> jdbc.batchUpdate(
					"INSERT INTO activity_log (app_user_id, login, channel, action, entity_type, entity_id, changes, summary, ip) "
							+ "VALUES ((SELECT u.id FROM app_user u WHERE lower(u.login) = lower(:login) ORDER BY u.id LIMIT 1), "
							+ ":login, :channel, :action, :type, :id, CAST(:changes AS JSONB), :summary, :ip)",
					batch));
		} catch (Exception ex) {
			log.error("Journal d'activité : écriture impossible ({} entrées, première : {} {} {}) : {}",
					entries.size(), entries.get(0).action(), entries.get(0).entityType(), entries.get(0).entityId(),
					ex.getMessage());
		}
	}

	/** Purge quotidienne au-delà de {@value #RETENTION_MONTHS} mois, une seule instance. */
	@Scheduled(cron = "${lstracker.activity-log.purge-cron:0 40 3 * * *}")
	public void purge() {
		Optional<ClusterLock.Held> lock = clusterLock.tryAcquire("activity-log-purge");
		if (lock.isEmpty()) {
			return;
		}
		try (ClusterLock.Held held = lock.get()) {
			int n = jdbc.update("DELETE FROM activity_log "
					+ "WHERE created_at < LOCALTIMESTAMP - make_interval(months => :months)",
					new MapSqlParameterSource("months", RETENTION_MONTHS));
			if (n > 0) {
				log.info("Purge du journal d'activité : {} entrées de plus de {} mois", n, RETENTION_MONTHS);
			}
		} catch (Exception ex) {
			log.error("Purge du journal d'activité impossible : {}", ex.getMessage());
		}
	}

	private static String truncate(String s, int max) {
		return s == null || s.length() <= max ? s : s.substring(0, max);
	}
}
