/* 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 */
package org.itech.labSampleTracker.entities;

import java.io.Serializable;
import java.util.Date;
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
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
* Domain class for entity "Site"
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
@Table(name = "site")
public class Site implements Serializable {
 
private static final long serialVersionUID = 1L;

	@Id
   @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id ;

    @Column(name="name", nullable=false, length=200)
    private String name ;

    @Column(name="dhis_code", nullable=false, length=10)
    private String dhisCode ;

    @Column(name="datim_code", length=20)
    private String datimCode ;

    @Column(name="district_id")
    private Integer districtId ;

    /** Désactivé plutôt que supprimé dès qu'il est référencé (cahier IV.f). */
    @Column(name="is_active", nullable=false)
    private Boolean isActive = true;

    @Column(name="longitude")
    private Double longitude ;

    @Column(name="latitude")
    private Double latitude ;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name="last_updated_at")
    private Date lastUpdatedAt ;


        @OneToMany(mappedBy="site")
    private List<SampleRetrieving> listOfSampleRetrieving ; 

    @OneToMany(mappedBy="site")
    private List<CircuitSite> listOfCircuitSite ; 

    @ManyToOne
    @JoinColumn(name="district_id", referencedColumnName="id", insertable=false, updatable=false)
    private District   district ; 


    public String toString() { 
        StringBuilder sb = new StringBuilder(); 
        sb.append(id);
        sb.append("|");
        sb.append(name);
        sb.append("|");
        sb.append(dhisCode);
        sb.append("|");
        sb.append(datimCode);
        sb.append("|");
        sb.append(districtId);
        sb.append("|");
        sb.append(longitude);
        sb.append("|");
        sb.append(latitude);
        sb.append("|");
        sb.append(lastUpdatedAt);
        return sb.toString(); 
    } 

}



