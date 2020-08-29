package org.jasig.ssp.service.impl;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import javax.mail.SendFailedException;

import org.jasig.ssp.model.EarlyAlert;
import org.jasig.ssp.model.Message;
import org.jasig.ssp.model.Person;
import org.jasig.ssp.model.SubjectAndBody;
import org.jasig.ssp.service.MessageService;
import org.jasig.ssp.service.ObjectNotFoundException;
import org.jasig.ssp.service.PersonService;
import org.jasig.ssp.service.reference.ConfigService;
import org.jasig.ssp.service.reference.MessageTemplateService;
import org.jasig.ssp.web.api.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SendConfirmationMessageToFaculty {
	@Autowired
	private ConfigService configService;
	@Autowired
	private PersonService personService;
	@Autowired
	private MessageTemplateService messageTemplateService;
	@Autowired
	private MessageService messageService;
	
	private static final Logger LOGGER = LoggerFactory
			.getLogger(SendConfirmationMessageToFaculty.class);
	
	public void execute(final EarlyAlert earlyAlert, Function<EarlyAlert, Map<String,Object>> functionFillTemplateParameters)
			throws ObjectNotFoundException, SendFailedException,
			ValidationException {
		if (earlyAlert == null) {
			throw new IllegalArgumentException("EarlyAlert was missing.");
		}

		if (earlyAlert.getPerson() == null) {
			throw new IllegalArgumentException("EarlyAlert.Person is missing.");
		}

        if (configService.getByNameOrDefaultValue("send_faculty_mail") != true) {
            LOGGER.debug("Skipping Faculty Early Alert Confirmation Email: Config Turned Off");
            return; //skip if faculty early alert email turned off
        }

		final UUID personId = earlyAlert.getCreatedBy().getId();
		Person person = personService.get(personId);
		if ( person == null ) {
			LOGGER.warn("EarlyAlert {} has no creator. Unable to send"
					+ " confirmation message to faculty.", earlyAlert);
		} else {
			final SubjectAndBody subjAndBody = messageTemplateService
					//.createEarlyAlertFacultyConfirmationMessage(fillTemplateParameters(earlyAlert));
					.createEarlyAlertFacultyConfirmationMessage(functionFillTemplateParameters.apply(earlyAlert));

			// Create and queue the message
			final Message message = messageService.createMessage(person, null,
					subjAndBody);

			LOGGER.info("Message {} created for EarlyAlert {}", message, earlyAlert);
		}
	}
}
