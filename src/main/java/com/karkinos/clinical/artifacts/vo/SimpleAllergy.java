package com.karkinos.clinical.artifacts.vo;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;

import ca.uhn.fhir.parser.IParser;

public class SimpleAllergy {
	private static String cashewNutAllergy = "{\n" + "  \"resourceType\": \"AllergyIntolerance\",\n"
			+ "  \"id\": \"example\",\n" + "  \"patient\": {\n" + "    \"reference\": \"Patient/example\"\n" + "  }, \n"
			+ "\"clinicalStatus\": {\n" + "    \"coding\": [\n" + "      {\n"
			+ "        \"system\": \"http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical\",\n"
			+ "        \"code\": \"active\",\n" + "        \"display\": \"Active\"\n" + "      }\n" + "    ]\n" + "  },"
			+ "  \"verificationStatus\": {\n" + "    \"coding\": [\n" + "      {\n"
			+ "        \"system\": \"http://terminology.hl7.org/CodeSystem/allergyintolerance-verification\",\n"
			+ "        \"code\": \"confirmed\",\n" + "        \"display\": \"Confirmed\"\n" + "      }\n" + "    ]\n"
			+ "  },\n" + "  \"type\": \"allergy\",\n" + "  \"category\": [\n" + "    \"food\"\n" + "  ],\n"
			+ "  \"criticality\": \"high\",\n" + "  \"code\": {\n" + "    \"coding\": [\n" + "      {\n"
			+ "        \"system\": \"http://snomed.info/sct\",\n" + "        \"code\": \"227493005\",\n"
			+ "        \"display\": \"Cashew nuts\"\n" + "      }\n" + "    ]\n" + "  },\n" + "  \"note\": [\n"
			+ "    {\n"
			+ "      \"text\": \"The criticality is high becasue of the observed anaphylactic reaction when challenged with cashew extract.\"\n"
			+ "    }\n" + "  ]\n" + "}";

	private static String penicillinAllergy = "{\n" + "  \"resourceType\": \"AllergyIntolerance\",\n"
			+ "  \"id\": \"medication\",\n" + "  \"clinicalStatus\": {\n" + "    \"coding\": [\n" + "      {\n"
			+ "        \"system\": \"http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical\",\n"
			+ "        \"code\": \"active\",\n" + "        \"display\": \"Active\"\n" + "      }\n" + "    ]\n"
			+ "  },\n" + "  \"category\": [\n" + "    \"medication\"\n" + "  ],\n" + "  \"criticality\": \"high\",\n"
			+ "  \"code\": {\n" + "    \"coding\": [\n" + "      {\n"
			+ "        \"system\": \"http://www.nlm.nih.gov/research/umls/rxnorm\",\n" + "        \"code\": \"7980\",\n"
			+ "        \"display\": \"Penicillin G\"\n" + "      }\n" + "    ]\n" + "  },\n" + "  \"patient\": {\n"
			+ "    \"reference\": \"Patient/example\"\n" + "  }\n" + "}";

	private static String getFoodAllergyResString() {
		return cashewNutAllergy;
	}

	private static String getMedicationResString() {
		return penicillinAllergy;
	}

	public static AllergyIntolerance getFoodAllergy(IParser parser, Reference patientRef, Reference practitionerRef) {
		AllergyIntolerance foodAllergy = parser.parseResource(AllergyIntolerance.class, getFoodAllergyResString());
		foodAllergy.setPatient(patientRef);
		StringType onsetString = new StringType();
		onsetString.setValue("Past 1 year");
		foodAllergy.setOnset(onsetString);
		foodAllergy.setAsserter(practitionerRef);
		return foodAllergy;
	}

	public static AllergyIntolerance getMedicationAllergy(IParser parser, Reference patientRef,
			Reference practitionerRef) {
		AllergyIntolerance medAllergy = parser.parseResource(AllergyIntolerance.class, getMedicationResString());
		medAllergy.setPatient(patientRef);
		StringType onsetString = new StringType();
		onsetString.setValue("Past 2 year");
		medAllergy.setOnset(onsetString);
		medAllergy.setAsserter(practitionerRef);
		return medAllergy;
	}
}
