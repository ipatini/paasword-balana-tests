/*******************************************************************************
 * Copyright (c) 2016, SICS Swedish ICT AB
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions 
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, 
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR 
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT 
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY 
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT 
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE 
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *******************************************************************************/
package se.sics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.wso2.balana.PDP;
import org.wso2.balana.PDPConfig;
import org.wso2.balana.attr.AttributeValue;
import org.wso2.balana.attr.StringAttribute;
import org.wso2.balana.ctx.Attribute;
import org.wso2.balana.ctx.ResponseCtx;
import org.wso2.balana.ctx.xacml3.RequestCtx;
import org.wso2.balana.finder.AttributeFinder;
import org.wso2.balana.finder.AttributeFinderModule;
import org.wso2.balana.finder.PolicyFinder;
import org.wso2.balana.finder.impl.FileBasedPolicyFinderModule;
import org.wso2.balana.utils.Constants.PolicyConstants;
import org.wso2.balana.xacml3.Attributes;

/**
 * An example PDP.
 * 
 * @author Ludwig Seitz
 *
 */
public class ExamplePDP {

    private PDP pdp;
    
    /**
     * Create a PDP loading the policies as *.xml files from a directory.
     * 
     * @param policyDirectory  the policy directory
     * @throws SQLException 
     */
    public ExamplePDP(String policyDirectory) throws SQLException {
		Set<String> fileNames 
				= getFilesInFolder(policyDirectory, ".xml");
		PolicyFinder pf = new PolicyFinder();
		FileBasedPolicyFinderModule  pfm 
			= new FileBasedPolicyFinderModule(fileNames);
		pf.setModules(Collections.singleton(pfm));
		pfm.init(pf);
		/*
		AttributeFinder af = new AttributeFinder();
		//Create DB AFM with default user, pwd and dbUrl
		AttributeFinderModule afm = new DBAttributeFinderModule(null, null, null);
		af.setModules(Collections.singletonList(afm));
		this.pdp = new org.wso2.balana.PDP(new PDPConfig(af, pf, null));*/
        
		PDPConfig pdpConfig = org.wso2.balana.Balana.getInstance().getPdpConfig();

        // registering new attribute finder. so default PDPConfig is needed to change
        AttributeFinder attributeFinder = pdpConfig.getAttributeFinder();
        java.util.List<AttributeFinderModule> finderModules = attributeFinder.getModules();
		AttributeFinderModule afm = new DBAttributeFinderModule(null, null, null);
        finderModules.add(afm);
        attributeFinder.setModules(finderModules);

        this.pdp = new PDP(new PDPConfig(attributeFinder, pf, null, true));
    }
    
    /**
     * Get the files from a directory (optionally specifying the desired
     * extension).
     * 
     * @param directory  the directory (full pathname)
     * @param extension  the desired extension filter
     * @return  the List of file names
     */
    private static Set<String> getFilesInFolder(String directory, 
            final String extension) {
        File dir = new File(directory);
        String[] children = null;
        if (extension != null) {
            FilenameFilter filter = new FilenameFilter() {
                public boolean accept(File f, String name) {
                    return name.endsWith(extension);
                }
            };
            children = dir.list(filter);
        } else {
            children = dir.list();
        }
        HashSet<String> result = new HashSet<String>();
        for (int i=0; i<children.length;i++) {
            result.add(directory + System.getProperty("file.separator") + children[i]);
        }
        return result;
    }
    
    public ResponseCtx evaluate(RequestCtx request) {
        return this.pdp.evaluate(request);
    }
    
    public String evaluate(String request) {
        return this.pdp.evaluate(request);
    }
    
    /**
     * Initialize the database with test data.
     * You need to first call DBAttributeFinderModule.init() 
     * before calling this.
     * 
     * @param dbUser  the user who can INSERT on the database
     * @param dbPwd   the password of this user
     * @param dbUrl  the database URL
     * @throws SQLException 
     */
    public static void init(String dbUser, String dbPwd, String dbUrl) 
            throws SQLException {
        String createData = "INSERT INTO " + DBAttributeFinderModule.dbName
                + "." + DBAttributeFinderModule.dbRoleTable
                + " VALUES ('John','admin')";
        //FIXME: Change test data up here
        Properties connectionProps = new Properties();
        connectionProps.put("user", dbUser);
        connectionProps.put("password", dbPwd);
        Connection rootConn = DriverManager.getConnection(
                dbUrl, connectionProps);
        Statement stmt = rootConn.createStatement();
        stmt.execute(createData);
        stmt.close();
        rootConn.close();
               
    }
    
