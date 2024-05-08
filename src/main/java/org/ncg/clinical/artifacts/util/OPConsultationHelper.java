package org.ncg.clinical.artifacts.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Type;
import org.ncg.clinical.artifacts.vo.AllergyIntoleranceRequest;
import org.ncg.clinical.artifacts.vo.ClinicalData;
import org.ncg.clinical.artifacts.vo.CoMorbidity;
import org.ncg.clinical.artifacts.vo.Diagnostic;
import org.ncg.clinical.artifacts.vo.ObservationWomenHealth;
import org.ncg.clinical.artifacts.vo.Test;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import ca.uhn.fhir.parser.IParser;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.PostConstruct;

@Service
public class OPConsultationHelper {

	private Map<String, String> indicatorLoincCodeMap = new HashMap<>();

	@PostConstruct
	public void init() throws Exception {
		// Initialize the map in the constructor
		indicatorLoincCodeMap.put("2 D ECHO with PASP".toLowerCase(), "34552-0");
		indicatorLoincCodeMap.put("FDG PETCT".toLowerCase(), "81553-0");
		indicatorLoincCodeMap.put("MRI brain".toLowerCase(), "24590-2");
		indicatorLoincCodeMap.put("Fiber optic bronchoscopy".toLowerCase(), "18744-3");
		indicatorLoincCodeMap.put("Endobronchial ultrasound with ROSE reports".toLowerCase(), "100231-0");
		indicatorLoincCodeMap.put("Pulmonary function tests with DLCO".toLowerCase(), "58477-1");
		indicatorLoincCodeMap.put("V/Q scan in pneumonectomy".toLowerCase(), "39942-8");
		indicatorLoincCodeMap.put("6MWT".toLowerCase(), "64098-7");
		indicatorLoincCodeMap.put("Molecular markers/NGS as needed".toLowerCase(), "73977-1");
		indicatorLoincCodeMap.put("FNAC report".toLowerCase(), "87179-8");
		indicatorLoincCodeMap.put("CECT head neck thorax report/ PET Ct/ MRI".toLowerCase(), "24627-2");
	}

	public Bundle createOPConsultationBundle(Date docDate, String clinicalArtifactsType, String hipPrefix,
			IParser jsonParser, ClinicalData clinicalData) throws Exception {
		Bundle bundle = FHIRUtils.createBundle(docDate, clinicalArtifactsType, null);

		Composition opDoc = new Composition();
		opDoc.setId(Utils.generateId());
		opDoc.setDate(bundle.getTimestamp());
		opDoc.setMeta(Utils.getMeta(docDate, Constants.STRUCTURE_DEFINITION_OP_CONSULT_RECORD));
		opDoc.setLanguage(Constants.EN_IN);
		opDoc.setIdentifier(FHIRUtils.getIdentifier(opDoc.getId(), Constants.HTTPS_NDHM_IN_PHR));
		opDoc.setStatus(Composition.CompositionStatus.FINAL);
		opDoc.setType(getOPConsultationType());
		opDoc.setTitle(getCompositionDocumentTitle());
		FHIRUtils.addToBundleEntry(bundle, opDoc, false);

		if (Objects.nonNull(clinicalData)) {
			// add patient entry
			Patient patientResource = FHIRUtils.addPatientResourceToComposition(clinicalData, bundle, opDoc);

			// add sections entry
			opDoc.setSection(
					createCancerModuleSections(hipPrefix, jsonParser, bundle, opDoc, clinicalData, patientResource));
		}

		return bundle;
	}

