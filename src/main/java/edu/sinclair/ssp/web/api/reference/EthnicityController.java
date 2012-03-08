package edu.sinclair.ssp.web.api.reference;

import java.util.List;
import java.util.UUID;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import edu.sinclair.ssp.factory.reference.EthnicityTOFactory;
import edu.sinclair.ssp.model.ObjectStatus;
import edu.sinclair.ssp.model.reference.Ethnicity;
import edu.sinclair.ssp.service.reference.EthnicityService;
import edu.sinclair.ssp.transferobject.ServiceResponse;
import edu.sinclair.ssp.transferobject.reference.EthnicityTO;
import edu.sinclair.ssp.web.api.RestController;
import edu.sinclair.ssp.web.api.validation.ValidationException;

@PreAuthorize("hasRole('ROLE_USER')")
@Controller
@RequestMapping("/reference/ethnicity")
public class EthnicityController extends RestController<EthnicityTO>{

	private static final Logger logger = LoggerFactory.getLogger(EthnicityController.class);

	@Autowired
	private EthnicityService service;
	
	@Autowired
	private EthnicityTOFactory toFactory;
	
	@Override
	@RequestMapping(value = "/", method = RequestMethod.GET)
	public @ResponseBody List<EthnicityTO> getAll(@RequestParam(required = false) ObjectStatus status) throws Exception {
		if(status==null){
			status = ObjectStatus.ACTIVE;
		}
		return toFactory.toTOList(service.getAll(status));
	}
	
	@Override
	@RequestMapping(value = "/{id}", method = RequestMethod.GET)
	public @ResponseBody EthnicityTO get(@PathVariable UUID id) throws Exception {
		Ethnicity ethnicity = service.get(id);
		if(ethnicity!=null){
			return toFactory.toTO(ethnicity);
		}else{
			return null;
		}
	}
	
	@Override
	@RequestMapping(value = "/", method = RequestMethod.POST)
	public @ResponseBody EthnicityTO create(@Valid @RequestBody EthnicityTO obj) throws Exception {
		if(obj.getId()!=null){
			throw new ValidationException("You submitted a ethnicity with an id to the create method.  Did you mean to save?");
		}
		
		Ethnicity model = toFactory.toModel(obj);
		
		if(null!=model){
			Ethnicity createdModel = service.create(model);
			if(null!=createdModel){
				return toFactory.toTO(createdModel);
			}
		}
		return null;
	}

	@Override
	@RequestMapping(value = "/{id}", method = RequestMethod.PUT)
	public @ResponseBody EthnicityTO save(@PathVariable UUID id, @Valid @RequestBody EthnicityTO obj) throws Exception {
		if(id==null){
			throw new ValidationException("You submitted a ethnicity without an id to the save method.  Did you mean to create?");
		}
		
		Ethnicity model = toFactory.toModel(obj);
		model.setId(id);
		
		Ethnicity savedEthnicity = service.save(model);
		if(null!=savedEthnicity){
			return toFactory.toTO(savedEthnicity);
		}
		return null;
	}

	@Override
	@RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
	public @ResponseBody ServiceResponse delete(@PathVariable UUID id) throws Exception {
		service.delete(id);
		return new ServiceResponse(true);
	}
	
	@Override
	@ExceptionHandler(Exception.class)
	public @ResponseBody ServiceResponse handle(Exception e){
		logger.error("Error: ", e);
		return new ServiceResponse(false, e.getMessage());
	}
}
