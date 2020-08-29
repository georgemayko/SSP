package org.jasig.ssp.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jasig.ssp.config.EarlyAlertResponseReminderRecipientsConfig;
import org.jasig.ssp.dao.EarlyAlertDao;
import org.jasig.ssp.model.EarlyAlert;
import org.jasig.ssp.model.Person;
import org.jasig.ssp.model.SubjectAndBody;
import org.jasig.ssp.model.WatchStudent;
import org.jasig.ssp.model.reference.Campus;
import org.jasig.ssp.service.MessageService;
import org.jasig.ssp.service.ObjectNotFoundException;
import org.jasig.ssp.service.PersonService;
import org.jasig.ssp.service.reference.ConfigService;
import org.jasig.ssp.service.reference.MessageTemplateService;
import org.jasig.ssp.transferobject.EarlyAlertTO;
import org.jasig.ssp.transferobject.messagetemplate.EarlyAlertMessageTemplateTO;
import org.jasig.ssp.util.DateTimeUtils;
import org.jasig.ssp.util.collections.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

@Service
public class SendAllEarlyAlertReminderNotifications {
	
	@Autowired //1
	private transient EarlyAlertResponseReminderRecipientsConfig earReminderRecipientConfig;
	@Autowired
	private PersonService personService;
	@Autowired
	private MessageService messageService;
	@Autowired
	private MessageTemplateService messageTemplateService;
	@Autowired
	private ConfigService configService;
	
	@Autowired
	private transient EarlyAlertDao dao;
	
	private static final Logger LOGGER = LoggerFactory
			.getLogger(SendAllEarlyAlertReminderNotifications.class);