	protected List<Composition.SectionComponent> createCancerModuleSections(String hipPrefix, IParser jsonParser,
			Bundle bundle, Composition opDoc, ClinicalData clinicalData, Patient patientResource) throws IOException {

		List<Composition.SectionComponent> sections = new ArrayList<>();

		// diagnostic
		if (Objects.nonNull(clinicalData.getDiagnostic())) {
			sections.add(createDiagnosticReportSection(bundle, opDoc, clinicalData.getDiagnostic(), patientResource,
					jsonParser, hipPrefix));
		}

		// oralCancer
		if (Objects.nonNull(clinicalData.getOralCancer())) {
			// create Medical History section and add condition resource
			Composition.SectionComponent oralCancerSection = createMedicalHistorySection(bundle, patientResource,
					Constants.ORAL_CANCER_CODE, Constants.ORAL_CANCER);

			for (Map.Entry<String, String> oralCancerDetail : clinicalData.getOralCancer().entrySet()) {
				DiagnosticReport report = getOralCancerReports(bundle, patientResource, oralCancerDetail);
				// Add reports as reference to the Chief complaint section
				oralCancerSection.getEntry().add(FHIRUtils.getReferenceToResource(report));
			}
			sections.add(oralCancerSection);
		}

		// lungCancer
		if (Objects.nonNull(clinicalData.getLungCancer())) {
			// create Medical History section and add condition resource
			Composition.SectionComponent lungCancerSection = createMedicalHistorySection(bundle, patientResource,
					Constants.LUNG_CANCER_CODE, Constants.LUNG_CANCER);

			for (Map.Entry<String, String> lungCancerDetail : clinicalData.getLungCancer().entrySet()) {
				DiagnosticReport report = getLungCancerReports(bundle, patientResource, lungCancerDetail);
				// Add the condition to the Chief complaint section
				lungCancerSection.getEntry().add(FHIRUtils.getReferenceToResource(report));
			}
			sections.add(lungCancerSection);
		}

		// co-morbidities
		if (Objects.nonNull(clinicalData.getCoMorbidities())) {
			for (CoMorbidity coMorbidityDetail : clinicalData.getCoMorbidities()) {
				Composition.SectionComponent coMorbiditySection = new Composition.SectionComponent();
				coMorbiditySection.setTitle(Constants.CO_MORBIDITIES);
				coMorbiditySection.setCode(getCoMorbiditiesCode(coMorbidityDetail.getName()));

				// Create a new Condition resource for the complaint
				Condition condition = new Condition();
				condition.setId(Utils.generateId());
				condition.setMeta(Utils.getMeta(new Date(), Constants.STRUCTURE_DEFINITION_CONDITION));

				// Set patient reference
				Reference patientRef = new Reference();
				patientRef.setReference("Patient/" + patientResource.getId());
				condition.setSubject(patientRef);

				// Set recorder reference
				Reference recorderRef = new Reference();
				recorderRef.setReference("Practitioner/" + UUID.randomUUID().toString());
				condition.setRecorder(recorderRef);

				// Set text
				Narrative narrative = new Narrative();
				narrative.setStatusAsString("generated");
				narrative.setDivAsString(
						"<div xmlns=\"http://www.w3.org/1999/xhtml\"><p><b>Generated Narrative: Condition</b><a name=\"example-02\"> </a></p><div style=\"display: inline-block; background-color: #d9e0e7; padding: 6px; margin: 4px; border: 1px solid #8da1b4; border-radius: 5px; line-height: 60%\"><p style=\"margin-bottom: 0px\">Resource Condition &quot;example-02&quot; </p><p style=\"margin-bottom: 0px\">Profile: <a href=\"StructureDefinition-Condition.html\">Condition</a></p></div><p><b>code</b>: Type 2 diabetes mellitus <span style=\"background: LightGoldenRodYellow; margin: 4px; border: 1px solid khaki\"> (<a href=\"https://browser.ihtsdotools.org/\">SNOMED CT</a>#44054006)</span></p><p><b>subject</b>: <a href=\"Patient-example-01.html\">Patient/example-01</a> &quot;&quot;</p><p><b>recorder</b>: <a href=\"Practitioner-example-01.html\">Practitioner/example-01</a> &quot;&quot;</p></div>");
				condition.setText(narrative);

				// set code
				condition.setCode(getCoMorbiditiesCode(coMorbidityDetail.getName()));

				FHIRUtils.addToBundleEntry(bundle, condition, true);

				// Add the condition to the Co-Morbidity section
				coMorbiditySection.addEntry(new Reference(condition));

				sections.add(coMorbiditySection);
				sections.add(createCoMorbiditiesSection(bundle, opDoc, coMorbidityDetail, patientResource, jsonParser,
						hipPrefix));
			}
		}

		// ObservationWomenHealth
		if (Objects.nonNull(clinicalData.getObservationWomenHealth())) {
			for (ObservationWomenHealth observationWomenHealthDetail : clinicalData.getObservationWomenHealth()) {
				Composition.SectionComponent observationWomenHealthSection = new Composition.SectionComponent();
				observationWomenHealthSection.setTitle(Constants.OBSERVATION_WOMEN_HEALTH);
				observationWomenHealthSection
						.setCode(getObservationWomenHealthCode(observationWomenHealthDetail.getName()));

				// Create a new Condition resource for the complaint
				Observation observation = new Observation();
				observation.setId(Utils.generateId());
				observation.setMeta(Utils.getMeta(new Date(), Constants.STRUCTURE_DEFINITION_OBSERVATION_WOMEN_HEALTH));
				observation.setStatus(Observation.ObservationStatus.FINAL);

				// Set patient reference
				Reference patientRef = new Reference();
				patientRef.setReference("Patient/" + patientResource.getId());
				observation.setSubject(patientRef);

				// Set performer references
				Reference recorderRef = new Reference();
				recorderRef.setReference("Practitioner/" + UUID.randomUUID().toString());

				List<Reference> performers = new ArrayList<>();
				performers.add(recorderRef);
				observation.setPerformer(performers);

				// Set text
				Narrative narrative = new Narrative();
				narrative.setStatusAsString("generated");
				narrative.setDivAsString(
						"<div xmlns=\"http://www.w3.org/1999/xhtml\"><p><b>Narrative with Details</b></p><p><b>id</b>: example-20</p><p><b>status</b>: final</p><p><b>code</b>: Age at menarche <span>(Details : LOINC code '42798-9' = 'Age at menarche', given as 'Age at menarche')</span></p><p><b>subject</b>: ABC</p><p><b>value</b>: 14 age</p></div>");
				observation.setText(narrative);

				// set code
				observation.setCode(getObservationWomenHealthCode(observationWomenHealthDetail.getName()));

				// set value
				observation.setValue(new org.hl7.fhir.r4.model.StringType(observationWomenHealthDetail.getValue()));

				// set effective date time
				observation.setEffective(getEffectiveObservationDate(new Date()));

				FHIRUtils.addToBundleEntry(bundle, observation, true);

				// Add the observation to the Observation Women Health section
				observationWomenHealthSection.addEntry(new Reference(observation));

				sections.add(observationWomenHealthSection);
				sections.add(createObservationWomenHealthSection(bundle, opDoc, observationWomenHealthDetail,
						patientResource, jsonParser, hipPrefix));
			}
		}

		// allergyIntolerance
		List<AllergyIntoleranceRequest> allergiesDetail = clinicalData.getAllergyIntolerance();
		if (Objects.nonNull(clinicalData.getAllergyIntolerance())) {
			for (AllergyIntoleranceRequest allergyDetail : allergiesDetail) {
				sections.add(createAllergiesSection(allergyDetail, bundle, opDoc, patientResource, jsonParser));
			}
		}

		return sections;
	}

