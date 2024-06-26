package com.karkinos.clinical.artifacts.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.karkinos.clinical.artifacts.util.Constants;
import com.karkinos.clinical.artifacts.util.OPConsultationHelper;
import com.karkinos.clinical.artifacts.vo.ClinicalData;

import ca.uhn.fhir.context.FhirContext;
import lombok.extern.slf4j.Slf4j;

/**
 * This class provides the business logic related to the patient.
 * 
 * @author kumari.anamika
 *
 */

@Service
@Slf4j
public class ClinicalDataServiceImpl implements ClinicalDataService {

	private FhirContext fhirContext = FhirContext.forR4();

	@Autowired
	private OPConsultationHelper opconsultationHelper;

	@Override
	public String clinicalDataGenerator(ClinicalData clinicalData) throws Exception {
		try {
			// clinicalArtifacts values: "DiagnosticReport","DischargeSummary",
			// "OpConsultRecord","Prescription","WellnessRecord","HealthDocument","Immunization"

			List<String> clinicalArtifacts = clinicalData.getClinicalArtifacts();

			Bundle bundle = new Bundle();
			if (!CollectionUtils.isEmpty(clinicalArtifacts)) {
				for (String clinicalArtifact : clinicalArtifacts) {
					// Create a new OPConsultation resource
					if (clinicalArtifact.equals(Constants.OP_CONSULT_RECORD)) {
						Date docDate = new Date();
						String hipPrefix = "";
						bundle = opconsultationHelper.createOPConsultationBundle(docDate, hipPrefix,
								fhirContext.newJsonParser(), clinicalData);
					}
				}
			} else {
				// generate clinical-data for rest of fields
			}

			String encodedString = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
			log.info("ClinicalDataServiceImpl::clinicalDataGenerator:: clinicalData encodedString: " + encodedString);

			return encodedString;
		} catch (Exception exception) {
			log.error("ClinicalDataServiceImpl::clinicalDataGenerator:: Exception: ", exception);
			throw new Exception("failed to generate data for the given request!");
		}
	}
}
