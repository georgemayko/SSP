/**
 * Licensed to Apereo under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Apereo licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jasig.ssp.service.impl; // NOPMD by jon.adams

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import javax.mail.SendFailedException;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang.StringUtils;
import org.jasig.ssp.dao.EarlyAlertDao;
import org.jasig.ssp.factory.EarlyAlertSearchResultTOFactory;
import org.jasig.ssp.model.EarlyAlert;
import org.jasig.ssp.model.EarlyAlertSearchResult;
import org.jasig.ssp.model.Message;
import org.jasig.ssp.model.ObjectStatus;
import org.jasig.ssp.model.Person;
import org.jasig.ssp.model.SubjectAndBody;
import org.jasig.ssp.model.reference.Campus;
import org.jasig.ssp.service.AbstractPersonAssocAuditableService;
import org.jasig.ssp.service.EarlyAlertService;
import org.jasig.ssp.service.MessageService;
import org.jasig.ssp.service.ObjectNotFoundException;
import org.jasig.ssp.service.PersonService;
import org.jasig.ssp.service.SecurityService;
import org.jasig.ssp.service.reference.ConfigService;
import org.jasig.ssp.service.reference.MessageTemplateService;
import org.jasig.ssp.transferobject.EarlyAlertSearchResultTO;
import org.jasig.ssp.transferobject.PagedResponse;
import org.jasig.ssp.transferobject.form.EarlyAlertSearchForm;
import org.jasig.ssp.transferobject.reports.EarlyAlertCourseCountsTO;
import org.jasig.ssp.transferobject.reports.EarlyAlertReasonCountsTO;
import org.jasig.ssp.transferobject.reports.EarlyAlertStudentReportTO;
import org.jasig.ssp.transferobject.reports.EarlyAlertStudentSearchTO;
import org.jasig.ssp.transferobject.reports.EntityCountByCoachSearchForm;
import org.jasig.ssp.transferobject.reports.EntityStudentCountByCoachTO;
import org.jasig.ssp.util.DateTimeUtils;
import org.jasig.ssp.util.collections.Triple;
import org.jasig.ssp.util.sort.PagingWrapper;
import org.jasig.ssp.util.sort.SortingAndPaging;
import org.jasig.ssp.web.api.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * EarlyAlert service implementation
 * 
 * @author jon.adams
 * 
 */
