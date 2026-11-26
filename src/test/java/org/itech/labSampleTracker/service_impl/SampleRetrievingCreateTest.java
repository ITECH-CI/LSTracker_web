package org.itech.labSampleTracker.service_impl;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.itech.labSampleTracker.dao.SampleRetrievingRepository;
import org.itech.labSampleTracker.entities.SampleRetrieving;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/** L'échec d'enregistrement d'une collecte remonte, au lieu de renvoyer null. */
class SampleRetrievingCreateTest {

	private final SampleRetrievingRepository repo = mock(SampleRetrievingRepository.class);
	private final SampleRetrievingServiceImpl service = new SampleRetrievingServiceImpl();

	SampleRetrievingCreateTest() {
		ReflectionTestUtils.setField(service, "sampleretrievingRepo", repo);
	}

	@Test
	void lErreurDEnregistrementRemonte() {
		when(repo.save(any())).thenThrow(new DataIntegrityViolationException("site_id null"));
		assertThrows(DataIntegrityViolationException.class, () -> service.create(new SampleRetrieving()));
	}

	@Test
	void laCollecteEnregistreeEstRenvoyee() {
		SampleRetrieving saved = new SampleRetrieving();
		when(repo.save(any())).thenReturn(saved);
		assertSame(saved, service.create(new SampleRetrieving()));
	}
}