	private Composition.SectionComponent createMedicalHistorySection(Bundle bundle, Patient patient, String loincCode,
			String cancerType) {
		// create Medical History section
		CodeableConcept medicalHistoryCode = FHIRUtils.getCodeableConcept(Constants.MEDICAL_HISTORY_SNOMED_CODE,
				Constants.SNOMED_SYSTEM_SCT, Constants.MEDICAL_HISTORY_SECTION, Constants.MEDICAL_HISTORY_SECTION);
		Composition.SectionComponent oralCancerSection = createSectionComponent(Constants.MEDICAL_HISTORY,
				medicalHistoryCode);

		// Create a new Condition resource for the complaint
		CodeableConcept conditionCode = FHIRUtils.getCodeableConcept(loincCode, Constants.LOINC_SYSTEM, cancerType,
				cancerType);
		Condition condition = createConditionResource(conditionCode);
		FHIRUtils.addToBundleEntry(bundle, condition, true);

		// Add the condition to the Chief complaint section
		oralCancerSection.addEntry(new Reference(condition));
		return oralCancerSection;
	}

	private Composition.SectionComponent createSectionComponent(String title, CodeableConcept code) {
		Composition.SectionComponent oralCancerSection = new Composition.SectionComponent();
		oralCancerSection.setTitle(title);
		oralCancerSection.setCode(code);
		return oralCancerSection;
	}

	private DiagnosticReport getLungCancerReports(Bundle bundle, Patient patient,
			Map.Entry<String, String> lungCancerDetail) throws IOException {
		String lungCancerIndicator = lungCancerDetail.getKey().toLowerCase();
		switch (lungCancerIndicator) {
		case "2 d echo with pasp":
		case "fdg petct":
		case "mri brain":
		case "fiber optic bronchoscopy":
		case "endobronchial ultrasound with rose reports":
		case "pulmonary function tests with dlco":
		case "v/q scan in pneumonectomy":
		case "6mwt":
		case "molecular markers/nsg as needed":
			return createDiagnosticReport(bundle, patient, lungCancerDetail.getKey(), lungCancerDetail.getValue(),
					indicatorLoincCodeMap, lungCancerIndicator);
		default:
			return null;
		}
	}

	private DiagnosticReport getOralCancerReports(Bundle bundle, Patient patient,
			Map.Entry<String, String> oralCancerDetail) throws IOException {
		String oralCancerIndicator = oralCancerDetail.getKey().toLowerCase();
		switch (oralCancerIndicator) {
		case "fnac report":
		case "cect head neck thorax report/ pet ct/ mri":
			return createDiagnosticReport(bundle, patient, oralCancerDetail.getKey(), oralCancerDetail.getValue(),
					indicatorLoincCodeMap, oralCancerIndicator);
		default:
			return null;
		}
	}

