package org.jasig.ssp.service.impl;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.jasig.ssp.transferobject.reports.JournalCaseNotesStudentReportTO;

public class SortByStudentName {

	public static void execute(List<JournalCaseNotesStudentReportTO> toSort) {
		Collections.sort(toSort,  new Comparator<JournalCaseNotesStudentReportTO>() {
	        public int compare(JournalCaseNotesStudentReportTO p1, JournalCaseNotesStudentReportTO p2) {
	        	
	        	int value = p1.getLastName().compareToIgnoreCase(
	     	                    p2.getLastName());
	        	if(value != 0)
	        		return value;
	        	
	        	value = p1.getFirstName().compareToIgnoreCase(
 	                    p2.getFirstName());
		       if(value != 0)
        		 return value;
		       if(p1.getMiddleName() == null && p2.getMiddleName() == null)
		    	   return 0;
		       if(p1.getMiddleName() == null)
		    	   return -1;
		       if(p2.getMiddleName() == null)
		    	   return 1;
		       return p1.getMiddleName().compareToIgnoreCase(
	                    p2.getMiddleName());
	        }
	    });
	}
}
