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


import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;


import org.wso2.balana.attr.AttributeValue;
import org.wso2.balana.attr.BagAttribute;
import org.wso2.balana.attr.StringAttribute;
import org.wso2.balana.cond.EvaluationResult;
import org.wso2.balana.ctx.EvaluationCtx;
import org.wso2.balana.ctx.Status;
import org.wso2.balana.utils.Constants.PolicyConstants;

/**
 * 
 * @author Ludwig Seitz
 *
 */
public class DBAttributeFinderModule extends org.wso2.balana.finder.AttributeFinderModule {
    
    /**
     * The default user of the database
     */
    private String defaultUser = "PaaSwordUser";
    
    /**
     * The default password of the default user. 
     * CAUTION! Only use this for testing, this is very insecure
     * (but then if you didn't figure that out yourself, I cannot help you
     * anyway).
     */
    private String defaultPassword = "PaaSword";
    
    /**
     * The default connection URL for the database. Here we use a 
     * MySQL database on port 3306.
     */
    private String defaultDbUrl = "jdbc:mysql://localhost:3306";
    
    /**
     * A prepared connection.
     */
    private Connection conn = null;
    
    /**
     * A prepared SELECT statement to get an attribute value.
     * 
     * Parameters: subjectId
     */
    private PreparedStatement selectRole;
    
    public static String dbName = "XACMLAttributes";
    
    public static String dbRoleTable = "roles";
    
    public static String dbRoleColumn = "role";
    
    public static String dbSubIdColumn = "subjectId";
    
    /**
     * Constructor
     * @throws SQLException 
     */
    public DBAttributeFinderModule(String user, String pwd, String dbUrl) 
            throws SQLException {
        if (dbUrl != null) {
            this.defaultDbUrl = dbUrl;
        }
        if (user != null) {
            this.defaultUser = user;
        }
        if (pwd != null) {
            this.defaultPassword = pwd;
        }
        
        Properties connectionProps = new Properties();      
        connectionProps.put("user", this.defaultUser);
        connectionProps.put("password", this.defaultPassword);
        this.conn = DriverManager.getConnection(this.defaultDbUrl, 
                connectionProps);
        
        this.selectRole = this.conn.prepareStatement("SELECT "
                + DBAttributeFinderModule.dbRoleColumn
                + " FROM " + DBAttributeFinderModule.dbName + "." 
                + DBAttributeFinderModule.dbRoleTable
                + " WHERE " + DBAttributeFinderModule.dbSubIdColumn + "=?;");
    }
    
    
    /**
     * Initialize the database. This will lead to exceptions if the 
     * user already exits (because in the lower versions of MySQL
     * there is not CREATE USER IF NOT EXISTS).
     * 
     * @param rootPwd  the root password of the DB administrator
     * @throws SQLException 
     */
    public static void initDB(String rootPwd, String user, String userPwd,
            String dbUrl) throws SQLException {
        String createDB = "CREATE DATABASE IF NOT EXISTS " 
                + DBAttributeFinderModule.dbName
                + " CHARACTER SET utf8 COLLATE utf8_bin;";
    
        String createRoleTable = "CREATE TABLE IF NOT EXISTS " 
                + DBAttributeFinderModule.dbName 
                + "." + DBAttributeFinderModule.dbRoleTable + "(" 
                + DBAttributeFinderModule.dbSubIdColumn 
                + " varchar(255) NOT NULL, " 
                + DBAttributeFinderModule.dbRoleColumn 
                +  " varchar(255) NOT NULL);";
        
        String cUser = "CREATE USER '" + user 
                + "'@'localhost' IDENTIFIED BY '" + userPwd + "';";
        
        String authzUser = "GRANT DELETE, INSERT, SELECT, UPDATE ON "
               + DBAttributeFinderModule.dbName + ".* TO '" + user
               + "'@'localhost';";
        
        Properties connectionProps = new Properties();
        connectionProps.put("user", "root");
        connectionProps.put("password", rootPwd);
        Connection rootConn = DriverManager.getConnection(
                dbUrl, connectionProps);
        Statement stmt = rootConn.createStatement();
        stmt.execute(createDB);
        stmt.execute(createRoleTable);
        try {
            stmt.execute(cUser);
            stmt.execute(authzUser);    
        } catch (SQLException s) {
            stmt.close();
            rootConn.close();
            return;
        }
        stmt.close();
        rootConn.close();
    }

