/*
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 * @author Pascal
*/
package org.itech.labSampleTracker.service_impl;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.itech.labSampleTracker.dao.SampleRetrievingRepository;
import org.itech.labSampleTracker.entities.SampleRetrieving;
import org.itech.labSampleTracker.service.SampleRetrievingService;

import java.util.Collections;
import java.util.List;

/**
* <h2>SampleRetrievingServiceimpl</h2>
*/
@Service
@Transactional
public class SampleRetrievingServiceImpl implements SampleRetrievingService {

@Autowired
private SampleRetrievingRepository sampleretrievingRepo;

 /**
  * Enregistre une collecte. Une erreur (site absent, convoyeur absent…)
  * remonte à l'appelant : sans collecte, l'échantillon qui en dépend ne doit
  * pas être enregistré.
  */
 @Override
    public SampleRetrieving create(SampleRetrieving d) {
        return sampleretrievingRepo.save(d);
    }

    
    @Override
    public SampleRetrieving update(SampleRetrieving d) {
        SampleRetrieving c;

        try {
            c = sampleretrievingRepo.saveAndFlush(d);

        } catch (Exception ex) {
            return null;
        }
        return c;
    }


    @Override
    public SampleRetrieving getOne(int id) {
        SampleRetrieving t;

        try {
            t = sampleretrievingRepo.findById(id).orElse(null);

        } catch (Exception ex) {
            return null;
        }
        return t;
    }


    @Override
    public List<SampleRetrieving> getAll() {
        List<SampleRetrieving> lst;

        try {
            lst = sampleretrievingRepo.findAll();

        } catch (Exception ex) {
            return Collections.emptyList();
        }
        return lst;
    }


    @Override
    public long getTotal() {
        long total;

        try {
            total = sampleretrievingRepo.count();
        } catch (Exception ex) {
            return 0;
        }
        return total;
    }


    @Override
    public boolean delete(int id) {
        try {
            sampleretrievingRepo.deleteById(id);
            return true;

        } catch (Exception ex) {
            return false;
        }
    }

   

}
