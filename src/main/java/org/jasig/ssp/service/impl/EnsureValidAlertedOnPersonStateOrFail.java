package org.jasig.ssp.service.impl;

import java.util.Date;
import java.util.Set;

import org.jasig.ssp.model.ObjectStatus;
import org.jasig.ssp.model.Person;
import org.jasig.ssp.model.PersonProgramStatus;
import org.jasig.ssp.model.reference.ProgramStatus;
import org.jasig.ssp.model.reference.StudentType;
import org.jasig.ssp.service.ObjectNotFoundException;
import org.jasig.ssp.service.PersonProgramStatusService;
import org.jasig.ssp.service.reference.ProgramStatusService;
import org.jasig.ssp.service.reference.StudentTypeService;
import org.jasig.ssp.web.api.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EnsureValidAlertedOnPersonStateOrFail {
	
	@Autowired
	private PersonProgramStatusService personProgramStatusService;
	@Autowired
	private ProgramStatusService programStatusService;
	@Autowired
	private StudentTypeService studentTypeService;
	
	public void execute(Person person) //1
			throws ObjectNotFoundException, ValidationException { //2
		//1
		if ( person.getObjectStatus() != ObjectStatus.ACTIVE ) {
			person.setObjectStatus(ObjectStatus.ACTIVE);
		}
		//1
		final ProgramStatus programStatus =  programStatusService.getActiveStatus();
		//1
		if ( programStatus == null ) {
			throw new ObjectNotFoundException(
					"Unable to find a ProgramStatus representing \"activeness\".",
					"ProgramStatus");
		}
		//1
		Set<PersonProgramStatus> programStatuses =
				person.getProgramStatuses();
		//1
		if ( programStatuses == null || programStatuses.isEmpty() ) {
			PersonProgramStatus personProgramStatus = new PersonProgramStatus();
			personProgramStatus.setEffectiveDate(new Date());
			personProgramStatus.setProgramStatus(programStatus);
			personProgramStatus.setPerson(person);
			programStatuses.add(personProgramStatus);
			person.setProgramStatuses(programStatuses);
			// save should cascade, but make sure custom create logic fires
			personProgramStatusService.create(personProgramStatus);
		}
		//1
		if ( person.getStudentType() == null ) {
			//1
			StudentType studentType = studentTypeService.get(StudentType.EAL_ID);
			//1
			if ( studentType == null ) {
				throw new ObjectNotFoundException(
						"Unable to find a StudentType representing an early "
								+ "alert-assigned type.", "StudentType");
			}
			person.setStudentType(studentType);
		}
	}
}
