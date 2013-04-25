package org.geworkbench.service.demand.service;

import java.io.IOException;

import javax.activation.DataHandler;

import org.geworkbench.service.demand.schema.DemandInput;

public interface DemandInputRepository {

	String storeDemandInput(DemandInput input) throws IOException;

    DataHandler[] execute(DemandInput input, String dataDir, StringBuilder log) throws IOException;
}
