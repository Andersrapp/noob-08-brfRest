package se.andersrapp.brf.controllers;

import se.andersrapp.brf.dtos.ResidencyDTO;
import se.andersrapp.brf.entities.Residency;
import se.andersrapp.brf.exception.DataNotFoundException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.ejb.EJB;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import se.andersrapp.brf.services.ApartmentFacadeLocal;
import se.andersrapp.brf.services.ResidencyFacadeLocal;
import se.andersrapp.brf.services.ResidentFacadeLocal;
import se.andersrapp.brf.utilities.JNDIUtility;
import se.andersrapp.brf.utilities.Utility;

/**
 *
 * @author Anders Rapp
 */
public class ResidencyController {

    private final String RESIDENT_URI = "http://localhost:8080/brfRest/api/residents";
    private final String APARTMENT_URI = "http://localhost:8080/brfREST/api/apartments";

    @EJB
    ResidencyFacadeLocal residencyFacade;

    @EJB
    ApartmentFacadeLocal apartmentFacade;

    @EJB
    ResidentFacadeLocal residentFacade;

    public ResidencyController() {
        residencyFacade = JNDIUtility.checkJNDI(residencyFacade);
        apartmentFacade = JNDIUtility.checkJNDI(apartmentFacade);
        residentFacade = JNDIUtility.checkJNDI(residentFacade);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllResidencies(
            @PathParam("parentResourceId") int parentResourceId,
            @Context Request request,
            @Context UriInfo info
    ) {
        List<Residency> residencies = new ArrayList<>();
        String uri = info.getAbsolutePath().toString();
        if (RESIDENT_URI.equalsIgnoreCase(uri.substring(0, 43))) {
            residencies = residencyFacade.findResidenciesByResident(parentResourceId);
            if (residencies.isEmpty()) {
                throw new DataNotFoundException("Residencies for resident with id: " + parentResourceId + " is not found!");
            }
        } else if (APARTMENT_URI.equalsIgnoreCase(uri.substring(0, 44))) {
            residencies = residencyFacade.findResidenciesByApartment(parentResourceId);
            if (residencies.isEmpty()) {
                throw new DataNotFoundException("Residencies for apartment with id: " + parentResourceId + " is not found!");
            }
        }

        int hashValue = 0;
        List<ResidencyDTO> residencyDTOs = new ArrayList<>();
        for (Residency residency : residencies) {
            ResidencyDTO residencyDTO = Utility.convertResidencyToDTO(residency);
            hashValue += residencyDTO.hashCode();
            residencyDTOs.add(residencyDTO);
        }
        CacheControl cc = new CacheControl();
        cc.setMaxAge(86400);
        cc.setPrivate(true);

        EntityTag eTag = new EntityTag(Integer.toString(hashValue));
        ResponseBuilder builder = request.evaluatePreconditions(eTag);
        GenericEntity<List<ResidencyDTO>> residencyDTOWrapper = new GenericEntity<List<ResidencyDTO>>(residencyDTOs) {
        };

        if (builder == null) {
            builder = Response.ok();
            builder.entity(residencyDTOWrapper);
            builder.tag(eTag);
        }
        builder.cacheControl(cc);

        return builder.build();
    }

    @GET
    @Path("{residencyId: \\d+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOneResidency(
            @PathParam("parentResourceId") int parentResourceId,
            @PathParam("residencyId") int residencyId,
            @Context Request request,
            @Context UriInfo info
    ) {
        Residency residency = new Residency();
        String uri = info.getAbsolutePathBuilder().build().toString();
        if (RESIDENT_URI.equalsIgnoreCase(uri.substring(0, 43))) {
            residency = residencyFacade.findOneResidentResidency(residencyId, parentResourceId);
        } else if (APARTMENT_URI.equalsIgnoreCase(uri.substring(0, 44))) {
            residency = residencyFacade.findOneApartmentResidency(parentResourceId, residencyId);
        }
        ResidencyDTO residencyDTO = Utility.convertResidencyToDTO(residency);
        residencyDTO.setLink(Utility.getLinkToSelf(residencyId, info));

        CacheControl cc = new CacheControl();
        cc.setMaxAge(86400);
        cc.setPrivate(true);

        EntityTag eTag = new EntityTag(Integer.toString(residencyDTO.hashCode()));

        ResponseBuilder builder = request.evaluatePreconditions(eTag);
        if (builder == null) {
            builder = Response.ok();
            builder.entity(residency);
            builder.tag(eTag);
        }
        builder.cacheControl(cc);

        return builder.build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createResidency(
            @PathParam("parentResourceId") int parentResourceId,
            @FormParam("subResourceId") int subResourceId,
            @FormParam("fromDate") String fromDate,
            @FormParam("toDate") String toDate,
            @Context UriInfo info,
            @Context Request request
    ) {
        Residency residency = new Residency();
        String uri = info.getAbsolutePath().toString();
        if (RESIDENT_URI.equalsIgnoreCase(uri.substring(0, 43))) {
            residency.setResident(residentFacade.find(parentResourceId));
            residency.setApartment(apartmentFacade.find(subResourceId));
        } else if (APARTMENT_URI.equalsIgnoreCase(uri.substring(0, 44))) {
            residency.setApartment(apartmentFacade.find(parentResourceId));
            residency.setResident(residentFacade.find(subResourceId));
        } else {
            return Response.notModified().build();
        }
        Date now = new Date();
        Date from;
        Date to;
        if (!fromDate.isEmpty()) {
            from = Utility.parseStringToDate(fromDate);
            if (!from.after(now)) {
                residency.setFromDate(from);
            } else {
                throw new IllegalArgumentException("From date has to have form yyyy-MM-dd and not be after the present");
            }
            if (toDate.isEmpty()) {
                residency.setToDate(null);
                to = Utility.parseStringToDate(toDate);
                if (to.after(from) && to.before(now)) {
                    residency.setToDate(to);
                } else {
                    throw new IllegalArgumentException("To date has to have form yyyy-MM-dd and not be after the present");
                }
            }
        }
        residency = residencyFacade.create(residency);
        ResidencyDTO residencyDTO = Utility.convertResidencyToDTO(residency);
        residencyDTO.setLink(Utility.getLinkToSelf(subResourceId, info));
        CacheControl cc = new CacheControl();
        cc.setMaxAge(86400);
        cc.setPrivate(true);

        EntityTag eTag = new EntityTag(Integer.toString(residencyDTO.hashCode()));
        ResponseBuilder builder = request.evaluatePreconditions(eTag);
        if (builder == null) {
            builder = Response.ok();
            builder.entity(residencyDTO);
            builder.tag(eTag);
        }
        builder.cacheControl(cc);
        return builder.build();
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{residencyId: \\d+}")
    public Response updateResidency(
            @PathParam("parentResourceId") int parentResourceId,
            @PathParam("residencyId") int residencyId,
            @FormParam("subResourceId") int subResourceId,
            @FormParam("fromDate") String fromDate,
            @FormParam("toDate") String toDate,
            @Context UriInfo info,
            @Context Request request
    ) {
        Residency residency = new Residency();
        String uri = info.getAbsolutePathBuilder().build().toString();
        if (RESIDENT_URI.equalsIgnoreCase(uri.substring(0, 43))) {
            residency = residencyFacade.findOneResidentResidency(residencyId, parentResourceId);
            residency.setResident(residentFacade.find(parentResourceId));
            residency.setApartment(apartmentFacade.find(subResourceId));
        } else if (APARTMENT_URI.equalsIgnoreCase(uri.substring(0, 44))) {
            residency = residencyFacade.findOneApartmentResidency(parentResourceId, residencyId);
            residency.setApartment(apartmentFacade.find(parentResourceId));
            residency.setResident(residentFacade.find(subResourceId));
        } else {
            return Response.status(Status.NOT_FOUND).build();
        }
        Date now = new Date();
        Date from;
        Date to;
        if (!fromDate.isEmpty()) {
            from = Utility.parseStringToDate(fromDate);
            if (!from.after(now)) {
                residency.setFromDate(from);
            } else {
                throw new IllegalArgumentException("From date has to have form yyyy-MM-dd and not be after the present");
            }
            if (!toDate.isEmpty()) {
                to = Utility.parseStringToDate(toDate);
                if (to.after(from) && to.before(now)) {
                    residency.setToDate(to);
                } else {
                    throw new IllegalArgumentException("To date has to have form yyyy-MM-dd and not be after the present");
                }
            }
        }
        residency = residencyFacade.edit(residency);
        ResidencyDTO residencyDTO = Utility.convertResidencyToDTO(residency);
        residencyDTO.setLink(Utility.getLinkToSelf(subResourceId, info));
        CacheControl cc = new CacheControl();
        cc.setMaxAge(86400);
        cc.setPrivate(true);

        EntityTag eTag = new EntityTag(Integer.toString(residencyDTO.hashCode()));
        ResponseBuilder builder = request.evaluatePreconditions(eTag);
        if (builder == null) {
            builder = Response.ok();
            builder.entity(residencyDTO);
            builder.tag(eTag);
        }
        builder.cacheControl(cc);
        return builder.build();
    }

    @DELETE
    @Path("{residencyId: \\d+}")
    public Response deleteResidency(
            @PathParam("parentResourceId") int parentResourceId,
            @PathParam("residencyId") int residencyId,
            @Context UriInfo info,
            @Context Request request
    ) {
        Residency residency = new Residency();
        String uri = info.getAbsolutePath().toString();
        if (RESIDENT_URI.equalsIgnoreCase(uri.substring(0, 43))) {
            residency = residencyFacade.findOneResidentResidency(residencyId, parentResourceId);
        } else if (APARTMENT_URI.equalsIgnoreCase(uri.substring(0, 44))) {
            residency = residencyFacade.findOneApartmentResidency(parentResourceId, residencyId);
        }
        if (residency.getResident() != null) {
            residency.setResident(null);
        }

        if (residency.getApartment() != null) {
            residency.setApartment(null);
        }

        residencyFacade.remove(residency);

        return Response.ok().build();
    }
}