	private DiagnosticReport createDiagnosticReport(Bundle bundle, Patient patient, String reportType,
			String reportValue, Map<String, String> indicatorLoincCodeMap, String lungCancerIndicator)
			throws IOException {
		// Create a new CodeableConcept
		CodeableConcept code = new CodeableConcept();
		if (indicatorLoincCodeMap.containsKey(lungCancerIndicator)) {
			code = FHIRUtils.getCodeableConcept(indicatorLoincCodeMap.get(lungCancerIndicator), Constants.LOINC_SYSTEM,
					reportType, reportType);
		}
		// Create a new DiagnosticReport resource
		DiagnosticReport report = createDiagnosticReportResource(bundle, patient, code);

		// Create a new DocumentReference resource
		DocumentReference documentReference = createDocumentReferenceResource(reportType, reportValue, patient,
				reportType + " report");

		report.addResult(FHIRUtils.getReferenceToResource(documentReference));

		return report;
	}

	private Composition.SectionComponent createCoMorbiditiesSection(Bundle bundle, Composition composition,
			CoMorbidity coMorbidity, Patient patient, IParser jsonParser, String hipPrefix) {
		if (Utils.randomBool())
			return null;

		Composition.SectionComponent section = composition.addSection();
		section.setTitle(Constants.CO_MORBIDITIES);
		CodeableConcept coMorbidityCode = FHIRUtils.getCodeableConcept(coMorbidity.getName(),
				Constants.SNOMED_SYSTEM_SCT, Constants.CO_MORBIDITIES_SECTION, Constants.CO_MORBIDITIES_SECTION);
		section.setCode(coMorbidityCode);

		return section;
	}

	private Composition.SectionComponent createObservationWomenHealthSection(Bundle bundle, Composition composition,
			ObservationWomenHealth observationWomenHealth, Patient patient, IParser jsonParser, String hipPrefix) {
		if (Utils.randomBool())
			return null;

		Composition.SectionComponent section = composition.addSection();
		section.setTitle(Constants.OBSERVATION_WOMEN_HEALTH);
		CodeableConcept observationWomenHealthCode = FHIRUtils.getCodeableConcept(observationWomenHealth.getName(),
				Constants.LOINC_SYSTEM, Constants.OBSERVATION_WOMEN_HEALTH_SECTION,
				Constants.OBSERVATION_WOMEN_HEALTH_SECTION);
		section.setCode(observationWomenHealthCode);
		return section;
	}