    @Override
    public boolean isDesignatorSupported() {
        return true;
    }
    
    public void close() throws SQLException {
        this.selectRole.close();
        this.conn.close();
    }
    
    @Override
    public EvaluationResult findAttribute(URI attributeType, URI attributeId, 
            String issuer, URI category, EvaluationCtx context) 
	{
		//System.out.println("CHECKING ATTR: "+attributeId);
        //if (!attributeId.toString().equals("role")) {
        if (!attributeId.toString().startsWith("urn:my-test:attributes:attr-db-")) {
            return new EvaluationResult(
                    BagAttribute.createEmptyBag(attributeType));
        }
        if (!category.toString().equals(PolicyConstants.SUBJECT_CATEGORY_URI)) {
        //if (!category.toString().equals("urn:oasis:names:tc:xacml:1.0:subject-category:access-subject")) {
            return new EvaluationResult(
                    BagAttribute.createEmptyBag(attributeType));
        }
        if (!attributeType.toString().equals(
                PolicyConstants.STRING_DATA_TYPE)) {
            return new EvaluationResult(
                    BagAttribute.createEmptyBag(attributeType));
        }
		//System.out.println("PASS 1");
		
        /*EvaluationResult subRes = context.getAttribute(
                URI.create(PolicyConstants.STRING_DATA_TYPE), 
                URI.create(PolicyConstants.SUBJECT_ID_DEFAULT), null,
                URI.create(PolicyConstants.SUBJECT_CATEGORY_URI));
        
        if (subRes.indeterminate()) {
            return subRes; //Some error happened
        }
        if (!subRes.getAttributeValue().isBag()) {
            //The results should have been a bag
            return new EvaluationResult(
                    new Status(Collections.singletonList(
                            Status.STATUS_PROCESSING_ERROR)));
        }    
        BagAttribute subBag = (BagAttribute)subRes.getAttributeValue();   
        if (subBag.isEmpty()) {
            return new EvaluationResult(
                    new Status(Collections.singletonList(
                            Status.STATUS_PROCESSING_ERROR), 
                            "Missing subject in request"));
        }
		System.out.println("PASS 2");
        
        //Now we are getting somwhere
        List<AttributeValue> bag = new ArrayList<AttributeValue>();
        Iterator<AttributeValue> iter = subBag.iterator();
        while(iter.hasNext()) {
            AttributeValue subjectAV = iter.next();
            String subjectId = subjectAV.encode();
            try {
                this.selectRole.setString(1, subjectId);
                ResultSet result = this.selectRole.executeQuery();
                while (result.next()) {
                    bag.add(new StringAttribute(
                            result.getString(dbRoleColumn)));
                }
                result.close();
            } catch (SQLException e) {
                return new EvaluationResult(
                        new Status(Collections.singletonList(
                                Status.STATUS_PROCESSING_ERROR), 
                                e.getMessage()));
            }
            
        }*/
		
		String subjectId = attributeId.toString().replace("urn:my-test:attributes:","");
        List<AttributeValue> bag = new ArrayList<AttributeValue>();
		try {
			this.selectRole.setString(1, subjectId);
			ResultSet result = this.selectRole.executeQuery();
			while (result.next()) {
				bag.add(new StringAttribute(
						result.getString(dbRoleColumn)));
			}
			result.close();
		} catch (SQLException e) {
			return new EvaluationResult(
					new Status(Collections.singletonList(
							Status.STATUS_PROCESSING_ERROR), 
							e.getMessage()));
		}
		
		//System.out.println("RESULT:  "+bag);
        return new EvaluationResult(new BagAttribute(
                URI.create(PolicyConstants.STRING_DATA_TYPE), bag));
    }
}
