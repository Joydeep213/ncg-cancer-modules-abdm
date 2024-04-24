package com.karkinos.clinical.artifacts.util;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.ImagingStudy;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.karkinos.clinical.artifacts.vo.ClinicalData;
import com.karkinos.clinical.artifacts.vo.Diagnostic;
import com.karkinos.clinical.artifacts.vo.SimpleAllergy;

import ca.uhn.fhir.parser.IParser;
import lombok.SneakyThrows;

@Service
public class OPConsultationHelper {

	@SneakyThrows
	public Bundle createOPConsultationBundle(Date docDate, String hipPrefix, IParser jsonParser,
			ClinicalData clinicalData) {
		Bundle bundle = FHIRUtils.createBundle(docDate, null);

		Composition opDoc = new Composition();
		opDoc.setId(Utils.generateId());
		opDoc.setDate(bundle.getTimestamp());
		opDoc.setIdentifier(FHIRUtils.getIdentifier(opDoc.getId(), hipPrefix, "document"));
		opDoc.setStatus(Composition.CompositionStatus.FINAL);
		opDoc.setType(getDocumentType());
		opDoc.setTitle(getCompositionDocumentTitle());
		FHIRUtils.addToBundleEntry(bundle, opDoc, false);

		if (Objects.nonNull(clinicalData)) {
			// add patient entry
			Patient patientResource = FHIRUtils.addPatientResourceToComposition(clinicalData, bundle, opDoc);

			// add sections entry
			createCancerModuleSections(hipPrefix, jsonParser, bundle, opDoc, clinicalData, patientResource);
		}

		return bundle;
	}

	protected String getCompositionDocumentTitle() {
		return "OP Consultation Record";
	}

	protected void createCancerModuleSections(String hipPrefix, IParser jsonParser, Bundle bundle, Composition opDoc,
			ClinicalData clinicalData, Patient patientResource) {

		// allergies
		if (Objects.nonNull(clinicalData.getClinical())
				&& !CollectionUtils.isEmpty(clinicalData.getClinical().getAllergies())) {
			createAllergiesSection(bundle, opDoc, patientResource, jsonParser);
		}

		// diagnostic
		if (Objects.nonNull(clinicalData.getDiagnostic()) && Objects.nonNull(clinicalData.getDiagnostic().getCbc())
				&& Objects.nonNull(clinicalData.getDiagnostic().getCbc().getHemoglobin())) {
			createDiagnosticReportSection(bundle, opDoc, clinicalData.getDiagnostic(), patientResource, jsonParser,
					hipPrefix);
		}

		// oralCancer
		if (Objects.nonNull(clinicalData.getOralCancer())) {
			for (Map.Entry<String, String> oralCancerDetail : clinicalData.getOralCancer().entrySet()) {
				createOralCancerSection(bundle, opDoc, oralCancerDetail, patientResource, jsonParser);
			}
		}

		// lungCancer
		if (Objects.nonNull(clinicalData.getLungCancer())) {
			for (Map.Entry<String, String> lungCancerDetail : clinicalData.getLungCancer().entrySet()) {
				createLungCancerSection(bundle, opDoc, lungCancerDetail, patientResource, jsonParser);
			}
		}
	}

	protected CodeableConcept getDocumentType() {
		return FHIRUtils.getOPConsultationType();
	}

	private void createOralCancerSection(Bundle bundle, Composition composition,
			Map.Entry<String, String> oralCancerDetail, Patient patient, IParser parser) {
		Composition.SectionComponent section = new Composition.SectionComponent();
		section.setTitle("Oral Cancer");
		section.setCode(FHIRUtils.getOralCancerFNACSectionCode(oralCancerDetail.getKey()));

		Observation observation = createObservation(composition.getDate(), patient);
		observation.setCode(FHIRUtils.getOralCancerFNACSectionCode("Oral Cancer"));
		observation.setValue(new StringType("FNAC report"));
		FHIRUtils.addToBundleEntry(bundle, observation, true);

		// Attach the report file
		Attachment attachment = new Attachment();
		attachment.setContentType("application/pdf");
		byte[] bytesToEncode = oralCancerDetail.getValue().getBytes();
		attachment.setData(bytesToEncode);
//		observation.setValue(attachment);

		section.setEntry(Collections.singletonList(new Reference(observation)));
	}