    /**
     * 
     * @param dbRoot
     * @param dbPwd
     * @param dbUrl
     * @param user
     * @throws SQLException 
     */
    public static void tearDown(String dbRoot, String dbPwd, String dbUrl, 
            String user) throws SQLException {
        Properties connectionProps = new Properties();
        connectionProps.put("user", dbRoot);
        connectionProps.put("password", dbPwd);
        Connection rootConn = DriverManager.getConnection(
                dbUrl, connectionProps);
              
        String dropDB = "DROP DATABASE IF EXISTS " 
                + DBAttributeFinderModule.dbName + ";";
        String dropUser = "DROP USER " + user + "@'localhost';";
        Statement stmt = rootConn.createStatement();
        stmt.execute(dropDB);
        stmt.execute(dropUser);        
        stmt.close();
        rootConn.close();
    }
    
    public static void main(String[] args) throws IOException, SQLException, org.wso2.balana.ParsingException {
		if (args.length>0 && (args[0].trim().equalsIgnoreCase("--init-db") || args[0].trim().equalsIgnoreCase("--clear-db"))) {
			String dbPwdFile  = "db.pwd";
			String dbUsername = "PaaSwordUser";
			String dbPassword = "PaaSword";
			String dbConnStr  = "jdbc:mysql://localhost:3306";
			if (args.length>1) dbPwdFile  = args[1].trim();
			if (args.length>2) dbUsername = args[2].trim();
			if (args.length>3) dbPassword = args[3].trim();
			if (args.length>4) dbConnStr  = args[4].trim();
			
			String dbPwd = null;
			BufferedReader br = new BufferedReader(new FileReader( dbPwdFile ));
			try {
				StringBuilder sb = new StringBuilder();
				String line = br.readLine();
				while (line != null) {
					sb.append(line);
					sb.append(System.lineSeparator());
					line = br.readLine();
				}
				dbPwd = sb.toString().replace(
						System.getProperty("line.separator"), "");     
			} finally {
				br.close();
			}
			
			if (args[0].trim().equalsIgnoreCase("--init-db")) {
				//Run this only once, then comment out...
				DBAttributeFinderModule.initDB(
						dbPwd, dbUsername,dbPassword, 
						dbConnStr);
				
				ExamplePDP.init(dbUsername,dbPassword, 
						dbConnStr);
				//... up until here
				
				System.out.println("Database initialized");
				return;
			}
			if (args[0].trim().equalsIgnoreCase("--clear-db")) {
				tearDown("root", dbPwd, dbConnStr, dbUsername);
				System.out.println("Database deleted");
				return;
			}
		}
        
		String policyDir = "src/test/resources";
		String requestFile = "request.xacml.xml";
		if (args.length>0) policyDir = args[0].trim();
		if (args.length>1) requestFile = args[1].trim();
		
        ExamplePDP pdp = new ExamplePDP(policyDir);
		
		if (args.length<2) {
			AttributeValue av = new StringAttribute("John");
			Attribute attr = new Attribute(URI.create(PolicyConstants.SUBJECT_ID_DEFAULT), 
					null, null, av, false, 0);
			Attributes attrs = new Attributes(URI.create(PolicyConstants.SUBJECT_CATEGORY_URI),
					Collections.singleton(attr));
			Set<Attributes> attributesSet = Collections.singleton(attrs);
			RequestCtx request = new RequestCtx(attributesSet, null);
			System.out.println("**REQUEST**");             
			request.encode(System.out);
			
			ResponseCtx response = pdp.evaluate(request);
			System.out.println("**RESPONSE**");             
			System.out.println(response.encode());             
		}
		else {
			String requestStr = new java.util.Scanner(new java.io.File(requestFile)).useDelimiter("\\Z").next();
			//RequestCtx request = org.wso2.balana.ctx.RequestCtxFactory.getFactory().getRequestCtx(requestStr);
			//org.wso2.balana.ctx.AbstractRequestCtx request 
			
			String responseStr = pdp.evaluate(requestStr);
			System.out.println(responseStr);
        }
    }
}
