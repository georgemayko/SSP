package org.jasig.ssp.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.jasig.ssp.dao.JournalEntryDao;
import org.jasig.ssp.dao.PersonDao;
import org.jasig.ssp.model.ObjectStatus;
import org.jasig.ssp.service.ObjectNotFoundException;
import org.jasig.ssp.transferobject.reports.BaseStudentReportTO;
import org.jasig.ssp.transferobject.reports.JournalCaseNotesStudentReportTO;
import org.jasig.ssp.transferobject.reports.JournalStepSearchFormTO;
import org.jasig.ssp.util.sort.PagingWrapper;
import org.jasig.ssp.util.sort.SortingAndPaging;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service //17 //TODO refatorar
public class GetJournalCaseNoteStudentReportTOsFromCriteria {
	
	@Autowired
	private transient JournalEntryDao dao; //1

	@Autowired
	private transient PersonDao personDao; //1

	
 	public List<JournalCaseNotesStudentReportTO> execute( //1
 			JournalStepSearchFormTO personSearchForm //1
 			, SortingAndPaging sAndP) //1
 					throws ObjectNotFoundException{ //1
 		 final List<JournalCaseNotesStudentReportTO> personsWithJournalEntries = dao.getJournalCaseNoteStudentReportTOsFromCriteria(personSearchForm, sAndP);
 		 final Map<String, JournalCaseNotesStudentReportTO> map = new HashMap<String, JournalCaseNotesStudentReportTO>();
 		 
 		 //1
 		 for(JournalCaseNotesStudentReportTO entry:personsWithJournalEntries){ 
 			 map.put(entry.getSchoolId(), entry);
 		 }
 		 //1
 		 final SortingAndPaging personSAndP = SortingAndPaging.createForSingleSortAll(ObjectStatus.ACTIVE, "lastName", "DESC") ;
 		 //2
 		 final PagingWrapper<BaseStudentReportTO> persons = personDao.getStudentReportTOs(personSearchForm, personSAndP);
 		
 		 //1
 		 if (persons == null) {
 			 return personsWithJournalEntries;
 		 }

 		 for (BaseStudentReportTO person:persons) { //1
 			 //1
			 if (!map.containsKey(person.getSchoolId()) && StringUtils.isNotBlank(person.getCoachSchoolId())) { 
				 boolean addStudent = true;
				//1
				 if (personSearchForm.getJournalSourceIds()!=null) {
					//1
					if (dao.getJournalCountForPersonForJournalSourceIds(person.getId(), personSearchForm.getJournalSourceIds()) == 0) {
						addStudent = false;
					}
				 }
			 	 if (addStudent) { //1
					 final JournalCaseNotesStudentReportTO entry = new JournalCaseNotesStudentReportTO(person);
					 personsWithJournalEntries.add(entry);
					 map.put(entry.getSchoolId(), entry);
				 }
 			}
 		 }
 		 //1
		 SortByStudentName.execute(personsWithJournalEntries);

 		 return personsWithJournalEntries;
 	}
}
