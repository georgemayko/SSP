package org.jasig.ssp.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.mail.SendFailedException;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang.StringUtils;
import org.jasig.ssp.model.EarlyAlert;
import org.jasig.ssp.model.EarlyAlertRouting;
import org.jasig.ssp.model.Message;
import org.jasig.ssp.model.ObjectStatus;
import org.jasig.ssp.model.Person;
import org.jasig.ssp.model.SubjectAndBody;
import org.jasig.ssp.service.EarlyAlertRoutingService;
import org.jasig.ssp.service.MessageService;
import org.jasig.ssp.service.ObjectNotFoundException;
import org.jasig.ssp.service.reference.MessageTemplateService;
import org.jasig.ssp.util.sort.PagingWrapper;
import org.jasig.ssp.util.sort.SortingAndPaging;
import org.jasig.ssp.web.api.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

@Service
public class SendMessageToAdvisor {
	
	@Autowired
	private transient MessageTemplateService messageTemplateService;
	
	@Autowired
	private transient MessageService messageService;
	
	@Autowired
	private transient EarlyAlertRoutingService earlyAlertRoutingService;
	
	private static final Logger LOGGER = LoggerFactory
			.getLogger(SendMessageToAdvisor.class);
	

	/**
	 * Send e-mail ({@link Message}) to the assigned advisor for the student.
	 * 
	 * @param earlyAlert
	 *            Early Alert
	 * @param emailCC
	 *            Email address to also CC this message
	 * @throws ObjectNotFoundException
	 * @throws SendFailedException
	 * @throws ValidationException
	 */
	public void execute(@NotNull final EarlyAlert earlyAlert, // NOPMD
			final String emailCC, Function<EarlyAlert, Map<String, Object>> functionFillTemplateParameters) throws ObjectNotFoundException,
			SendFailedException, ValidationException {
		if (earlyAlert == null) {
			throw new IllegalArgumentException("Early alert was missing.");
		}

		if (earlyAlert.getPerson() == null) {
			throw new IllegalArgumentException("EarlyAlert Person is missing.");
		}

		final Person person = earlyAlert.getPerson().getCoach();
		final SubjectAndBody subjAndBody = messageTemplateService
				.createEarlyAlertAdvisorConfirmationMessage(functionFillTemplateParameters.apply(earlyAlert));
		
		Set<String> watcherEmailAddresses = new HashSet<String>(earlyAlert.getPerson().getWatcherEmailAddresses());
		if(emailCC != null && !emailCC.isEmpty())
		{
			watcherEmailAddresses.add(emailCC);
		}
		if ( person == null ) {
			LOGGER.warn("Student {} had no coach when EarlyAlert {} was"
					+ " created. Unable to send message to coach.",
					earlyAlert.getPerson(), earlyAlert);
		} else {
			// Create and queue the message
			final Message message = messageService.createMessage(person, org.springframework.util.StringUtils.arrayToCommaDelimitedString(watcherEmailAddresses
					.toArray(new String[watcherEmailAddresses.size()])),
					subjAndBody);
			LOGGER.info("Message {} created for EarlyAlert {}", message, earlyAlert);
		}

		// Send same message to all applicable Campus Early Alert routing
		// entries
		final PagingWrapper<EarlyAlertRouting> routes = earlyAlertRoutingService
				.getAllForCampus(earlyAlert.getCampus(), new SortingAndPaging(
						ObjectStatus.ACTIVE));

        if (routes.getResults() > 0) {
			final ArrayList<String> alreadySent = Lists.newArrayList();
		
 			for (final EarlyAlertRouting route : routes.getRows()) {
                // Check that route applies
                if ( route.getEarlyAlertReason() == null ) {
                    throw new ObjectNotFoundException(
                            "EarlyAlertRouting missing EarlyAlertReason.", "EarlyAlertReason");
                }

                // Only routes that are for any of the Reasons in this EarlyAlert should be applied.
                if ( (earlyAlert.getEarlyAlertReasons() == null)
                        || !earlyAlert.getEarlyAlertReasons().contains(route.getEarlyAlertReason()) ) {
                    continue;
                }

                // Send e-mail to specific person
                final Person to = route.getPerson();
                if ( to != null && StringUtils.isNotBlank(to.getPrimaryEmailAddress()) ) {
                    //check if this alert has already been sent to this recipient, if so skip
                    if ( alreadySent.contains(route.getPerson().getPrimaryEmailAddress()) ) {
                        continue;
                    } else {
                        alreadySent.add(route.getPerson().getPrimaryEmailAddress());
                    }

                    final Message message = messageService.createMessage(to, null, subjAndBody);
                    LOGGER.info("Message {} for EarlyAlert {} also routed to {}",
                                new Object[]{message, earlyAlert, to}); // NOPMD
                }

				// Send e-mail to a group
				if ( !StringUtils.isEmpty(route.getGroupName()) && !StringUtils.isEmpty(route.getGroupEmail()) ) {
                    final Message message = messageService.createMessage(route.getGroupEmail(), null,subjAndBody);
					LOGGER.info("Message {} for EarlyAlert {} also routed to {}", new Object[] { message, earlyAlert, // NOPMD
									route.getGroupEmail() });
				}
			}
		}
	}
}
