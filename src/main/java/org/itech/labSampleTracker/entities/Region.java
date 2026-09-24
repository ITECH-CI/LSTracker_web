/* 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 */
package org.itech.labSampleTracker.entities;

import lombok.*;
import jakarta.persistence.*;
import java.io.Serializable;

import java.util.List;

/**
 * Domain class for entity "Region"
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
@Table(name = "region")
public class Region implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	/** Désactivé plutôt que supprimé dès qu'il est référencé (cahier IV.f). */
	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	@OneToMany(mappedBy = "region")
	private List<District> listOfDistrict;

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(id);
		sb.append("|");
		sb.append(name);
		return sb.toString();
	}

}
