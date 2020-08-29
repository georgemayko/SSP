package org.jasig.ssp.service.impl;

import java.util.HashSet;
import java.util.Set;

import org.jasig.ssp.model.EarlyAlert;
import org.jasig.ssp.model.reference.EarlyAlertReason;
import org.jasig.ssp.model.reference.EarlyAlertSuggestion;
import org.jasig.ssp.service.ObjectNotFoundException;
import org.jasig.ssp.service.PersonService;
import org.jasig.ssp.service.reference.EarlyAlertReasonService;
import org.jasig.ssp.service.reference.EarlyAlertSuggestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EarlyAlertMergerHelper {
	
	@Autowired
	private PersonService personService;
	
	@Autowired
	private EarlyAlertReasonService earlyAlertReasonService;
	
	@Autowired
	private EarlyAlertSuggestionService earlyAlertSuggestionService;
	
	public void execute(EarlyAlert current, EarlyAlert obj) throws ObjectNotFoundException  {
		
		current.setCourseName(obj.getCourseName());
		current.setCourseTitle(obj.getCourseTitle());
		current.setEmailCC(obj.getEmailCC());
		current.setCampus(obj.getCampus());
		current.setEarlyAlertReasonOtherDescription(obj
				.getEarlyAlertReasonOtherDescription());
		current.setComment(obj.getComment());
		current.setClosedDate(obj.getClosedDate());
		//1
		if ( obj.getClosedById() == null ) {
			current.setClosedBy(null);
			//1
		} else {
			//1
			current.setClosedBy(personService.get(obj.getClosedById()));
		}
		//1
		if (obj.getPerson() == null) {
			current.setPerson(null);
			//1
		} else {
			//1
			current.setPerson(personService.get(obj.getPerson().getId()));
		}
		//1
		final Set<EarlyAlertReason> earlyAlertReasons = new HashSet<EarlyAlertReason>();
		//1
		if (obj.getEarlyAlertReasons() != null) {
			//1
			for (final EarlyAlertReason reason : obj.getEarlyAlertReasons()) {
				earlyAlertReasons.add(earlyAlertReasonService.load(reason
						.getId()));
			}
		}

		current.setEarlyAlertReasons(earlyAlertReasons);
		//1
		final Set<EarlyAlertSuggestion> earlyAlertSuggestions = new HashSet<EarlyAlertSuggestion>();
		//1
		if (obj.getEarlyAlertSuggestions() != null) {
			for (final EarlyAlertSuggestion reason : obj
					.getEarlyAlertSuggestions()) {
				earlyAlertSuggestions.add(earlyAlertSuggestionService
						.load(reason
								.getId()));
			}
		}

		current.setEarlyAlertSuggestions(earlyAlertSuggestions);
		
	}
	
}
