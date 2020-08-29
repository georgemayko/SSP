package org.jasig.ssp.service.impl;

import java.util.UUID;

import javax.validation.constraints.NotNull;

import org.jasig.ssp.model.EarlyAlert;
import org.jasig.ssp.model.Person;
import org.jasig.ssp.web.api.validation.ValidationException;

public class EarlyAlertCreateValidation {

	public static void execute(@NotNull final EarlyAlert earlyAlert) throws ValidationException {
		// Validate objects
		if (earlyAlert == null) {
			throw new IllegalArgumentException("EarlyAlert must be provided.");
		}
		if (earlyAlert.getPerson() == null) {
			throw new ValidationException("EarlyAlert Student data must be provided.");
		}

		final Person student = earlyAlert.getPerson();

		// Figure student advisor or early alert coordinator
		final UUID assignedAdvisor = earlyAlert.getEarlyAlertAdvisor();
		if (assignedAdvisor == null) {
			throw new ValidationException(
					"Could not determine the Early Alert Advisor for student ID " + student.getId());
		}
	}

}