	private Composition.SectionComponent createDiagnosticReportSection(Bundle bundle, Composition composition,
			Diagnostic diagnostic, Patient patient, IParser jsonParser, String hipPrefix) throws IOException {
		if (Utils.randomBool())
			return null;

		CodeableConcept diagnosticReportCode = FHIRUtils.getCodeableConcept(Constants.DR_SNOMED_CODE,
				Constants.SNOMED_SYSTEM_SCT, Constants.DIAGNOSTIC_REPORT, Constants.DIAGNOSTIC_REPORT);
		Composition.SectionComponent diagnosticReportSection = createSectionComponent(Constants.DIAGNOSTIC_REPORTS,
				diagnosticReportCode);

		if (Objects.nonNull(diagnostic.getCbc()) && Objects.nonNull(diagnostic.getCbc().getHemoglobin())) {
			// Create a new DiagnosticReport resource
			CodeableConcept codeCBC = FHIRUtils.getCodeableConcept(Constants.DR_CBC_SNOMED_CODE,
					Constants.SNOMED_SYSTEM_SCT, Constants.DR_CBC, Constants.DR_CBC);

			// Create an Observation for haemoglobin
			CodeableConcept haemoglobinCode = FHIRUtils.getCodeableConcept(Constants.DR_HAEMOGLOBIN_CODE,
					Constants.LOINC_SYSTEM, Constants.DR_HAEMOGLOBIN, Constants.DR_HAEMOGLOBIN);
			createDiagnosticReportAndObservation(bundle, composition, diagnostic, patient, diagnosticReportSection,
					codeCBC, Constants.GRAM_PER_DECILITER, diagnostic.getCbc().getHemoglobin(), haemoglobinCode);
		}

		if (Objects.nonNull(diagnostic.getBiopsyHistopathologyReport())) {
			// Create a new DiagnosticReport resource
			CodeableConcept code = FHIRUtils.getCodeableConcept(Constants.BIOPSY_HISTOPATHOLOGY_SNOMED_CODE,
					Constants.SNOMED_SYSTEM_SCT, Constants.BIOPSY_HISTOPATHOLOGY_REPORT,
					Constants.BIOPSY_HISTOPATHOLOGY_REPORT);
			// Create a new DiagnosticReport resource
			DiagnosticReport report = createDiagnosticReportResource(bundle, patient, code);

			// Create a new DocumentReference resource
			DocumentReference documentReference = createDocumentReferenceResource(Constants.BIOPSY_HISTOPATHOLOGY,
					diagnostic.getBiopsyHistopathologyReport(), patient, Constants.BIOPSY_HISTOPATHOLOGY);

			report.addResult(FHIRUtils.getReferenceToResource(documentReference));

			diagnosticReportSection.getEntry().add(FHIRUtils.getReferenceToResource(report));
		}

		if (Objects.nonNull(diagnostic.getBioChemistry())) {
			if (Objects.nonNull(diagnostic.getBioChemistry().getLipidProfile())) {
				if (StringUtils.isNotBlank(diagnostic.getBioChemistry().getLipidProfile().getAttachment())) {
					// Create a new DiagnosticReport resource
					CodeableConcept code = FHIRUtils.getCodeableConcept(Constants.BIO_CHEMISTRY_SNOMED_CODE,
							Constants.SNOMED_SYSTEM_SCT, Constants.BIO_CHEMISTRY, Constants.BIO_CHEMISTRY);
					// Create a new DiagnosticReport resource
					DiagnosticReport report = createDiagnosticReportResource(bundle, patient, code);

					// Create a new DocumentReference resource
					DocumentReference documentReference = createDocumentReferenceResource(Constants.LIPID_PROFILE,
							diagnostic.getBioChemistry().getLipidProfile().getAttachment(), patient,
							Constants.LIPID_PROFILE);

					report.addResult(FHIRUtils.getReferenceToResource(documentReference));

					diagnosticReportSection.getEntry().add(FHIRUtils.getReferenceToResource(report));
				} else {
					if (!CollectionUtils.isEmpty(diagnostic.getBioChemistry().getLipidProfile().getLipidTests())) {
						for (Test lipidTest : diagnostic.getBioChemistry().getLipidProfile().getLipidTests()) {
							String testName = lipidTest.getTestName().toLowerCase();
							createLipidProfileObservation(bundle, composition, diagnostic, patient,
									diagnosticReportSection, lipidTest, testName);
						}
					}
				}
			}

			if (Objects.nonNull(diagnostic.getBioChemistry().getRenalFunction())) {
				if (StringUtils.isNotBlank(diagnostic.getBioChemistry().getRenalFunction().getAttachment())) {
					// Create a new DiagnosticReport resource
					CodeableConcept code = FHIRUtils.getCodeableConcept(Constants.RENAL_TEST_LOINC_CODE,
							Constants.LOINC_SYSTEM, Constants.BIO_CHEMISTRY, Constants.BIO_CHEMISTRY);
					// Create a new DiagnosticReport resource
					DiagnosticReport report = createDiagnosticReportResource(bundle, patient, code);

					// Create a new DocumentReference resource
					DocumentReference documentReference = createDocumentReferenceResource(Constants.RENAL_TEST,
							diagnostic.getBioChemistry().getRenalFunction().getAttachment(), patient,
							Constants.RENAL_TEST);

					report.addResult(FHIRUtils.getReferenceToResource(documentReference));

					diagnosticReportSection.getEntry().add(FHIRUtils.getReferenceToResource(report));
				} else {
				}
			}
		}

		return diagnosticReportSection;
	}

