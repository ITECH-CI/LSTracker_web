package org.itech.labSampleTracker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.itech.labSampleTracker.entities.Sample;
import org.itech.labSampleTracker.entities.SampleRetrieving;
import org.junit.jupiter.api.Test;

/** Site affiché sur la fiche d'un échantillon, y compris sans collecte ni site demandeur. */
class SampleEditSiteTest {

	@Test
	void siteDeLaCollecteEnPriorite() {
		Sample s = new Sample();
		SampleRetrieving r = new SampleRetrieving();
		r.setSiteId(7);
		s.setSampleRetrieving(r);
		s.setRequesterSiteId(9L);
		assertEquals(7, SampleController.displayedSiteId(s));
	}

	@Test
	void siteDemandeurSansCollecte() {
		Sample s = new Sample();
		s.setRequesterSiteId(9L);
		assertEquals(9, SampleController.displayedSiteId(s));
	}

	@Test
	void aucunSiteSansCollecteNiSiteDemandeur() {
		assertNull(SampleController.displayedSiteId(new Sample()));
	}
}
