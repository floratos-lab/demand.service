package org.geworkbench.service.demand.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Random;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geworkbench.service.demand.schema.DemandInput;

public class StubDemandInputRepository implements DemandInputRepository{
	
    private static final Log logger = LogFactory.getLog(StubDemandInputRepository.class);
    
    private static final String DEMANDROOT = "/ifs/data/c2b2/af_lab/cagrid/r/demand/runs/";
	private static final String scriptDir = "/ifs/data/c2b2/af_lab/cagrid/r/demand/scripts/";
	private static final String rscript   = "/nfs/apps/R/3.0.1/bin/Rscript";
	private static final String account   = "cagrid";
	private static final String submitSh  = "demand_submit.sh";
	private static final long POLL_INTERVAL = 20000;    //20 seconds
	private static final Random random = new Random();
	private static final String demandR=	"DMAND.R";			//R script
 	private static final String logExt	=	".log";				//log file
 	private static final String expExt	=	".exp";				//input exp dataset file
 	private static final String spExt	=	"_sample.info.txt";	//input sample info
 	private static final String annoExt	=	"_annot.csv";		//input annotation
 	private static final String nwExt	=	"_network.txt";		//input network in lab format
 	private static final String resFile	=	"DMAND_result.txt";	//result file
 	private static final String resEdge	=	"KL_edge.txt";		//result edge file
 	private static final String resMod	=	"Module.txt";		//result module file
 	private static final String parName	=	"parameter.txt";	//configuration file
 	private static final String resDir	="result_Geldanamycin/";//result directory

	@Override
	public String storeDemandInput(DemandInput input) throws IOException {
    	String dataDir = getDataDir();
    	if (dataDir==null) {
    		logger.error("Cannot find data dir to store demand input");
    		return null;
    	}
    	
    	String setName   = input.getName();
    	String expFname  = dataDir + setName + expExt;
    	String nwFname   = dataDir + setName + nwExt;
    	String annoFname = dataDir + setName + annoExt;
    	String spFname   = dataDir + setName + spExt;
    	
    	writeFile(input.getExpfile(),   expFname);    	
    	writeFile(input.getNwfile(),     nwFname);
    	writeFile(input.getAnnofile(), annoFname);
    	writeFile(input.getSpfile(),     spFname);
    	
    	String resultDir = dataDir + resDir;

		String paramFname = dataDir + parName;
		File paramFile = new File(paramFname);
		writeParamFile(paramFile, expFname, nwFname, annoFname, spFname, resultDir);
    	
		logger.info("Storing demand input " + paramFname + " [" +paramFile.exists() + "]");
        return dataDir;
	}

	private void writeFile(DataHandler handler, String fname) throws IOException{
    	File file = new File(fname);
    	OutputStream os = new FileOutputStream(file);
    	handler.writeTo(os);
    	os.close();
	}
	
	private void writeParamFile(File paramFile, String expFname, String nwFname, String annoFname, String spFname, String rsltDir){
		BufferedWriter bw = null;
		try{
			bw = new BufferedWriter(new FileWriter(paramFile));
			bw.write("parameter\tvalue");
			bw.newLine();
			bw.write("expfile\t"+expFname);
			bw.newLine();
			bw.write("network_file\t"+nwFname);
			bw.newLine();
			bw.write("ANNOTFILE\t"+annoFname);
			bw.newLine();
			bw.write("phenotypefile\t"+spFname);
			bw.newLine();
			bw.write("DIR\t"+rsltDir);
			bw.newLine();
			bw.flush();
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			try{
				if (bw!=null) bw.close();
				paramFile.deleteOnExit();
			}catch(IOException e){
				e.printStackTrace();
			}
		}
	}

	
	@Override
	public DataHandler[] execute(DemandInput input, String dataDir, StringBuilder log) throws IOException {
    	String setName    = input.getName();
    	String runid      = new File(dataDir).getName();
    	String paramFname = dataDir + parName;
    	String logfname   = dataDir + setName + logExt;
    	String submitStr = submitBase + logfname + " -N " + runid + "\n" +
    			rscript + " " + scriptDir + demandR + " " + paramFname + "\n";
    	
    	String submitFile = dataDir + submitSh;
    	if (!writeToFile(submitFile, submitStr)){
    		String msg = "Cannot find write demand job submit script";
    		logger.error(msg);
    		log.append(msg);
    		return null;
    	}
    	int ret = submitJob(submitFile);
    	if (ret!=0){
    		String msg = "Demand job "+runid+" submission error\n";
    		logger.error(msg);
    		log.append(msg);
    		return null;
    	}

		while(!isJobDone(runid)){
		    try{
		    	Thread.sleep(POLL_INTERVAL);
		    }catch(InterruptedException e){
		    }
		}
    	String resultDir = dataDir + resDir;
    	
    	String resFname 	=	resultDir + resFile;
		File resultFile 	=	new File(resFname);
		String resEdgeFname	=	resultDir + resEdge;
		File resultEdgeFile	=	new File(resEdgeFname);
		String resModFname	=	resultDir + resMod;
		File resultModFile	=	new File(resModFname);
    	
		if (!resultFile.exists()){
		    String err = null;
		    if ((err = runError(logfname)) != null){
		    	String msg = "Demand job "+runid+" abnormal termination\n"+err;
		    	logger.error(msg);
		    	log.append(msg);
		    }else{
		    	String msg = "Demand job "+runid+" was killed";
		    	logger.error(msg);
		    	log.append(msg);
		    }
		    return null;
		}

        logger.info("Sending demand output " + resFname);

		DataHandler[] handlers = {
				new DataHandler(new FileDataSource(resultFile)),
				new DataHandler(new FileDataSource(resultEdgeFile)),
				new DataHandler(new FileDataSource(resultModFile)) };
        return handlers;
	}

