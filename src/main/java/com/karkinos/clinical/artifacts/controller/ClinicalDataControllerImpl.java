package com.karkinos.clinical.artifacts.controller;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.karkinos.clinical.artifacts.service.ClinicalDataService;
import com.karkinos.clinical.artifacts.vo.ClinicalData;

/**
 * This class provides the business logic related to the OPConsultation.
 * 
 * @author kumari.anamika
 *
 */

@RestController
@Validated
public class ClinicalDataControllerImpl implements ClinicalDataController {

	@Autowired
	private ClinicalDataService clinicalDataService;

	/**
	 * {@inheritDoc}
	 */
	@PostMapping(value = "/ncg/cancer-modules/abdm/clinical-artifacts")
	@Override
	public ResponseEntity<String> generateClinicalDataJSON(@Valid @RequestBody ClinicalData clinicalData)
			throws Exception {

		return new ResponseEntity<>(clinicalDataService.clinicalDataGenerator(clinicalData), HttpStatus.OK);
	}
}
