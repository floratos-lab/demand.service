package org.geworkbench.service.demand.ws;

import java.io.IOException;

import javax.activation.DataHandler;
import javax.xml.bind.JAXBElement;

import org.geworkbench.service.demand.schema.ObjectFactory;
import org.geworkbench.service.demand.schema.DemandInput;
import org.geworkbench.service.demand.schema.DemandOutput;
import org.geworkbench.service.demand.service.DemandInputRepository;
import org.springframework.util.Assert;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
public class DemandInputRepositoryEndpoint {

    private DemandInputRepository demandInputRepository;

    private ObjectFactory objectFactory;

    public DemandInputRepositoryEndpoint(DemandInputRepository demandInputRepository) {
        Assert.notNull(demandInputRepository, "'demandInputRepository' must not be null");
        this.demandInputRepository = demandInputRepository;
        this.objectFactory = new ObjectFactory();
    }

    @PayloadRoot(localPart = "ExecuteDemandRequest", namespace = "http://www.geworkbench.org/service/demand")
    @ResponsePayload
    public JAXBElement<DemandOutput> executeDemand(@RequestPayload JAXBElement<DemandInput> requestElement) throws IOException {
    	DemandInput request = requestElement.getValue();

        String dataDir = demandInputRepository.storeDemandInput(request);
        
        DataHandler[] handlers = null;
        StringBuilder log = new StringBuilder();
        if (dataDir == null)
        	log.append("Cannot find data dir to store demand input");
        else
        	handlers = demandInputRepository.execute(request, dataDir, log);

        DemandOutput response = new DemandOutput();
        response.setLog(log.toString());
        response.setResfile(handlers[0]);
        response.setEdgefile(handlers[1]);
        response.setModfile(handlers[2]);
        return objectFactory.createExecuteDemandResponse(response);
    }
}
