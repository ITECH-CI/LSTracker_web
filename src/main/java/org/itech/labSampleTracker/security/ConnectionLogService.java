package org.itech.labSampleTracker.security;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.itech.labSampleTracker.config.ClusterLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Journal des connexions et fréquentation (cahier V, journalisation).
 *
 * <ul>
 * <li>{@link #recordAttempt} : une ligne par tentative de connexion dans
 * {@code connection_log} ;</li>
 * <li>{@link #touch} : présence en ligne (en mémoire) et une « visite » par
 * utilisateur, jour et canal dans {@code user_activity_day} ;</li>
 * <li>{@link #presence} : compteurs du pied de page.</li>
 * </ul>
 *
 * <p>« En ligne » = une requête dans les {@value #ONLINE_MINUTES} dernières
 * minutes : le jeton mobile ne dit pas si l'application est ouverte, et une
 * session web survit à la fermeture du navigateur. La présence est tenue par
 * instance : en déploiement multi-instances, chaque instance ne compte que
 * ses propres utilisateurs (les visites, en base, restent exactes).
 *
 * <p>Aucune erreur de journalisation ne doit empêcher une connexion : toutes
 * les écritures sont protégées.
 */
@Service
@EnableScheduling
public class ConnectionLogService {

	private static final Logger log = LoggerFactory.getLogger(ConnectionLogService.class);

	public static final String WEB = "web";
	public static final String MOBILE = "mobile";

	static final int ONLINE_MINUTES = 15;
	/** Conservation du journal et des visites (données personnelles). */
	static final int RETENTION_MONTHS = 12;
	private static final Duration MONTH_CACHE = Duration.ofMinutes(1);

	private final NamedParameterJdbcTemplate jdbc;
	private final ClusterLock clusterLock;

	/** Dernière requête par canal et par identifiant de connexion. */
	private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();
	/** Visites déjà enregistrées aujourd'hui (évite une écriture par requête). */
	private final Set<String> visitsToday = ConcurrentHashMap.newKeySet();
	private volatile LocalDate visitsDay = LocalDate.now();

	private volatile Map<String, Long> monthCache;
	private volatile Instant monthCachedAt = Instant.EPOCH;

	public ConnectionLogService(NamedParameterJdbcTemplate jdbc, ClusterLock clusterLock) {
		this.jdbc = jdbc;
		this.clusterLock = clusterLock;
	}

	/** Canal d'une requête : l'API mobile (actuelle ou ancienne) ou le web. */
	public static String channelOf(String servletPath) {
		return servletPath != null && (servletPath.startsWith("/api_v2/") || servletPath.startsWith("/api/"))
				? MOBILE : WEB;
	}

	public void recordAttempt(String login, String channel, String outcome, String ip, String userAgent) {
		if (login == null || login.isBlank()) {
			return;
		}
		String l = truncate(login.trim(), 100);
		try {
			jdbc.update("INSERT INTO connection_log (app_user_id, login, role, channel, outcome, ip, user_agent) "
					+ "SELECT u.id, :login, u.role, :channel, :outcome, :ip, :ua "
					+ "FROM (SELECT 1) one LEFT JOIN app_user u ON lower(u.login) = lower(:login) "
					+ "ORDER BY u.id LIMIT 1",
					new MapSqlParameterSource().addValue("login", l).addValue("channel", channel)
							.addValue("outcome", outcome).addValue("ip", truncate(ip, 64))
							.addValue("ua", truncate(userAgent, 255)));
		} catch (Exception ex) {
			log.error("Journal des connexions : écriture impossible ({} / {} / {}) : {}", l, channel, outcome,
					ex.getMessage());
		}
	}

	/** Déconnexion web explicite : l'utilisateur n'est plus compté en ligne. */
	public void forget(String login, String channel) {
		if (login != null) {
			lastSeen.remove(channel + ':' + login.toLowerCase());
		}
	}

	/** Requête authentifiée : présence en ligne et visite du jour. */
	public void touch(String login, String channel) {
		if (login == null || login.isBlank()) {
			return;
		}
		String key = channel + ':' + login.toLowerCase();
		lastSeen.put(key, Instant.now());

		LocalDate today = LocalDate.now();
		if (!today.equals(visitsDay)) {
			visitsToday.clear();
			visitsDay = today;
		}
		if (visitsToday.add(key)) {
			try {
				jdbc.update("INSERT INTO user_activity_day (app_user_id, day, channel) "
						+ "SELECT u.id, CAST(:day AS DATE), :channel FROM app_user u WHERE lower(u.login) = lower(:login) "
						+ "ON CONFLICT DO NOTHING",
						new MapSqlParameterSource().addValue("day", today).addValue("channel", channel)
								.addValue("login", login));
			} catch (Exception ex) {
				visitsToday.remove(key); // nouvel essai à la prochaine requête
				log.error("Journal des visites : écriture impossible ({} / {}) : {}", login, channel,
						ex.getMessage());
			}
		}
	}

	/**
	 * Compteurs du pied de page : utilisateurs en ligne par canal, et pour le
	 * mois en cours, utilisateurs actifs et visites (utilisateur × jour ×
	 * canal).
	 */
	public Map<String, Long> presence() {
		Instant limit = Instant.now().minus(Duration.ofMinutes(ONLINE_MINUTES));
		lastSeen.values().removeIf(t -> t.isBefore(limit));
		long web = lastSeen.keySet().stream().filter(k -> k.startsWith(WEB + ':')).count();
		long mobile = lastSeen.keySet().stream().filter(k -> k.startsWith(MOBILE + ':')).count();

		Map<String, Long> out = new LinkedHashMap<>();
		out.put("onlineWeb", web);
		out.put("onlineMobile", mobile);
		out.putAll(month());
		return out;
	}

	/** Utilisateur en ligne : identifiant (minuscules), canal, dernière requête. */
	public record Online(String login, String channel, Instant lastSeen) {
	}

	/** Utilisateurs en ligne, le plus récemment actif en premier. */
	public java.util.List<Online> onlineNow() {
		Instant limit = Instant.now().minus(Duration.ofMinutes(ONLINE_MINUTES));
		return lastSeen.entrySet().stream().filter(e -> !e.getValue().isBefore(limit)).map(e -> {
			int sep = e.getKey().indexOf(':');
			return new Online(e.getKey().substring(sep + 1), e.getKey().substring(0, sep), e.getValue());
		}).sorted(java.util.Comparator.comparing(Online::lastSeen).reversed()).toList();
	}

	private Map<String, Long> month() {
		if (monthCache != null && monthCachedAt.plus(MONTH_CACHE).isAfter(Instant.now())) {
			return monthCache;
		}
		Map<String, Long> m = new LinkedHashMap<>();
		try {
			jdbc.query("SELECT count(DISTINCT app_user_id) AS users, count(*) AS visits FROM user_activity_day "
					+ "WHERE day >= CAST(date_trunc('month', CURRENT_DATE) AS DATE)", rs -> {
						m.put("monthUsers", rs.getLong("users"));
						m.put("monthVisits", rs.getLong("visits"));
					});
		} catch (Exception ex) {
			log.error("Compteurs de fréquentation indisponibles : {}", ex.getMessage());
			return monthCache != null ? monthCache : Map.of();
		}
		monthCache = m;
		monthCachedAt = Instant.now();
		return m;
	}

	/** Purge quotidienne au-delà de {@value #RETENTION_MONTHS} mois, une seule instance. */
	@Scheduled(cron = "${lstracker.connection-log.purge-cron:0 30 3 * * *}")
	public void purge() {
		Optional<ClusterLock.Held> lock = clusterLock.tryAcquire("connection-log-purge");
		if (lock.isEmpty()) {
			return;
		}
		try (ClusterLock.Held held = lock.get()) {
			MapSqlParameterSource p = new MapSqlParameterSource("months", RETENTION_MONTHS);
			int logs = jdbc.update("DELETE FROM connection_log "
					+ "WHERE created_at < LOCALTIMESTAMP - make_interval(months => :months)", p);
			int days = jdbc.update("DELETE FROM user_activity_day "
					+ "WHERE day < CURRENT_DATE - make_interval(months => :months)", p);
			if (logs + days > 0) {
				log.info("Purge du journal des connexions : {} connexions et {} visites de plus de {} mois", logs,
						days, RETENTION_MONTHS);
			}
		} catch (Exception ex) {
			log.error("Purge du journal des connexions impossible : {}", ex.getMessage());
		}
	}

	private static String truncate(String s, int max) {
		return s == null || s.length() <= max ? s : s.substring(0, max);
	}
}