	public void execute( Date minimumResponseComplianceDate) {
		//Date lastResponseDate = getMinimumResponseComplianceDate();
		Date lastResponseDate = minimumResponseComplianceDate;
		// if no responseDate is given no emails are sent
		if (lastResponseDate == null) {
			return;
		}
		List<EarlyAlert> eaOutOfCompliance = dao.getResponseDueEarlyAlerts(lastResponseDate);
		
		Map<UUID, List<EarlyAlertMessageTemplateTO>> easByCoach = new HashMap<UUID, List<EarlyAlertMessageTemplateTO>>();
		Map<UUID, Person> coaches = new HashMap<UUID, Person>();
		final boolean includeCoachAsRecipient = this.earReminderRecipientConfig.includeCoachAsRecipient();
		final boolean includeEarlyAlertCoordinatorAsRecipient = this.earReminderRecipientConfig.includeEarlyAlertCoordinatorAsRecipient();
		final boolean includeEarlyAlertCoordinatorAsRecipientOnlyIfStudentHasNoCoach = this.earReminderRecipientConfig.includeEarlyAlertCoordinatorAsRecipientOnlyIfStudentHasNoCoach();
		LOGGER.info("Config: includeCoachAsRecipient(): {}", includeCoachAsRecipient);
		LOGGER.info("Config: includeEarlyAlertCoordinatorAsRecipient(): {}", includeEarlyAlertCoordinatorAsRecipient);
		LOGGER.info("Config: includeEarlyAlertCoordinatorAsRecipientOnlyIfStudentHasNoCoach(): {}", includeEarlyAlertCoordinatorAsRecipientOnlyIfStudentHasNoCoach);
		for (EarlyAlert earlyAlert: eaOutOfCompliance){
			final Set<Person> recipients = new HashSet<Person>();
			Person coach = earlyAlert.getPerson().getCoach();
			if (includeCoachAsRecipient) {
				if (coach == null) {
					LOGGER.warn("Early Alert with id: {} is associated with a person without a coach, so skipping email to coach.", earlyAlert.getId());
				} else {
					recipients.add(coach);
				}
			}
			if (includeEarlyAlertCoordinatorAsRecipient || (coach == null && includeEarlyAlertCoordinatorAsRecipientOnlyIfStudentHasNoCoach)) {
				final Campus campus = earlyAlert.getCampus();
				if (campus == null) {
					LOGGER.error("Early Alert with id: {} does not have valid a campus, so skipping email to EAC.", earlyAlert.getId());
				} else {
					final UUID earlyAlertCoordinatorId = campus.getEarlyAlertCoordinatorId();
					if ( earlyAlertCoordinatorId == null ) {
						LOGGER.error("Early Alert with id: {} has campus with no early alert coordinator, so skipping email to EAC.", earlyAlert.getId());
					} else {
						try {
							final Person earlyAlertCoordinator = personService.get(earlyAlertCoordinatorId);
							if (earlyAlertCoordinator == null) { // guard against change in behavior where ObjectNotFoundException is not thrown (which we've seen)
								LOGGER.error("Early Alert with id: {} has campus with an early alert coordinator with a bad ID ({}), so skipping email to EAC.", earlyAlert.getId(), earlyAlertCoordinatorId);
							} else {
								recipients.add(earlyAlertCoordinator);
							}
						} catch(ObjectNotFoundException exp){
							LOGGER.error("Early Alert with id: {} has campus with an early alert coordinator with a bad ID ({}), so skipping email to coach because no coach can be resolved.", new Object[] { earlyAlert.getId(), earlyAlertCoordinatorId, exp });
						}
					}
				}
			}
			LOGGER.debug("Early Alert: {}; Recipients: {}", earlyAlert.getId(), recipients);
			if (recipients.isEmpty()) {
				continue;
			} else {
				for (Person person : recipients) {
					// We've definitely got a coach by this point
					if (easByCoach.containsKey(person.getId())){
						final List<EarlyAlertMessageTemplateTO> coachEarlyAlerts = easByCoach.get(person.getId());
						coachEarlyAlerts.add(createEarlyAlertTemplateTO(earlyAlert));
					} else {
						coaches.put(person.getId(), person);
						final ArrayList<EarlyAlertMessageTemplateTO> eam = Lists.newArrayList();
						eam.add(createEarlyAlertTemplateTO(earlyAlert)); // add separately from newArrayList() call else list will be sized to 1
						easByCoach.put(person.getId(), eam);
					}
				}
			}
			List<WatchStudent> watchers = earlyAlert.getPerson().getWatchers();
			for (WatchStudent watcher : watchers) {
				if(easByCoach.containsKey(watcher.getPerson().getId())){
					final List<EarlyAlertMessageTemplateTO> coachEarlyAlerts = easByCoach.get(watcher.getPerson().getId());
					coachEarlyAlerts.add(createEarlyAlertTemplateTO( earlyAlert));
				}else{
					coaches.put(watcher.getPerson().getId(), watcher.getPerson());
					final ArrayList<EarlyAlertMessageTemplateTO> eam = Lists.newArrayList();
					eam.add(createEarlyAlertTemplateTO( earlyAlert)); // add separately from newArrayList() call else list will be sized to 1
					easByCoach.put(watcher.getPerson().getId(), eam);
				}				
			}
		}
		for(UUID coachId: easByCoach.keySet()){
			Map<String,Object> messageParams = new HashMap<String,Object>();

			Collections.sort(easByCoach.get(coachId), new Comparator<EarlyAlertTO>() {
				@Override
				public int compare(EarlyAlertTO p1, EarlyAlertTO p2) {
					Date p1Date = p1.getLastResponseDate();
					if (p1Date == null)
						p1Date = p1.getCreatedDate();
					Date p2Date = p2.getLastResponseDate();
					if (p2Date == null)
						p2Date = p2.getCreatedDate();
					return p1Date.compareTo(p2Date);
				}

			});

			Integer daysSince1900ResponseExpected =  DateTimeUtils.daysSince1900(lastResponseDate);
			List<Pair<EarlyAlertMessageTemplateTO,Integer>> earlyAlertTOPairs = new ArrayList<Pair<EarlyAlertMessageTemplateTO,Integer>>();
			for(EarlyAlertMessageTemplateTO ea:easByCoach.get(coachId)){
				Integer daysOutOfCompliance;
				if(ea.getLastResponseDate() != null){
					daysOutOfCompliance = daysSince1900ResponseExpected - DateTimeUtils.daysSince1900(ea.getLastResponseDate());
				}else{
				    daysOutOfCompliance = daysSince1900ResponseExpected - DateTimeUtils.daysSince1900(ea.getCreatedDate());
				}
				
				// Just in case attempt to only send emails for EA full day out of compliance
				if(daysOutOfCompliance >= 0)
					earlyAlertTOPairs.add(new Pair<EarlyAlertMessageTemplateTO,Integer>(ea, daysOutOfCompliance));
			}
			messageParams.put("earlyAlertTOPairs", earlyAlertTOPairs);
			messageParams.put("coach", coaches.get(coachId));
			messageParams.put("DateTimeUtils", DateTimeUtils.class);
			messageParams.put("termToRepresentEarlyAlert",
					configService.getByNameEmpty("term_to_represent_early_alert"));
			
			
			SubjectAndBody subjAndBody = messageTemplateService.createEarlyAlertResponseRequiredToCoachMessage(messageParams);
			try{
				messageService.createMessage(coaches.get(coachId), null, subjAndBody);
			}catch(Exception exp){
				LOGGER.error("Unable to send reminder emails to coach: " + coaches.get(coachId).getFullName() + "\n", exp);
			}
		}
		
	}
	
	private EarlyAlertMessageTemplateTO createEarlyAlertTemplateTO(EarlyAlert earlyAlert){
		Person creator = null;
		try{
			 creator = personService.get(earlyAlert.getCreatedBy().getId());
		}catch(ObjectNotFoundException exp){
			LOGGER.error("Early Alert with id: " + earlyAlert.getId() + " does not have valid creator: " + earlyAlert.getCreatedBy(), exp);
		}
		return new EarlyAlertMessageTemplateTO(earlyAlert, creator,earlyAlert.getPerson().getWatcherEmailAddresses());
	}
}
