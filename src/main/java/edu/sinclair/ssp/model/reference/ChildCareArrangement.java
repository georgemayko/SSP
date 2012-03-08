package edu.sinclair.ssp.model.reference;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Table;

@Entity
@Table(name = "child_care_arrangement", schema = "public")
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public class ChildCareArrangement extends AbstractReference{

	public ChildCareArrangement() {
		super();
	}
	
	public ChildCareArrangement(UUID id) {
		super(id);
	}
	
	public ChildCareArrangement(UUID id, String name) {
		super(id, name);
	}

	public ChildCareArrangement(UUID id, String name, String description) {
		super(id, name, description);
	}

}

