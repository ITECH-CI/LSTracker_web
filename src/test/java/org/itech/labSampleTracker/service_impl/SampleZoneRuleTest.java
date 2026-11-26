package org.itech.labSampleTracker.service_impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.itech.labSampleTracker.dao.SampleRepository;
import org.itech.labSampleTracker.entities.Sample;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Un échantillon sans collecte ni site demandeur n'est pas enregistré. */
class SampleZoneRuleTest {

	private final SampleRepository repo = mock(SampleRepository.class);
	private final SampleServiceImpl service = new SampleServiceImpl();

	SampleZoneRuleTest() {
		ReflectionTestUtils.setField(service, "sampleRepo", repo);
	}

	@Test
	void sansCollecteNiSiteDemandeurLEnregistrementEstRefuse() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(new Sample()));
		assertEquals(SampleServiceImpl.NO_ZONE_MESSAGE, ex.getMessage());
		verify(repo, never()).save(any());
	}

	@Test
	void uneCollecteOuUnSiteDemandeurSuffit() {
		Sample collected = new Sample();
		collected.setSampleRetrievingId(12);
		Sample requested = new Sample();
		requested.setRequesterSiteId(34L);
		when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
		assertSame(collected, service.create(collected));
		assertSame(requested, service.create(requested));
	}
}