@Service
@Transactional
public class EarlyAlertServiceImpl extends // NOPMD
		AbstractPersonAssocAuditableService<EarlyAlert>
		implements EarlyAlertService {

	@Autowired //1
	private transient EarlyAlertDao dao;
	@Autowired //1
	private transient ConfigService configService;
	@Autowired //1
	private transient MessageService messageService;
	@Autowired //1
	private transient MessageTemplateService messageTemplateService;
	@Autowired //1
	private transient PersonService personService;
	@Autowired //1
	private transient SecurityService securityService;
	@Autowired //1
	private transient EarlyAlertSearchResultTOFactory searchResultFactory;
	
	@Autowired //1
	private SendMessageToAdvisor sendMessageToAdvisor;
	@Autowired //1
	private EarlyAlertFillTemplateParameters earlyAlertFillTemplateParameters;
	@Autowired //1
	private SendAllEarlyAlertReminderNotifications sendAllEarlyAlertReminderNotifications;
	@Autowired
	private EnsureValidAlertedOnPersonStateOrFail ensureValidAlertedOnPersonStateOrFail;
	@Autowired
	private EarlyAlertMergerHelper earlyAlertMergerHelper;
	//1
	Function<EarlyAlert, Map<String, Object>> functionFillTemplateParameters = (earlyAlertParam) -> this.fillTemplateParameters(earlyAlertParam);
	
	private static final Logger LOGGER = LoggerFactory
			.getLogger(EarlyAlertServiceImpl.class);

	
	@Override
	protected EarlyAlertDao getDao() {
		return dao;
	}

	@Override
	//12
	@Transactional(rollbackFor = { ObjectNotFoundException.class, ValidationException.class })
	public EarlyAlert create(@NotNull final EarlyAlert earlyAlert) //1
			throws ObjectNotFoundException, ValidationException {
		// Validate objects
		EarlyAlertCreateValidation.execute(earlyAlert);
		final Person student = earlyAlert.getPerson();
		final UUID assignedAdvisor = earlyAlert.getEarlyAlertAdvisor();
		//1
		if (student.getCoach() == null
				|| assignedAdvisor.equals(student.getCoach().getId())) {
			student.setCoach(personService.get(assignedAdvisor));
		}
		
		ensureValidAlertedOnPersonStateNoFail(student);

		// Create alert
		final EarlyAlert saved = getDao().save(earlyAlert);

		// Send e-mail to assigned advisor (coach)
		//1
		try {
			sendMessageToAdvisor(saved, earlyAlert.getEmailCC());
		//1
		} catch (final SendFailedException e) {
			LOGGER.warn(
					"Could not send Early Alert message to advisor.",
					e);
			throw new ValidationException(
					"Early Alert notification e-mail could not be sent to advisor. Early Alert was NOT created.",
					e);
		}

		// Send e-mail CONFIRMATION to faculty
		// 1
		try {
			sendConfirmationMessageToFaculty(saved);
			//1
		} catch (final SendFailedException e) {
			LOGGER.warn(
					"Could not send Early Alert confirmation to faculty.",
					e);
			throw new ValidationException(
					"Early Alert confirmation e-mail could not be sent. Early Alert was NOT created.",
					e);
		}

		return saved;
	}

	@Override //6
	public void closeEarlyAlert(UUID earlyAlertId)
			throws ObjectNotFoundException, ValidationException { //2
		final EarlyAlert earlyAlert = getDao().get(earlyAlertId);

		// DAOs don't implement ObjectNotFoundException consistently and we'd
		// rather they not implement it at all, so a small attempt at 'future
		// proofing' here
		//1
		if ( earlyAlert == null ) {
			throw new ObjectNotFoundException(earlyAlertId, EarlyAlert.class.getName());
		}
		
		boolean ableToClose = earlyAlert.tryToClose(securityService.currentUser());
		if(ableToClose) {
			// This save will result in a Hib session flush, which works fine with
			// our current usage. Future use cases might prefer to delay the
			// flush and we can address that when the time comes. Might not even
			// need to change anything here if it turns out nothing actually
			// *depends* on the flush.
			getDao().save(earlyAlert);
		}
	}
	
	@Override //6
	public void openEarlyAlert(UUID earlyAlertId)
			throws ObjectNotFoundException, ValidationException { //2
		final EarlyAlert earlyAlert = getDao().get(earlyAlertId);

		// DAOs don't implement ObjectNotFoundException consistently and we'd
		// rather they not implement it at all, so a small attempt at 'future
		// proofing' here
		//1
		if ( earlyAlert == null ) {
			throw new ObjectNotFoundException(earlyAlertId, EarlyAlert.class.getName());
		}
		
		boolean ableToOpen = earlyAlert.tryToOpen(securityService.currentUser());
		if(ableToOpen) {	
			// This save will result in a Hib session flush, which works fine with
			// our current usage. Future use cases might prefer to delay the
			// flush and we can address that when the time comes. Might not even
			// need to change anything here if it turns out nothing actually
			// *depends* on the flush.
			getDao().save(earlyAlert);
		}
	}

	@Override 
	public EarlyAlert save(@NotNull final EarlyAlert obj)
			throws ObjectNotFoundException { //1
		final EarlyAlert current = getDao().get(obj.getId());
		current.merge(obj, earlyAlertMergerHelper);
		return getDao().save(current);
	}

	@Override
	public PagingWrapper<EarlyAlert> getAllForPerson(final Person person,
			final SortingAndPaging sAndP) {
		return getDao().getAllForPersonId(person.getId(), sAndP);
	}

	/**
	 * Business logic to determine the advisor that is assigned to the student
	 * for this Early Alert.
	 * 
	 * @param earlyAlert
	 *            EarlyAlert instance
	 * @throws ValidationException
	 *             If Early Alert, Student, and/or system information could not
	 *             determine the advisor for this student.
	 * @return The assigned advisor
	 */
	//3
	private void ensureValidAlertedOnPersonStateNoFail(Person person) { //1
		//1
		try {
			ensureValidAlertedOnPersonStateOrFail.execute(person);
		//1	
		} catch ( Exception e ) {
			LOGGER.error("Unable to set a program status or student type on "
					+ "person '{}'. This is likely to prevent that person "
					+ "record from appearing in caseloads, student searches, "
					+ "and some reports.", person.getId(), e);
		}
	}

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
	private void sendMessageToAdvisor(@NotNull final EarlyAlert earlyAlert, // NOPMD
			final String emailCC) throws ObjectNotFoundException,
			SendFailedException, ValidationException {
		sendMessageToAdvisor.execute(earlyAlert, emailCC, functionFillTemplateParameters);
	}

	@Override
	public void sendMessageToStudent(@NotNull final EarlyAlert earlyAlert)
			throws ObjectNotFoundException, SendFailedException,
			ValidationException {
		if (earlyAlert == null) {
			throw new IllegalArgumentException("EarlyAlert was missing.");
		}

		if (earlyAlert.getPerson() == null) {
			throw new IllegalArgumentException("EarlyAlert.Person is missing.");
		}

		final Person person = earlyAlert.getPerson();
		final SubjectAndBody subjAndBody = messageTemplateService
				.createEarlyAlertToStudentMessage(fillTemplateParameters(earlyAlert));

		Set<String> watcheremails = new HashSet<String>(person.getWatcherEmailAddresses());
		// Create and queue the message
		final Message message = messageService.createMessage(person, org.springframework.util.StringUtils.arrayToCommaDelimitedString(watcheremails
				.toArray(new String[watcheremails.size()])),
				subjAndBody);

		LOGGER.info("Message {} created for EarlyAlert {}", message, earlyAlert);
	}

	/**
	 * Send confirmation e-mail ({@link Message}) to the faculty who created
	 * this alert.
	 * 
	 * @param earlyAlert
	 *            Early Alert
	 * @throws ObjectNotFoundException
	 * @throws SendFailedException
	 * @throws ValidationException
	 */
	private void sendConfirmationMessageToFaculty(final EarlyAlert earlyAlert)
			throws ObjectNotFoundException, SendFailedException,
			ValidationException {
		new SendConfirmationMessageToFaculty().execute(earlyAlert, functionFillTemplateParameters);
	}

	@Override
	public Map<String, Object> fillTemplateParameters(
			@NotNull final EarlyAlert earlyAlert) {
		return earlyAlertFillTemplateParameters.execute(earlyAlert);
	}

	@Override
	public void applyEarlyAlertCounts(Person person) {
		if ( person == null ) {
			return; // can occur in some legit person lookup call paths
		}
	}

	@Override
	public Map<UUID, Number> getCountOfActiveAlertsForPeopleIds(
			final Collection<UUID> peopleIds) {
		return dao.getCountOfActiveAlertsForPeopleIds(peopleIds);
	}

	@Override
	public Map<UUID, Number> getCountOfClosedAlertsForPeopleIds(
			final Collection<UUID> peopleIds) {
		return dao.getCountOfClosedAlertsForPeopleIds(peopleIds);
	}
	
	@Override
	public Long getCountOfEarlyAlertsForSchoolIds(
			final Collection<String> schoolIds, Campus campus) {
		return dao.getCountOfAlertsForSchoolIds(schoolIds, campus);
	}

	@Override
	public Long getEarlyAlertCountForCoach(Person coach, Date createDateFrom, Date createDateTo, List<UUID> studentTypeIds) {
		return dao.getEarlyAlertCountForCoach(coach, createDateFrom, createDateTo, studentTypeIds);
	}

	@Override
	public Long getStudentEarlyAlertCountForCoach(Person coach, Date createDateFrom, Date createDateTo, List<UUID> studentTypeIds) {
		return dao.getStudentEarlyAlertCountForCoach(coach, createDateFrom, createDateTo, studentTypeIds);
	}
	
	@Override
	public Long getEarlyAlertCountForCreatedDateRange(String termCode, Date createDatedFrom, Date createdDateTo, Campus campus, String rosterStatus) {
		return dao.getEarlyAlertCountForCreatedDateRange(termCode, createDatedFrom, createdDateTo, campus, rosterStatus);
	}

	@Override
	public Long getClosedEarlyAlertCountForClosedDateRange(Date closedDateFrom, Date closedDateTo, Campus campus, String rosterStatus) {
		return dao.getClosedEarlyAlertCountForClosedDateRange(closedDateFrom, closedDateTo, campus, rosterStatus);
	}

	@Override
	public Long getClosedEarlyAlertsCountForEarlyAlertCreatedDateRange(String termCode, Date createDatedFrom, Date createdDateTo, Campus campus, String rosterStatus) {
		return dao.getClosedEarlyAlertsCountForEarlyAlertCreatedDateRange(termCode, createDatedFrom, createdDateTo, campus, rosterStatus);
	}

	@Override
	public Long getStudentCountForEarlyAlertCreatedDateRange(String termCode, Date createDatedFrom,
															 Date createdDateTo, Campus campus, String rosterStatus) {
		return dao.getStudentCountForEarlyAlertCreatedDateRange(termCode, createDatedFrom, createdDateTo, campus, rosterStatus);
	}

	@Override
	public PagingWrapper<EarlyAlertStudentReportTO> getStudentsEarlyAlertCountSetForCriteria(
			EarlyAlertStudentSearchTO earlyAlertStudentSearchTO,
			SortingAndPaging createForSingleSort) {
		return dao.getStudentsEarlyAlertCountSetForCriteria(earlyAlertStudentSearchTO, createForSingleSort);
	}

    @Override
    public  List<EarlyAlertCourseCountsTO> getStudentEarlyAlertCountSetPerCourses(
            String termCode, Date createdDateFrom, Date createdDateTo, Campus campus, ObjectStatus objectStatus ) {
        return dao.getStudentEarlyAlertCountSetPerCourses(termCode, createdDateFrom, createdDateTo, campus, objectStatus);
    }
	@Override
	public Long getStudentEarlyAlertCountSetPerCoursesTotalStudents(
			String termCode, Date createdDateFrom, Date createdDateTo, Campus campus, ObjectStatus objectStatus ) {
		return dao.getStudentEarlyAlertCountSetPerCoursesTotalStudents(termCode, createdDateFrom, createdDateTo, campus, objectStatus);
	}


    @Override
    public  List<Triple<String, Long, Long>> getEarlyAlertReasonTypeCountByCriteria(
            Campus campus, String termCode, Date createdDateFrom, Date createdDateTo, ObjectStatus status) {
        return dao.getEarlyAlertReasonTypeCountByCriteria(campus, termCode, createdDateFrom, createdDateTo, status);
    }

    @Override
    public List<EarlyAlertReasonCountsTO> getStudentEarlyAlertReasonCountByCriteria(
            String termCode, Date createdDateFrom, Date createdDateTo, Campus campus, ObjectStatus objectStatus) {
        return dao.getStudentEarlyAlertReasonCountByCriteria(termCode, createdDateFrom, createdDateTo, campus, objectStatus);
    }

	@Override
	public Long getStudentEarlyAlertReasonCountByCriteriaTotalStudents(
			String termCode, Date createdDateFrom, Date createdDateTo, Campus campus, ObjectStatus objectStatus) {
		return dao.getStudentEarlyAlertReasonCountByCriteriaTotalStudents(termCode, createdDateFrom, createdDateTo, campus, objectStatus);
	}


	@Override
	public PagingWrapper<EntityStudentCountByCoachTO> getStudentEarlyAlertCountByCoaches(EntityCountByCoachSearchForm form) {
		return dao.getStudentEarlyAlertCountByCoaches(form);
	}
	
	@Override
	public Long getEarlyAlertCountSetForCriteria(EarlyAlertStudentSearchTO searchForm){
		return dao.getEarlyAlertCountSetForCriteria(searchForm);
	}
	
	@Override
	public void sendAllEarlyAlertReminderNotifications() {
		sendAllEarlyAlertReminderNotifications.execute(getMinimumResponseComplianceDate());
	}
	
	public Map<UUID,Number> getResponsesDueCountEarlyAlerts(List<UUID> personIds){
		Date lastResponseDate = getMinimumResponseComplianceDate();
		if(lastResponseDate == null)
			return new HashMap<UUID,Number>();
		return dao.getResponsesDueCountEarlyAlerts(personIds, lastResponseDate);
	}
	
	private Date getMinimumResponseComplianceDate(){
		final String numVal = configService
				.getByNameNull("maximum_days_before_early_alert_response");
		if(StringUtils.isBlank(numVal))
			return null;
		Integer allowedDaysPastResponse = Integer.parseInt(numVal);
		
	    return DateTimeUtils.getDateOffsetInDays(new Date(), -allowedDaysPastResponse);
				
	}

	@Override
	public PagedResponse<EarlyAlertSearchResultTO> searchEarlyAlert(
			EarlyAlertSearchForm form) {
		PagingWrapper<EarlyAlertSearchResult> models = dao.searchEarlyAlert(form);
		return new PagedResponse<EarlyAlertSearchResultTO>(true,
				models.getResults(), searchResultFactory.asTOList(models.getRows()));	
	}
}