    private static final String maxmem = "4G";
	private static final String timeout = "48::";
    private String submitBase = "#!/bin/bash\n#$ -l mem="+maxmem+",time="+timeout+" -cwd -j y -o ";
    
    private String getDataDir(){
		File root = new File(DEMANDROOT);
		if (!root.exists() && !root.mkdir()) return null;

		int i = 0;
		String dirname = null;
		File randdir = null;
		try{
		    do{
		    	dirname = DEMANDROOT + "dmd" + random.nextInt(Short.MAX_VALUE)+ "/";
		    	randdir = new File(dirname);
		    }while(randdir.exists() && ++i < Short.MAX_VALUE);
		}catch(Exception e){
		    e.printStackTrace();
		    return null;
		}
		if (i < Short.MAX_VALUE){
			if (!randdir.mkdir()) return null;
			return dirname;
		}
		else return null;
	}
    
    private boolean writeToFile(String fname, String string){
	    BufferedWriter bw = null;
	    try{
			bw = new BufferedWriter(new FileWriter(fname));
			bw.write(string);
			bw.flush();
	    }catch(IOException e){
	    	e.printStackTrace();
	    	return false;
	    }finally{
			try{
			    if (bw!=null) bw.close();
			}catch(IOException e){
			    e.printStackTrace();
			}
	    }
	    return true;
	}
    
	private int submitJob(java.lang.String jobfile){
		String command = "qsub " + jobfile;
		logger.info(command);
		try {
			Process p = Runtime.getRuntime().exec(command);
			StreamGobbler out = new StreamGobbler(p.getInputStream(), "INPUT");
			StreamGobbler err = new StreamGobbler(p.getErrorStream(), "ERROR");
			out.start();
			err.start();
			return p.waitFor();
		} catch (Exception e) {
			return -1;
		}
	}
	
	private boolean isJobDone(String runid) {
		String cmd = "qstat -u "+account;
		BufferedReader brIn = null;
		BufferedReader brErr = null;
		try{
			Process p = Runtime.getRuntime().exec(cmd);
			brIn = new BufferedReader(new InputStreamReader(p.getInputStream()));
			brErr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			String line = null;
			while ((line = brIn.readLine())!=null || (line = brErr.readLine())!=null){
				if(line.startsWith("error")) return false; //cluster scheduler error
				String[] toks = line.trim().split("\\s+");
				if (toks.length > 3 && toks[2].equals(runid))
					return false;
			}
		}catch(Exception e){
			e.printStackTrace();
			return true;
		}finally {
			try{
				if (brIn!=null)  brIn.close();
				if (brErr!=null) brErr.close();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		return true;
	}

	private String runError(String logfname){
		StringBuilder str = new StringBuilder();
		BufferedReader br = null;
		boolean error = false;
		File logFile = new File(logfname);
		if (!logFile.exists()) return null;
		try{
			br = new BufferedReader(new FileReader(logFile));
			String line = null;
			int i = 0;
			while((line = br.readLine())!=null){
				if (((i = line.indexOf("Error"))>-1)){
					str.append(line.substring(i)+"\n");
					error = true;
				}
			}
		}catch(IOException e){
			e.printStackTrace();
		}finally{
			try{
				if (br!=null) br.close();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		if (error)  return str.toString();
		return null;
	}

	public static class StreamGobbler extends Thread
	{
	    private InputStream is;
	    private String type;
	    private OutputStream os;
	    
	    StreamGobbler(InputStream is, String type)
	    {
	        this(is, type, null);
	    }
	    StreamGobbler(InputStream is, String type, OutputStream redirect)
	    {
	        this.is = is;
	        this.type = type;
	        this.os = redirect;
	    }
	    
	    public void run()
	    {
            PrintWriter pw = null;
            BufferedReader br = null;
	        try {
	            if (os != null)
	                pw = new PrintWriter(os, true);
	                
	            InputStreamReader isr = new InputStreamReader(is);
	            br = new BufferedReader(isr);
	            String line=null;
	            while ( (line = br.readLine()) != null)
	            {
	                if (pw != null){
	                    pw.println(line);
	                }
	                System.out.println(type + ">" + line);    
	            }
	        } catch (IOException ioe) {
	            ioe.printStackTrace();  
	        } finally {
	        	try{
		        	if (pw!=null) pw.close();
	        		if (br!=null) br.close();
	            }catch(Exception e){
	            	e.printStackTrace();
	            }
	        }
	    }
	}
}
