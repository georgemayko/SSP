package org.studentsuccessplan.ssp.web.api.reference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.studentsuccessplan.ssp.model.reference.Citizenship;
import org.studentsuccessplan.ssp.service.AuditableCrudService;
import org.studentsuccessplan.ssp.service.reference.CitizenshipService;
import org.studentsuccessplan.ssp.transferobject.reference.CitizenshipTO;

@PreAuthorize("hasRole('ROLE_USER')")
@Controller
@RequestMapping("/reference/citizenship")
public class CitizenshipController extends
		AbstractAuditableReferenceController<Citizenship, CitizenshipTO> {

	@Autowired
	protected transient CitizenshipService citizenshipService;

	@Override
	protected AuditableCrudService<Citizenship> getService() {
		return citizenshipService;
	}

	protected CitizenshipController() {
		super(Citizenship.class, CitizenshipTO.class);
	}
}