	private void createLipidProfileObservation(Bundle bundle, Composition composition, Diagnostic diagnostic,
			Patient patient, Composition.SectionComponent diagnosticReportSection, Test lipidTest, String testName) {
		if (testName.startsWith(Constants.TOTAL)) {
			CodeableConcept code = FHIRUtils.getCodeableConcept(Constants.CHOLESTEROL_TOTAL_LOINC_CODE,
					Constants.LOINC_SYSTEM,
					Constants.CHOLESTEROL_TOTAL_CHOLESTEROL_IN_HDL_MASS_RATIO_IN_SERUM_OR_PLASMA,
					Constants.CHOLESTEROL_TOTAL_CHOLESTEROL_IN_HDL_MASS_RATIO_IN_SERUM_OR_PLASMA);
			createDiagnosticReportAndObservation(bundle, composition, diagnostic, patient, diagnosticReportSection,
					code, lipidTest.getUnitOfMeasurement(), lipidTest.getResult(), code);
		}
		if (testName.startsWith(Constants.HDL)) {
			CodeableConcept code = FHIRUtils.getCodeableConcept(Constants.CHOLESTEROL_HDL_LOINC_CODE,
					Constants.LOINC_SYSTEM, Constants.CHOLESTEROL_IN_HDL_MASS_VOLUME_IN_SERUM_OR_PLASMA,
					Constants.CHOLESTEROL_IN_HDL_MASS_VOLUME_IN_SERUM_OR_PLASMA);
			createDiagnosticReportAndObservation(bundle, composition, diagnostic, patient, diagnosticReportSection,
					code, lipidTest.getUnitOfMeasurement(), lipidTest.getResult(), code);
		}
		if (testName.startsWith(Constants.LDL)) {
			CodeableConcept code = FHIRUtils.getCodeableConcept(Constants.CHOLESTEROL_LDL_LOINC_CODE,
					Constants.LOINC_SYSTEM, Constants.CHOLESTEROL_IN_LDL_MASS_VOLUME_IN_SERUM_OR_PLASMA_BY_CALCULATION,
					Constants.CHOLESTEROL_IN_LDL_MASS_VOLUME_IN_SERUM_OR_PLASMA_BY_CALCULATION);
			createDiagnosticReportAndObservation(bundle, composition, diagnostic, patient, diagnosticReportSection,
					code, lipidTest.getUnitOfMeasurement(), lipidTest.getResult(), code);
		}
		if (testName.startsWith(Constants.VLDL)) {
			CodeableConcept code = FHIRUtils.getCodeableConcept(Constants.CHOLESTEROL_VLDL_LOINC_CODE,
					Constants.LOINC_SYSTEM, Constants.CHOLESTEROL_IN_VLDL_MASS_VOLUME_IN_SERUM_OR_PLASMA_BY_CALCULATION,
					Constants.CHOLESTEROL_IN_VLDL_MASS_VOLUME_IN_SERUM_OR_PLASMA_BY_CALCULATION);
			createDiagnosticReportAndObservation(bundle, composition, diagnostic, patient, diagnosticReportSection,
					code, lipidTest.getUnitOfMeasurement(), lipidTest.getResult(), code);
		}
		if (testName.equals(Constants.TRIGLYCERIDES)) {
			CodeableConcept code = FHIRUtils.getCodeableConcept(Constants.TRIGLYCERIDE_LOINC_CODE,
					Constants.LOINC_SYSTEM, Constants.TRIGLYCERIDE_MASS_VOLUME_IN_SERUM_OR_PLASMA,
					Constants.TRIGLYCERIDE_MASS_VOLUME_IN_SERUM_OR_PLASMA);
			createDiagnosticReportAndObservation(bundle, composition, diagnostic, patient, diagnosticReportSection,
					code, lipidTest.getUnitOfMeasurement(), lipidTest.getResult(), code);
		}
		if (testName.equals(Constants.TRIGLYCERIDES_FASTING)) {
			CodeableConcept code = FHIRUtils.getCodeableConcept(Constants.TRIGLYCERIDE_FASTING_LOINC_CODE,
					Constants.LOINC_SYSTEM, Constants.TRIGLYCERIDE_MASS_VOLUME_IN_SERUM_OR_PLASMA_FASTING,
					Constants.TRIGLYCERIDE_MASS_VOLUME_IN_SERUM_OR_PLASMA_FASTING);
			createDiagnosticReportAndObservation(bundle, composition, diagnostic, patient, diagnosticReportSection,
					code, lipidTest.getUnitOfMeasurement(), lipidTest.getResult(), code);
		}
		if (testName.equals(Constants.FASTING_DURATION.toLowerCase())) {
			CodeableConcept code = FHIRUtils.getCodeableConcept(Constants.FASTING_DURATION_LOINC_CODE,
					Constants.LOINC_SYSTEM, Constants.FASTING_DURATION, Constants.FASTING_DURATION);
			createDiagnosticReportAndObservation(bundle, composition, diagnostic, patient, diagnosticReportSection,
					code, lipidTest.getUnitOfMeasurement(), lipidTest.getResult(), code);
		}
		if (testName.equals(Constants.FASTING_STATUS.toLowerCase())) {
			CodeableConcept code = FHIRUtils.getCodeableConcept(Constants.FASTING_STATUS_LOINC_CODE,
					Constants.LOINC_SYSTEM, Constants.FASTING_STATUS, Constants.FASTING_STATUS);
			createDiagnosticReportAndObservation(bundle, composition, diagnostic, patient, diagnosticReportSection,
					code, lipidTest.getUnitOfMeasurement(), lipidTest.getResult(), code);
		}
	}

	private void createDiagnosticReportAndObservation(Bundle bundle, Composition composition, Diagnostic diagnostic,
			Patient patient, Composition.SectionComponent section, CodeableConcept diagnosticReportCode, String unit,
			double value, CodeableConcept observationCode) {
		// Create a new DiagnosticReport resource
		DiagnosticReport report = createDiagnosticReportResource(bundle, patient, diagnosticReportCode);

		// Create an Observation
		Observation observation = createObservation(composition.getDate(), patient);
		observation.setCode(observationCode);
		observation.setValue(new Quantity().setValue(value).setUnit(unit));
		FHIRUtils.addToBundleEntry(bundle, observation, true);

		// Add Observation to the DiagnosticReport
		report.addResult(FHIRUtils.getReferenceToResource(observation));

		section.getEntry().add(FHIRUtils.getReferenceToResource(report));
	}

