/* 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:03 )
 */
package org.itech.labSampleTracker.entities;

import java.io.Serializable;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Domain class for entity "District"
 *
 * @author Pascal
 *
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
//@ToString
@Entity
@Table(name = "district")
public class District implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "region_id")
	private Integer regionId;

	/** Désactivé plutôt que supprimé dès qu'il est référencé (cahier IV.f). */
	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	@OneToMany(mappedBy = "district")
	private List<Site> listOfSite;

	@ManyToOne
	@JoinColumn(name = "region_id", referencedColumnName = "id", insertable = false, updatable = false)
	private Region region;

	@OneToMany(mappedBy = "district")
	private List<Lab> listOfLab;

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(id);
		sb.append("|");
		sb.append(name);
		sb.append("|");
		sb.append(regionId);
		return sb.toString();
	}

}
