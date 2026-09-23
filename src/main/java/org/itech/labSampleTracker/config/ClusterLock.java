package org.itech.labSampleTracker.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Verrou partagé entre toutes les instances de l'application, par verrou
 * consultatif PostgreSQL ({@code pg_try_advisory_lock}) : toutes les instances
 * utilisent la même base, donc un seul détenteur à la fois dans le cluster.
 * Aucune table ni dépendance supplémentaire.
 *
 * <p>Le verrou est lié à la connexion qui l'a pris : {@link Held} la garde
 * ouverte jusqu'à {@link Held#close()}. Si l'instance s'arrête brutalement,
 * PostgreSQL libère le verrou avec la connexion.
 */
@Component
public class ClusterLock {

	private static final Logger log = LoggerFactory.getLogger(ClusterLock.class);

	private final DataSource dataSource;

	public ClusterLock(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * Tente de prendre le verrou {@code name} sans attendre.
	 *
	 * @return le verrou détenu (à fermer), ou vide s'il est déjà pris ailleurs
	 */
	public Optional<Held> tryAcquire(String name) {
		Connection c = null;
		try {
			c = dataSource.getConnection();
			try (PreparedStatement ps = c.prepareStatement("SELECT pg_try_advisory_lock(hashtext(?))")) {
				ps.setString(1, name);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next() && rs.getBoolean(1)) {
						return Optional.of(new Held(c, name));
					}
				}
			}
			c.close();
			return Optional.empty();
		} catch (SQLException e) {
			closeQuietly(c);
			throw new IllegalStateException("Verrou « " + name + " » indisponible : " + e.getMessage(), e);
		}
	}

	private static void closeQuietly(Connection c) {
		if (c != null) {
			try {
				c.close();
			} catch (SQLException ignored) {
				// connexion déjà inutilisable : rien à faire
			}
		}
	}

	/** Verrou détenu ; {@link #close()} le libère et rend la connexion. */
	public static final class Held implements AutoCloseable {
		private final Connection connection;
		private final String name;

		private Held(Connection connection, String name) {
			this.connection = connection;
			this.name = name;
		}

		@Override
		public void close() {
			try (PreparedStatement ps = connection.prepareStatement("SELECT pg_advisory_unlock(hashtext(?))")) {
				ps.setString(1, name);
				ps.execute();
			} catch (SQLException e) {
				// La fermeture de la connexion ci-dessous libère aussi le verrou.
				log.warn("Libération du verrou « {} » : {}", name, e.getMessage());
			} finally {
				closeQuietly(connection);
			}
		}
	}
}