	private CodeableConcept getOPConsultationType() {
		return FHIRUtils.getCodeableConcept(Constants.OPCR_SNOMED_CODE, Constants.SNOMED_SYSTEM_SCT,
				Constants.CLINICAL_CONSULTATION_REPORT, Constants.CLINICAL_CONSULTATION_REPORT);
	}

	protected String getCompositionDocumentTitle() {
		return "OP Consultation Record";
	}

	private DocumentReference createDocumentReferenceResource(String reportType, String reportValue, Patient patient,
			String reportName) throws IOException {
		// create CodeableConcept type
		CodeableConcept type = FHIRUtils.getCodeableConcept(indicatorLoincCodeMap.get(reportType),
				Constants.LOINC_SYSTEM, reportType + " report", reportType + " report");

		// create documentReference resource
		DocumentReference documentReference = new DocumentReference();
		documentReference.setType(type);
		documentReference.setSubject(new Reference(patient));
		documentReference.setStatus(Enumerations.DocumentReferenceStatus.CURRENT);

		// Set the content (attachment) of the document
		DocumentReference.DocumentReferenceContentComponent content = new DocumentReference.DocumentReferenceContentComponent();
		Attachment attachment = FHIRUtils.getAttachment(reportName, reportValue);
		content.setAttachment(attachment);

		documentReference.addContent(content);
		return documentReference;
	}

	private Condition createConditionResource(CodeableConcept code) {
		Condition condition = new Condition();
		condition.setId(Utils.generateId());
		condition.setMeta(Utils.getMeta(new Date(), Constants.STRUCTURE_DEFINITION_CONDITION));
		condition.setClinicalStatus(getConditionClinicalStatus());
		condition.setCode(code);

		return condition;
	}

	private CodeableConcept getConditionClinicalStatus() {
		return FHIRUtils.getCodeableConcept(Constants.ACTIVE.toLowerCase(),
				Constants.FHIR_CONDITION_CLINICAL_STATUS_SYSTEM, Constants.ACTIVE.toLowerCase(), Constants.ACTIVE);
	}

	private Observation createObservation(Date compositionDate, Patient patient) {
		Observation observation = new Observation();
		observation.setId(UUID.randomUUID().toString());
		observation.setStatus(Observation.ObservationStatus.FINAL);
		observation.setSubject(new Reference(patient));
		observation.setEffective(getEffectiveObservationDate(compositionDate));
		return observation;
	}

