package org.itech.labSampleTracker.dto;

import java.util.Date;

import org.itech.labSampleTracker.enums.UserLevel;
import org.itech.labSampleTracker.enums.UserRole;
import org.itech.labSampleTracker.enums.UserType;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {
	private Integer id;
	private String lastName;
	private String firstName;
	@Pattern(regexp = "^[0-9a-zA-Z]{5,20}$", message = "Doit contenir au moins 5 caractères")
	private String login;
	@Pattern(regexp = "^((00225)?|(\\+225)?)([\\d]{10})$", message = "Numéro de phone invalide !")
	private String phoneContact;
	private UserRole role;
	private UserType userType;
	private UserLevel userLevel;
	@Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#&()–[{}]:;',?/*~$^+=<>]).{8,20}$", message = "Doit contenir au moins 8 caractères, une majuscule, une minuscule, un chiffre et un caractère spécial (! @ # $ % & *)")
	private String password;
	private String repassword;
	private boolean active = true;
	private boolean locked = false;
	@DateTimeFormat(pattern="dd/MM/YYYY")
	private Date passwordExpireAt;
	/** Régions affectées, saisies dans le formulaire pour un superviseur régional. */
	private java.util.List<Integer> regions;
	/** Districts affectés, saisis dans le formulaire pour un superviseur de district. */
	private java.util.List<Integer> districts;

}