	private Observation createObservation(Date compositionDate, Patient patient) {
		Observation observation = new Observation();
		observation.setStatus(Observation.ObservationStatus.FINAL);
		observation.setSubject(new Reference(patient));
		observation.setId(UUID.randomUUID().toString());
		observation.setEffective(getEffectiveObservationDate(compositionDate));
		return observation;
	}

	private Composition.SectionComponent createLungCancerSection(Bundle bundle, Composition composition,
			Map.Entry<String, String> lungCancerDetail, Patient patient, IParser parser) {
		Composition.SectionComponent section = new Composition.SectionComponent();
		section.setTitle("Lung Cancer");

		ImagingStudy imagingStudy = new ImagingStudy();
		imagingStudy.setStatus(ImagingStudy.ImagingStudyStatus.AVAILABLE);
		imagingStudy.setSubject(new Reference(patient));
		imagingStudy.setDescription(lungCancerDetail.getKey());

		// Create a new instance of ImagingStudySeriesInstanceComponent
		ImagingStudy.ImagingStudySeriesInstanceComponent instance = new ImagingStudy.ImagingStudySeriesInstanceComponent();
		ImagingStudy.ImagingStudySeriesComponent series = imagingStudy.addSeries();
		series.addInstance(instance);

		section.setEntry(Collections.singletonList(new Reference(imagingStudy)));
		return section;
	}

	@SneakyThrows
	protected void createDiagnosticReportSection(Bundle bundle, Composition composition, Diagnostic diagnostic,
			Patient patient, IParser jsonParser, String hipPrefix) {
		if (Utils.randomBool())
			return;

		Composition.SectionComponent section = composition.addSection();
		section.setTitle("Diagnostic Reports");
		section.setCode(FHIRUtils.getDiagnosticReportType());

		// Create a new DiagnosticReport
		DiagnosticReport report = new DiagnosticReport();
		report.setId(Utils.generateId());
		report.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);
		report.setCode(new CodeableConcept().setText("CBC"));
		report.setSubject(FHIRUtils.getReferenceToPatient(patient));
		FHIRUtils.addToBundleEntry(bundle, report, false);

		// Create an Observation for hemoglobin
		Observation hemoglobinObservation = createObservation(composition.getDate(), patient);
		hemoglobinObservation.setCode(new CodeableConcept().setText("Hemoglobin"));
		hemoglobinObservation.setValue(new Quantity().setValue(diagnostic.getCbc().getHemoglobin()).setUnit("g/dL"));
		FHIRUtils.addToBundleEntry(bundle, hemoglobinObservation, true);

		// Add the hemoglobin Observation to the DiagnosticReport
		report.addResult(FHIRUtils.getReferenceToResource(hemoglobinObservation));

		report.setIssued(composition.getDate());
		if (Utils.randomBool()) {
			report.setEffective(FHIRUtils.getDateTimeType(composition.getDate()));
		}

		section.getEntry().add(FHIRUtils.getReferenceToResource(report));
	}

	protected Type getEffectiveObservationDate(Date compositionDate) {
		DateTimeType dateTimeType = new DateTimeType();
		dateTimeType.setValue(compositionDate);
		return dateTimeType;
	}

	@SneakyThrows
	protected void createAllergiesSection(Bundle bundle, Composition composition, Patient patient, IParser parser) {
		Composition.SectionComponent section = composition.addSection();
		section.setTitle("Allergy Section");
		section.setCode(FHIRUtils.getAllergySectionType());
		AllergyIntolerance foodAllergy = SimpleAllergy.getFoodAllergy(parser, composition.getSubject(),
				composition.getAuthorFirstRep());
		AllergyIntolerance medicationAllergy = SimpleAllergy.getMedicationAllergy(parser, composition.getSubject(),
				composition.getAuthorFirstRep());
		FHIRUtils.addToBundleEntry(bundle, foodAllergy, true);
		FHIRUtils.addToBundleEntry(bundle, medicationAllergy, true);
		section.getEntry().add(FHIRUtils.getReferenceToResource(foodAllergy));
		section.getEntry().add(FHIRUtils.getReferenceToResource(medicationAllergy));
	}
}
