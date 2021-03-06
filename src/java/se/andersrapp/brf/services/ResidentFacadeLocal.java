package se.andersrapp.brf.services;

import se.andersrapp.brf.entities.Resident;
import java.util.List;
import javax.ejb.Local;

/**
 *
 * @author Anders Rapp
 */
@Local
public interface ResidentFacadeLocal {

    Resident create(Resident resident);

    Resident edit(Resident resident);

    void remove(Resident resident);

    Resident find(Object id);

    List<Resident> findAll();

    List<Resident> findRange(int[] range);

    int count();
    
}
