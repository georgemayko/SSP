/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jasig.ssp.service.external.impl;

import org.jasig.ssp.model.external.ExternalStudentRecords;
import org.jasig.ssp.model.external.ExternalStudentRecordsLite;
import org.jasig.ssp.service.external.ExternalStudentRecordsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ExternalStudentRecordsServiceImpl implements
		ExternalStudentRecordsService {

	
	
	@Override
	public ExternalStudentRecords getExternalStudentRecordsByStudentId(
			String studentId) {
		
		return null;
	}

	@Override
	public ExternalStudentRecordsLite getExternalStudentRecordsLiteByStudentId(
			String studentId) {
		// TODO Auto-generated method stub
		return null;
	}

}