	private DiagnosticReport createDiagnosticReportResource(Bundle bundle, Patient patient, CodeableConcept code) {
		DiagnosticReport report = new DiagnosticReport();
		report.setId(Utils.generateId());
		report.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);
		report.setCode(code);
		report.setSubject(FHIRUtils.getReferenceToPatient(patient));
		report.setIssued(new Date());
		FHIRUtils.addToBundleEntry(bundle, report, true);
		return report;
	}

	protected CodeableConcept getAllergyIntoleranceCode() {
		return FHIRUtils.getCodeableConcept(Constants.ALLERGY_INTOLERANCE_CODE, Constants.SNOMED_SYSTEM_SCT,
				Constants.ALLERGY_INTOLERANCE_SECTION, Constants.ALLERGY_INTOLERANCE_SECTION);
	}

	protected CodeableConcept getCoMorbiditiesCode(String name) {
		switch (name.toLowerCase()) {
		case "hypertension":
			return FHIRUtils.getCodeableConcept(Constants.HYPERTENSION_CODE, Constants.SNOMED_SYSTEM_SCT, name, null);
		case "coronary artery disease":
			return FHIRUtils.getCodeableConcept(Constants.CORONARY_ARTERY_DISEASE_CODE, Constants.SNOMED_SYSTEM_SCT,
					name, null);
		case "chronic obstructive pulmonary disease":
			return FHIRUtils.getCodeableConcept(Constants.CHRONIC_OBSTRUCTIVE_PULMONARY_DISEASE_CODE,
					Constants.SNOMED_SYSTEM_SCT, name, null);
		case "diabetes mellitus":
			return FHIRUtils.getCodeableConcept(Constants.DIABETES_MELLITUS_CODE, Constants.SNOMED_SYSTEM_SCT, name,
					null);
		default:
			return null;
		}
	}

	// fetch ObservationWomenHealth code
	protected CodeableConcept getObservationWomenHealthCode(String name) {
		switch (name.toLowerCase()) {
		case "pregnancy status":
			return FHIRUtils.getCodeableConcept(Constants.PREGNANCY_STATUS_CODE, Constants.LOINC_SYSTEM, name, name);
		case "menstrual cycle":
			return FHIRUtils.getCodeableConcept(Constants.MENSTRUAL_CYCLE_CODE, Constants.LOINC_SYSTEM, name, name);
		case "obstetric history":
			return FHIRUtils.getCodeableConcept(Constants.OBSTETRIC_HISTORY_CODE, Constants.LOINC_SYSTEM, name, name);
		case "breast health":
			return FHIRUtils.getCodeableConcept(Constants.BREAST_HEALTH_CODE, Constants.LOINC_SYSTEM, name, name);
		default:
			return null;
		}
	}

	protected CodeableConcept getOralCancerFNACCode() {
		return FHIRUtils.getCodeableConcept(Constants.ORAL_CANCER_FNAC_CODE, Constants.LOINC_SYSTEM, Constants.FNAC,
				Constants.FNAC);
	}

	protected Type getEffectiveObservationDate(Date compositionDate) {
		DateTimeType dateTimeType = new DateTimeType();
		dateTimeType.setValue(compositionDate);
		return dateTimeType;
	}

	private Composition.SectionComponent createAllergiesSection(AllergyIntoleranceRequest allergyDetail, Bundle bundle,
			Composition composition, Patient patient, IParser parser) throws IOException {

		// Create a new AllergyIntolerance resource
		AllergyIntolerance allergyIntolerance = new AllergyIntolerance();

		// Set resource type and ID
		allergyIntolerance.setId(UUID.randomUUID().toString());

		// Set profile
		allergyIntolerance.getMeta().addProfile(Constants.STRUCTURE_DEFINITION_ALLERGY_INTOLERANCE);

		// Set text
		Narrative narrative = new Narrative();
		narrative.setStatusAsString("generated");
		narrative.setDivAsString("<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>" + allergyDetail.getName()
				+ "</p><p>recordedDate:2015-08-06</p></div>");
		allergyIntolerance.setText(narrative);

		// Set clinicalStatus
		CodeableConcept clinicalStatus = new CodeableConcept();
		clinicalStatus = FHIRUtils.getCodeableConcept(Constants.FHIR_ALLERGY_INTOLERANCE_CLINICAL_STATUS_SYSTEM,
				Constants.SNOMED_SYSTEM_SCT, Constants.ACTIVE.toLowerCase(), Constants.ACTIVE);
		allergyIntolerance.setClinicalStatus(clinicalStatus);

		// Set verificationStatus
		CodeableConcept verificationStatus = new CodeableConcept();
		verificationStatus = FHIRUtils.getCodeableConcept(Constants.FHIR_ALLERGY_INTOLERANCE_VERIFICATION_STATUS_SYSTEM,
				Constants.SNOMED_SYSTEM_SCT, Constants.CONFIRMED.toLowerCase(), Constants.CONFIRMED);
		allergyIntolerance.setVerificationStatus(verificationStatus);

		// Set code
		CodeableConcept code = new CodeableConcept();
		code = FHIRUtils.getCodeableConcept(Constants.ALLERGY_INTOLERANCE_CODE, Constants.SNOMED_SYSTEM_SCT,
				allergyDetail.getName(), allergyDetail.getName() + allergyDetail.getType());

		// Set patient reference
		Reference patientRef = new Reference();
		patientRef.setReference(patient.getName().toString());
		allergyIntolerance.setPatient(patientRef);

		// Set recorded date
		DateTimeType currentTime = new DateTimeType(new Date());
		allergyIntolerance.setRecordedDateElement(new DateTimeType(currentTime.getValueAsString()));

		// Set recorder reference
		Reference recorderRef = new Reference();
		recorderRef.setReference(UUID.randomUUID().toString());
		allergyIntolerance.setRecorder(recorderRef);

		// Set note
		Annotation note = new Annotation();
		note.setText("The patient reports of: " + allergyDetail.getName() + " allergy which is of type: "
				+ allergyDetail.getType());
		allergyIntolerance.addNote(note);

		Composition.SectionComponent section = new Composition.SectionComponent();
		section.setTitle(Constants.ALLERGY_INTOLERANCE_SECTION);
		section.setCode(getAllergyIntoleranceCode());

		// Create a new Condition resource for the complaint
		Condition condition = createConditionResource(code);
		FHIRUtils.addToBundleEntry(bundle, allergyIntolerance, true);

		// Add the condition to the Chief complaint section
		section.addEntry(new Reference(condition));

		return section;
	}
}
