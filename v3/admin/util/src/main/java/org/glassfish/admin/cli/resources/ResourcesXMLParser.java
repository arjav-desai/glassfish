/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2010 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.admin.cli.resources;

import org.glassfish.resource.common.Resource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;  
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;  
import org.xml.sax.InputSource;
import org.xml.sax.ErrorHandler;
import org.xml.sax.EntityResolver;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;

import com.sun.enterprise.util.SystemPropertyConstants;


//i18n import
import com.sun.enterprise.util.i18n.StringManager;

import java.util.*;

import org.xml.sax.ext.LexicalHandler;

import org.glassfish.api.I18n;
import static org.glassfish.resource.common.ResourceConstants.*;

/**
 * This Class reads the Properties (resources) from the xml file supplied 
 * to constructor
 */
@I18n("resources.parser")
public class ResourcesXMLParser implements EntityResolver
{

    private File resourceFile = null;
    private Document document;
    private List<Resource> vResources;
    private boolean isDoctypePresent = false;
    /* list of resources that needs to be created prior to module deployment. This 
     * includes all non-Connector resources and resource-adapter-config
     */
//    private List<Resource> connectorResources;
    
    /* Includes all connector resources in the order in which the resources needs
      to be created */
//    private List<Resource> nonConnectorResources;
    
    // i18n StringManager
    private static StringManager localStrings =
        StringManager.getManager( ResourcesXMLParser.class );
    
    private static final int NONCONNECTOR = 2;
    private static final int CONNECTOR = 1;

    private static final String SUN_RESOURCES = "sun-resources";

    public static final String JAVA_APP_SCOPE_PREFIX = "java:app/";
    public static final String JAVA_COMP_SCOPE_PREFIX = "java:comp/";
    public static final String JAVA_MODULE_SCOPE_PREFIX = "java:module/";
    public static final String JAVA_GLOBAL_SCOPE_PREFIX = "java:global/";

    /**
     * List of naming scopes
     */
    public static final List<String> namingScopes = Collections.unmodifiableList(
            Arrays.asList(
                JAVA_APP_SCOPE_PREFIX,
                JAVA_COMP_SCOPE_PREFIX,
                JAVA_MODULE_SCOPE_PREFIX,
                JAVA_GLOBAL_SCOPE_PREFIX
            ));

    /** Creates new ResourcesXMLParser */
    public ResourcesXMLParser(File resourceFile) throws Exception {
        this.resourceFile = resourceFile;
        initProperties();
        vResources = new ArrayList<Resource>();
        String scope = "";
        generateResourceObjects(scope);
    }

    /** Creates new ResourcesXMLParser */
    public ResourcesXMLParser(File resourceFile, String scope) throws Exception {
        this.resourceFile = resourceFile;
        initProperties();
        vResources = new ArrayList<Resource>();
        generateResourceObjects(scope);
    }

    /**
     *Parse the XML Properties file and populate it into document object
     */
    private void initProperties() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            AddResourcesErrorHandler  errorHandler = new AddResourcesErrorHandler();
            factory.setValidating(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver(this);
            builder.setErrorHandler(errorHandler);
            if (resourceFile == null) {
		String msg = localStrings.getString( "resources.parser.no_resource_file", 
                                                        "Resource file ({0} does not exist", 
                                                        resourceFile );
                throw new Exception( msg );
            }
            InputSource is = new InputSource(resourceFile.toURI().toString());
            document = builder.parse(is);
            detectDeprecatedDescriptor();
        }/*catch(SAXParseException saxpe){
            throw new Exception(saxpe.getLocalizedMessage());
        }*/catch (SAXException sxe) {
            //This check is introduced to check if DOCTYPE is present in sun-resources.xml
            //And throw proper error message if DOCTYPE is missing
            try {
                SAXParserFactory spf = SAXParserFactory.newInstance();
                SAXParser sp = spf.newSAXParser();
                sp.setProperty("http://xml.org/sax/properties/lexical-handler", new MyLexicalHandler());
                sp.getXMLReader().parse(new InputSource(resourceFile.toString()));
            } catch (ParserConfigurationException ex) {
            } catch (SAXException ex) {
            } catch (IOException ex) {
            }
            if(!isDoctypePresent){
                throw new Exception(
                    localStrings.getString("resources.parser.doctype_not_present_in_xml", 
                                            "Error Parsing the xml ({0}), doctype is not present", 
                                            resourceFile.toString()));
            }
            Exception  x = sxe;
            if (sxe.getException() != null)
               x = sxe.getException();
            throw new Exception(x.getLocalizedMessage());
        }
        catch (ParserConfigurationException pce) {
            // Parser with specified options can't be built
            throw new Exception(pce.getLocalizedMessage());
        }
        catch (IOException ioe) {
            throw new Exception(ioe.getLocalizedMessage());
        }
    }

    /**
     * detects and logs a warning if any of the deprecated descriptor (sun-resources*.dtd) is specified
     */
    private void detectDeprecatedDescriptor() {
        String publicId = document.getDoctype().getPublicId();
        String systemId = document.getDoctype().getSystemId();
        Logger logger  = Logger.getLogger(ResourcesXMLParser.class.getName());
        if( (publicId != null && publicId.contains(SUN_RESOURCES)) ||
                (systemId != null && systemId.contains(SUN_RESOURCES))){
            String msg = localStrings.getString(
                    "deprecated.resources.dtd", resourceFile.getAbsolutePath() );
            logger.log(Level.FINEST, msg);
        }
    }

    /**
     * Get All the resources from the document object.
     *
     */
    private void generateResourceObjects(String scope) throws Exception
    {
        if (document != null) {
            for (Node nextKid = document.getDocumentElement().getFirstChild();
                    nextKid != null; nextKid = nextKid.getNextSibling()) 
            {
                String nodeName = nextKid.getNodeName();
                if (nodeName.equalsIgnoreCase(Resource.CUSTOM_RESOURCE)) {
                    generateCustomResource(nextKid, scope);
                }
                else if (nodeName.equalsIgnoreCase(Resource.EXTERNAL_JNDI_RESOURCE)) 
                {
                    generateJNDIResource(nextKid, scope);
                }
                else if (nodeName.equalsIgnoreCase(Resource.JDBC_RESOURCE)) 
                {
                    generateJDBCResource(nextKid, scope);
                }
                else if (nodeName.equalsIgnoreCase(Resource.JDBC_CONNECTION_POOL)) 
                {
                    generateJDBCConnectionPoolResource(nextKid, scope);
                }
                else if (nodeName.equalsIgnoreCase(Resource.MAIL_RESOURCE)) 
                {
                    generateMailResource(nextKid, scope);
                }
                else if (nodeName.equalsIgnoreCase(Resource.ADMIN_OBJECT_RESOURCE))
                {
                    generateAdminObjectResource(nextKid, scope);
                }
                else if (nodeName.equalsIgnoreCase(Resource.CONNECTOR_RESOURCE)) 
                {
                    generateConnectorResource(nextKid, scope);
                }
                else if (nodeName.equalsIgnoreCase(Resource.CONNECTOR_CONNECTION_POOL)) 
                {
                    generateConnectorConnectionPoolResource(nextKid, scope);
                }
                else if (nodeName.equalsIgnoreCase(Resource.RESOURCE_ADAPTER_CONFIG)) 
                {
                    generateResourceAdapterConfig(nextKid, scope);
                }
                else if (nodeName.equalsIgnoreCase(Resource.CONNECTOR_WORK_SECURITY_MAP))
                {
                    generateWorkSecurityMap(nextKid, scope);
                }
            }
        }
    }
    
    /**
     * Sorts the resources defined in the resources configuration xml file into
     * two buckets
     *  a) list of resources that needs to be created prior to
     *  module deployment. This includes all non-Connector resources
     *  and resource-adapter-config
     *  b) includes all connector resources in the order in which the resources needs
     *  to be created
     *  and returns the requested resources bucker to the caller.
     *   
     *  @param resources Resources list as defined in sun-resources.xml
     *  @param type Specified either ResourcesXMLParser.CONNECTOR or 
     *  ResourcesXMLParser.NONCONNECTOR to indicate the type of 
     *  resources are needed by the client of the ResourcesXMLParser
     *  @param isResourceCreation During the resource Creation Phase, RA configs are
     *  added to the nonConnector resources list so that they can be created in the
     *  <code>PreResCreationPhase</code>. When the isResourceCreation is false, the 
     *  RA config resuorces are added to the Connector Resources list, so that they 
     *  can be deleted as all other connector resources in the 
     *  <code>PreResDeletionPhase</code>
     */
    private static List<Resource> getResourcesOfType(List<Resource> resources, 
                                            int type, boolean isResourceCreation, boolean ignoreDuplicates) {
        List<Resource> nonConnectorResources = new ArrayList<Resource>();
        List<Resource> connectorResources = new ArrayList<Resource>();
        
        for (Resource res : resources) {
            if (isConnectorResource(res)) {
                if (res.getType().equals(Resource.RESOURCE_ADAPTER_CONFIG)) {
                    if(isResourceCreation) {
                        //RA config is considered as a nonConnector Resource, 
                        //during sun-resources.xml resource-creation phase, so that 
                        //it could be created before the RAR is deployed.
                        addToList(nonConnectorResources, res, ignoreDuplicates);
                    } else {
                        addToList(connectorResources, res, ignoreDuplicates);
                    }
                } else {
                    addToList(connectorResources, res, ignoreDuplicates);
                }
            } else {
                addToList(nonConnectorResources, res, ignoreDuplicates);
            }
        }
        
        List<Resource> finalSortedConnectorList = sortConnectorResources(connectorResources);
        List<Resource> finalSortedNonConnectorList = sortNonConnectorResources(nonConnectorResources);
        if (type == CONNECTOR) {
            return finalSortedConnectorList;
        } else {
            return finalSortedNonConnectorList;
        }
    }

    private static void addToList(List<Resource> resources, Resource res, boolean ignoreDuplicates){
        if(ignoreDuplicates){
            if(!resources.contains(res)){
                resources.add(res);
            }
        }else{
            resources.add(res);
        }
    }

    /**
     * Sort connector resources
     * Resource Adapter configs are added first.
     * Pools are then added to the list, so that the connection
     * pools can be created prior to any other connector resource defined
     * in the resources configuration xml file.
     * @param connectorResources List of Resources to be sorted.
     * @return The sorted list.
     */
    private static List<Resource> sortConnectorResources(List<Resource> connectorResources) {
        List<Resource> raconfigs = new ArrayList<Resource>();
        List<Resource> ccps = new ArrayList<Resource>();
        List<Resource> others = new ArrayList<Resource>();
        
        List<Resource> finalSortedConnectorList = new ArrayList<Resource>();
        
        for (Resource resource : connectorResources) {
            if (resource.getType().equals(Resource.RESOURCE_ADAPTER_CONFIG)){
                raconfigs.add(resource);
            } else if (resource.getType().equals(Resource.CONNECTOR_CONNECTION_POOL)) {
                ccps.add(resource);
            } else {
                others.add(resource);
            }
        }
        
        finalSortedConnectorList.addAll(raconfigs);
        finalSortedConnectorList.addAll(ccps);
        finalSortedConnectorList.addAll(others);
        return finalSortedConnectorList;
    }
    
    /**
     * Sort non connector resources
     * JDBC Pools are added first to the list, so that the conncetion
     * pools can be created prior to any other jdbc resource defined
     * in the resources configuration xml file.
     * @param nonConnectorResources List of Resources to be sorted.
     * @return The sorted list.
     */
    private static List<Resource> sortNonConnectorResources(List<Resource> nonConnectorResources) {
        List<Resource> jdbccps = new ArrayList<Resource>();
        List<Resource> pmfs = new ArrayList<Resource>();
        List<Resource> others = new ArrayList<Resource>();
        
        List<Resource> finalSortedNonConnectorList = new ArrayList<Resource>();
        
        for (Resource resource : nonConnectorResources) {
            if(resource.getType().equals(Resource.JDBC_CONNECTION_POOL)) {
                jdbccps.add(resource);
            } else if(resource.getType().equals(Resource.PERSISTENCE_MANAGER_FACTORY_RESOURCE)) {
                //TODO throw exception as pmf resource is not supported anymore
                pmfs.add(resource);
            } else {
                others.add(resource);
            }
        }
        
        finalSortedNonConnectorList.addAll(jdbccps);
        finalSortedNonConnectorList.addAll(others);
        finalSortedNonConnectorList.addAll(pmfs);
        return finalSortedNonConnectorList;
    }
    
    /**
     * Determines if the passed in <code>Resource</code> is a connector
     * resource. A connector resource is either a connector connection pool or a
     * connector resource, security map, ra config or an admin object
     * 
     * @param res Resource that needs to be tested
     * @return
     */
    private static boolean isConnectorResource(Resource res) {
        String type = res.getType();
        return (    
                    (type.equals(Resource.ADMIN_OBJECT_RESOURCE)) ||
                    (type.equals(Resource.CONNECTOR_CONNECTION_POOL)) ||
                    (type.equals(Resource.CONNECTOR_RESOURCE)) ||
                    (type.equals(Resource.CONNECTOR_SECURITY_MAP)) ||
                    (type.equals(Resource.RESOURCE_ADAPTER_CONFIG)) 
            );
    }

    private String getScopedName(String name, String scope) throws Exception{
        if(namingScopes.contains(scope)){
            if(name != null){
                for(String namingScope : namingScopes){
                    if(name.startsWith(namingScope) && !namingScope.equals(scope)){
                        String msg = localStrings.getString( "invalid.scope.defined.for.resource",
                                                                "Resource [ {0} ] is not allowed to specify the scope " +
                                                                        "[ {1} ]. Acceptable scope for this resource" +
                                                                        " is [ {2} ]",
                                                                name, namingScope, scope );
                        throw new IllegalStateException(msg);
                    }
                }
                if(!name.startsWith(scope)){
                    name = scope + name;
                }
            }
        }
        return name;
    }

    /*
     * Generate the Custom resource
     */
    private void generateCustomResource(Node nextKid, String scope) throws Exception
    {
        NamedNodeMap attributes = nextKid.getAttributes();
        
        if (attributes == null)
            return;
        
        Node jndiNameNode = attributes.getNamedItem(JNDI_NAME);
        String jndiName = getScopedName(jndiNameNode.getNodeValue(), scope);
        
        Node resTypeNode = attributes.getNamedItem(RES_TYPE);
        String resType = resTypeNode.getNodeValue();
        
        Node factoryClassNode = attributes.getNamedItem(FACTORY_CLASS);
        String factoryClass = factoryClassNode.getNodeValue();
        
        Node enabledNode = attributes.getNamedItem(ENABLED);
        
        Resource customResource = new Resource(Resource.CUSTOM_RESOURCE);
        customResource.setAttribute(JNDI_NAME, jndiName);
        customResource.setAttribute(RES_TYPE, resType);
        customResource.setAttribute(FACTORY_CLASS, factoryClass);
        if (enabledNode != null) {
           String sEnabled = enabledNode.getNodeValue();
           customResource.setAttribute(ENABLED, sEnabled);
        }
        
        NodeList children = nextKid.getChildNodes();
        generatePropertyElement(customResource, children);
        vResources.add(customResource);
        
        //debug strings
        printResourceElements(customResource);
    }
    
    /**
     * Generate the JNDI resource
     */
    private void generateJNDIResource(Node nextKid, String scope) throws Exception
    {
        NamedNodeMap attributes = nextKid.getAttributes();
        if (attributes == null)
            return;
        
        Node jndiNameNode = attributes.getNamedItem(JNDI_NAME);
        String jndiName = getScopedName(jndiNameNode.getNodeValue(), scope);
        Node jndiLookupNode = attributes.getNamedItem(JNDI_LOOKUP);
        String jndiLookup = jndiLookupNode.getNodeValue();
        Node resTypeNode = attributes.getNamedItem(RES_TYPE);
        String resType = resTypeNode.getNodeValue();
        Node factoryClassNode = attributes.getNamedItem(FACTORY_CLASS);
        String factoryClass = factoryClassNode.getNodeValue();
        Node enabledNode = attributes.getNamedItem(ENABLED);
        
        Resource jndiResource = new Resource(Resource.EXTERNAL_JNDI_RESOURCE);
        jndiResource.setAttribute(JNDI_NAME, jndiName);
        jndiResource.setAttribute(JNDI_LOOKUP, jndiLookup);
        jndiResource.setAttribute(RES_TYPE, resType);
        jndiResource.setAttribute(FACTORY_CLASS, factoryClass);
        if (enabledNode != null) {
           String sEnabled = enabledNode.getNodeValue();
           jndiResource.setAttribute(ENABLED, sEnabled);
        }
        
        NodeList children = nextKid.getChildNodes();
        generatePropertyElement(jndiResource, children);
        vResources.add(jndiResource);
        
        //debug strings
        printResourceElements(jndiResource);
    }
    
    /**
     * Generate the JDBC resource
     */
    private void generateJDBCResource(Node nextKid, String scope) throws Exception {
        NamedNodeMap attributes = nextKid.getAttributes();
        if (attributes == null)
            return;
        
        Node jndiNameNode = attributes.getNamedItem(JNDI_NAME);
        String jndiName = getScopedName(jndiNameNode.getNodeValue(), scope);
        Node poolNameNode = attributes.getNamedItem(POOL_NAME);
        String poolName = getScopedName(poolNameNode.getNodeValue(), scope);
        Node enabledNode = attributes.getNamedItem(ENABLED);

        Resource jdbcResource = new Resource(Resource.JDBC_RESOURCE);
        jdbcResource.setAttribute(JNDI_NAME, jndiName);
        jdbcResource.setAttribute(POOL_NAME, poolName);
        if (enabledNode != null) {
           String enabledName = enabledNode.getNodeValue();
           jdbcResource.setAttribute(ENABLED, enabledName);
        }
        
        NodeList children = nextKid.getChildNodes();
        //get description
        if (children != null) 
        {
            for (int ii=0; ii<children.getLength(); ii++) 
            {
                if (children.item(ii).getNodeName().equals("description")) {
                    if (children.item(ii).getFirstChild() != null) {
                        jdbcResource.setDescription(
                            children.item(ii).getFirstChild().getNodeValue());
                    } 
                }
            }
        }

        vResources.add(jdbcResource);
        
        //debug strings
        printResourceElements(jdbcResource);
    }
    
    /**
     * Generate the JDBC Connection pool Resource
     */
    private void generateJDBCConnectionPoolResource(Node nextKid, String scope) throws Exception
    {
        NamedNodeMap attributes = nextKid.getAttributes();
        if (attributes == null)
            return;
        
        Node nameNode = attributes.getNamedItem(CONNECTION_POOL_NAME);
        String name = getScopedName(nameNode.getNodeValue(), scope);
        Node nSteadyPoolSizeNode = attributes.getNamedItem(STEADY_POOL_SIZE);
        Node nMaxPoolSizeNode = attributes.getNamedItem(MAX_POOL_SIZE);
        Node nMaxWaitTimeInMillisNode  = 
             attributes.getNamedItem(MAX_WAIT_TIME_IN_MILLIS);
        Node nPoolSizeQuantityNode  = 
             attributes.getNamedItem(POOL_SIZE_QUANTITY);
        Node nIdleTimeoutInSecNode  = 
             attributes.getNamedItem(IDLE_TIME_OUT_IN_SECONDS);
        Node nIsConnectionValidationRequiredNode  = 
             attributes.getNamedItem(IS_CONNECTION_VALIDATION_REQUIRED);
        Node nConnectionValidationMethodNode  = 
             attributes.getNamedItem(CONNECTION_VALIDATION_METHOD);
        Node nFailAllConnectionsNode  = 
             attributes.getNamedItem(FAIL_ALL_CONNECTIONS);
        Node nValidationTableNameNode  = 
             attributes.getNamedItem(VALIDATION_TABLE_NAME);
        Node nResType  = attributes.getNamedItem(RES_TYPE);
        Node nTransIsolationLevel  = 
             attributes.getNamedItem(TRANS_ISOLATION_LEVEL);
        Node nIsIsolationLevelQuaranteed  = 
             attributes.getNamedItem(IS_ISOLATION_LEVEL_GUARANTEED);
        Node datasourceNode = attributes.getNamedItem(DATASOURCE_CLASS);
        Node nonTransactionalConnectionsNode = 
                attributes.getNamedItem(NON_TRANSACTIONAL_CONNECTIONS);
        Node allowNonComponentCallersNode = 
                attributes.getNamedItem(ALLOW_NON_COMPONENT_CALLERS);
        Node validateAtmostOncePeriodNode = 
                attributes.getNamedItem(VALIDATE_ATMOST_ONCE_PERIOD_IN_SECONDS);
        Node connectionLeakTimeoutNode = 
                attributes.getNamedItem(CONNECTION_LEAK_TIMEOUT_IN_SECONDS);
        Node connectionLeakReclaimNode = 
                attributes.getNamedItem(CONNECTION_LEAK_RECLAIM);
        Node connectionCreationRetryAttemptsNode = 
                attributes.getNamedItem(CONNECTION_CREATION_RETRY_ATTEMPTS);
        Node connectionCreationRetryIntervalNode = 
                attributes.getNamedItem(CONNECTION_CREATION_RETRY_INTERVAL_IN_SECONDS);
        Node statementTimeoutNode = 
                attributes.getNamedItem(STATEMENT_TIMEOUT_IN_SECONDS);
        Node lazyConnectionEnlistmentNode = 
                attributes.getNamedItem(LAZY_CONNECTION_ENLISTMENT);
        Node lazyConnectionAssociationNode = 
                attributes.getNamedItem(LAZY_CONNECTION_ASSOCIATION);
        Node associateWithThreadNode = 
                attributes.getNamedItem(ASSOCIATE_WITH_THREAD);
        Node matchConnectionsNode = 
                attributes.getNamedItem(MATCH_CONNECTIONS);
        Node maxConnectionUsageCountNode = 
                attributes.getNamedItem(MAX_CONNECTION_USAGE_COUNT);
        Node wrapJDBCObjectsNode = 
                attributes.getNamedItem(WRAP_JDBC_OBJECTS);
        Node poolingNode
            = attributes.getNamedItem(POOLING);
        Node pingNode
            = attributes.getNamedItem(PING);
        Node customValidationNode
            = attributes.getNamedItem(CUSTOM_VALIDATION);
        Node driverClassNameNode
            = attributes.getNamedItem(DRIVER_CLASSNAME);
        Node initSqlNode
            = attributes.getNamedItem(INIT_SQL);
        Node sqlTraceListenersNode
            = attributes.getNamedItem(SQL_TRACE_LISTENERS);
        Node statementCacheSizeNode
            = attributes.getNamedItem(STATEMENT_CACHE_SIZE);

        String datasource = datasourceNode.getNodeValue();
        
        Resource jdbcConnPool = new Resource(Resource.JDBC_CONNECTION_POOL);
        jdbcConnPool.setAttribute(CONNECTION_POOL_NAME, name);
        jdbcConnPool.setAttribute(DATASOURCE_CLASS, datasource);
        if (nSteadyPoolSizeNode != null) {
           String sSteadyPoolSize = nSteadyPoolSizeNode.getNodeValue();
           jdbcConnPool.setAttribute(STEADY_POOL_SIZE, sSteadyPoolSize);
        }
        if (nMaxPoolSizeNode != null) {
           String sMaxPoolSize = nMaxPoolSizeNode.getNodeValue();
           jdbcConnPool.setAttribute(MAX_POOL_SIZE, sMaxPoolSize);
        }
        if (nMaxWaitTimeInMillisNode != null) {
           String sMaxWaitTimeInMillis = nMaxWaitTimeInMillisNode.getNodeValue();
           jdbcConnPool.setAttribute(MAX_WAIT_TIME_IN_MILLIS, sMaxWaitTimeInMillis);
        }
        if (nPoolSizeQuantityNode != null) {
           String sPoolSizeQuantity = nPoolSizeQuantityNode.getNodeValue();
           jdbcConnPool.setAttribute(POOL_SIZE_QUANTITY, sPoolSizeQuantity);
        }
        if (nIdleTimeoutInSecNode != null) {
           String sIdleTimeoutInSec = nIdleTimeoutInSecNode.getNodeValue();
           jdbcConnPool.setAttribute(IDLE_TIME_OUT_IN_SECONDS, sIdleTimeoutInSec);
        }
        if (nIsConnectionValidationRequiredNode != null) {
           String sIsConnectionValidationRequired = nIsConnectionValidationRequiredNode.getNodeValue();
           jdbcConnPool.setAttribute(IS_CONNECTION_VALIDATION_REQUIRED, sIsConnectionValidationRequired);
        }
        if (nConnectionValidationMethodNode != null) {
           String sConnectionValidationMethod = nConnectionValidationMethodNode.getNodeValue();
           jdbcConnPool.setAttribute(CONNECTION_VALIDATION_METHOD, sConnectionValidationMethod);
        }
        if (nFailAllConnectionsNode != null) {
           String sFailAllConnection = nFailAllConnectionsNode.getNodeValue();
           jdbcConnPool.setAttribute(FAIL_ALL_CONNECTIONS, sFailAllConnection);
        }
        if (nValidationTableNameNode != null) {
           String sValidationTableName = nValidationTableNameNode.getNodeValue();
           jdbcConnPool.setAttribute(VALIDATION_TABLE_NAME, sValidationTableName);
        }
        if (nResType != null) {
           String sResType = nResType.getNodeValue();
           jdbcConnPool.setAttribute(RES_TYPE, sResType);
        }
        if (nTransIsolationLevel != null) {
           String sTransIsolationLevel = nTransIsolationLevel.getNodeValue();
           jdbcConnPool.setAttribute(TRANS_ISOLATION_LEVEL, sTransIsolationLevel);
        }
        if (nIsIsolationLevelQuaranteed != null) {
           String sIsIsolationLevelQuaranteed = 
                  nIsIsolationLevelQuaranteed.getNodeValue();
           jdbcConnPool.setAttribute(IS_ISOLATION_LEVEL_GUARANTEED, 
                                     sIsIsolationLevelQuaranteed);
        }
        if (nonTransactionalConnectionsNode != null) {
           jdbcConnPool.setAttribute(NON_TRANSACTIONAL_CONNECTIONS, 
                                        nonTransactionalConnectionsNode.getNodeValue());
        }
        if (allowNonComponentCallersNode != null) {
           jdbcConnPool.setAttribute(ALLOW_NON_COMPONENT_CALLERS, 
                                        allowNonComponentCallersNode.getNodeValue());
        }
        if (validateAtmostOncePeriodNode != null) {
           jdbcConnPool.setAttribute(VALIDATE_ATMOST_ONCE_PERIOD_IN_SECONDS,
                                        validateAtmostOncePeriodNode.getNodeValue());
        }
        if (connectionLeakTimeoutNode != null) {
           jdbcConnPool.setAttribute(CONNECTION_LEAK_TIMEOUT_IN_SECONDS,
                                        connectionLeakTimeoutNode.getNodeValue());
        }
        if (connectionLeakReclaimNode != null) {
           jdbcConnPool.setAttribute(CONNECTION_LEAK_RECLAIM, 
                                        connectionLeakReclaimNode.getNodeValue());
        }
        if (connectionCreationRetryAttemptsNode != null) {
           jdbcConnPool.setAttribute(CONNECTION_CREATION_RETRY_ATTEMPTS, 
                                        connectionCreationRetryAttemptsNode.getNodeValue());
        }
        if (connectionCreationRetryIntervalNode != null) {
           jdbcConnPool.setAttribute(CONNECTION_CREATION_RETRY_INTERVAL_IN_SECONDS, 
                                        connectionCreationRetryIntervalNode.getNodeValue());
        }
        if (statementTimeoutNode != null) {
           jdbcConnPool.setAttribute(STATEMENT_TIMEOUT_IN_SECONDS,
                                        statementTimeoutNode.getNodeValue());
        }
        if (lazyConnectionEnlistmentNode != null) {
           jdbcConnPool.setAttribute(LAZY_CONNECTION_ENLISTMENT, 
                                        lazyConnectionEnlistmentNode.getNodeValue());
        }
        if (lazyConnectionAssociationNode != null) {
           jdbcConnPool.setAttribute(LAZY_CONNECTION_ASSOCIATION, 
                                        lazyConnectionAssociationNode.getNodeValue());
        }
        if (associateWithThreadNode != null) {
           jdbcConnPool.setAttribute(ASSOCIATE_WITH_THREAD, 
                                        associateWithThreadNode.getNodeValue());
        }
        if (matchConnectionsNode != null) {
           jdbcConnPool.setAttribute(MATCH_CONNECTIONS, 
                                        matchConnectionsNode.getNodeValue());
        }
        if (maxConnectionUsageCountNode != null) {
           jdbcConnPool.setAttribute(MAX_CONNECTION_USAGE_COUNT, 
                                        maxConnectionUsageCountNode.getNodeValue());
        }
        if (wrapJDBCObjectsNode != null) {
           jdbcConnPool.setAttribute(WRAP_JDBC_OBJECTS, 
                                        wrapJDBCObjectsNode.getNodeValue());
        }
        if(poolingNode != null){
           String pooling = poolingNode.getNodeValue();
           jdbcConnPool.setAttribute(POOLING,pooling);
        }
        if(pingNode != null){
           String ping = pingNode.getNodeValue();
           jdbcConnPool.setAttribute(PING,ping);
        }
        if(initSqlNode != null){
           String initSQL = initSqlNode.getNodeValue();
           jdbcConnPool.setAttribute(INIT_SQL,initSQL);
        }
        if(sqlTraceListenersNode != null){
           String sqlTraceListeners= sqlTraceListenersNode.getNodeValue();
           jdbcConnPool.setAttribute(SQL_TRACE_LISTENERS, sqlTraceListeners);
        }
        if(customValidationNode != null){
           String customValidation = customValidationNode.getNodeValue();
           jdbcConnPool.setAttribute(CUSTOM_VALIDATION,customValidation);
        }
        if(driverClassNameNode != null){
           String driverClassName = driverClassNameNode.getNodeValue();
           jdbcConnPool.setAttribute(DRIVER_CLASSNAME,driverClassName);
        }
        if(statementCacheSizeNode != null){
           String statementCacheSize = statementCacheSizeNode.getNodeValue();
           jdbcConnPool.setAttribute(STATEMENT_CACHE_SIZE,statementCacheSize);
        }

        NodeList children = nextKid.getChildNodes();
        generatePropertyElement(jdbcConnPool, children);
        vResources.add(jdbcConnPool);
        
        //debug strings
        printResourceElements(jdbcConnPool);
    }
    
    /**
     * Generate the Mail resource
     */
    private void generateMailResource(Node nextKid, String scope) throws Exception
    {
        NamedNodeMap attributes = nextKid.getAttributes();
        if (attributes == null)
            return;

        Node jndiNameNode = attributes.getNamedItem(JNDI_NAME);
        Node hostNode   = attributes.getNamedItem(MAIL_HOST);
        Node userNode   = attributes.getNamedItem(MAIL_USER);
        Node fromAddressNode   = attributes.getNamedItem(MAIL_FROM_ADDRESS);
        Node storeProtoNode   = attributes.getNamedItem(MAIL_STORE_PROTO);
        Node storeProtoClassNode   = attributes.getNamedItem(MAIL_STORE_PROTO_CLASS);
        Node transProtoNode   = attributes.getNamedItem(MAIL_TRANS_PROTO);
        Node transProtoClassNode   = attributes.getNamedItem(MAIL_TRANS_PROTO_CLASS);
        Node debugNode   = attributes.getNamedItem(MAIL_DEBUG);
        Node enabledNode   = attributes.getNamedItem(ENABLED);

        String jndiName = getScopedName(jndiNameNode.getNodeValue(), scope);
        String host     = hostNode.getNodeValue();
        String user     = userNode.getNodeValue();
        String fromAddress = fromAddressNode.getNodeValue();
        
        Resource mailResource = new Resource(Resource.MAIL_RESOURCE);

        mailResource.setAttribute(JNDI_NAME, jndiName);
        mailResource.setAttribute(MAIL_HOST, host);
        mailResource.setAttribute(MAIL_USER, user);
        mailResource.setAttribute(MAIL_FROM_ADDRESS, fromAddress);
        if (storeProtoNode != null) {
           String sStoreProto = storeProtoNode.getNodeValue();
           mailResource.setAttribute(MAIL_STORE_PROTO, sStoreProto);
        }
        if (storeProtoClassNode != null) {
           String sStoreProtoClass = storeProtoClassNode.getNodeValue();
           mailResource.setAttribute(MAIL_STORE_PROTO_CLASS, sStoreProtoClass);
        }
        if (transProtoNode != null) {
           String sTransProto = transProtoNode.getNodeValue();
           mailResource.setAttribute(MAIL_TRANS_PROTO, sTransProto);
        }
        if (transProtoClassNode != null) {
           String sTransProtoClass = transProtoClassNode.getNodeValue();
           mailResource.setAttribute(MAIL_TRANS_PROTO_CLASS, sTransProtoClass);
        }
        if (debugNode != null) {
           String sDebug = debugNode.getNodeValue();
           mailResource.setAttribute(MAIL_DEBUG, sDebug);
        }
        if (enabledNode != null) {
           String sEnabled = enabledNode.getNodeValue();
           mailResource.setAttribute(ENABLED, sEnabled);
        }

        NodeList children = nextKid.getChildNodes();
        generatePropertyElement(mailResource, children);
        vResources.add(mailResource);
        
        //debug strings
        printResourceElements(mailResource);
    }
    
    /**
     * Generate the Admin Object resource
     */
    private void generateAdminObjectResource(Node nextKid, String scope) throws Exception
    {
        NamedNodeMap attributes = nextKid.getAttributes();
        if (attributes == null)
            return;
        
        Node jndiNameNode = attributes.getNamedItem(JNDI_NAME);
        String jndiName = getScopedName(jndiNameNode.getNodeValue(), scope);
        Node resTypeNode = attributes.getNamedItem(RES_TYPE);
        String resType = resTypeNode.getNodeValue();
        Node classNameNode = attributes.getNamedItem(ADMIN_OBJECT_CLASS_NAME);
        String className = null;
        if(classNameNode != null){
            className = classNameNode.getNodeValue();
        }
        Node resAdapterNode = attributes.getNamedItem(RES_ADAPTER);
        String resAdapter = resAdapterNode.getNodeValue();
        Node enabledNode = attributes.getNamedItem(ENABLED);

        Resource adminObjectResource = 
                    new Resource(Resource.ADMIN_OBJECT_RESOURCE);
        adminObjectResource.setAttribute(JNDI_NAME, jndiName);
        adminObjectResource.setAttribute(RES_TYPE, resType);
        if(classNameNode != null){
            adminObjectResource.setAttribute(ADMIN_OBJECT_CLASS_NAME, className);
        }
        adminObjectResource.setAttribute(RES_ADAPTER, resAdapter);

        if (enabledNode != null) {
           String sEnabled = enabledNode.getNodeValue();
           adminObjectResource.setAttribute(ENABLED, sEnabled);
        }
        
        NodeList children = nextKid.getChildNodes();
        generatePropertyElement(adminObjectResource, children);
        vResources.add(adminObjectResource);
        
        //debug strings
        printResourceElements(adminObjectResource);
    }

    /**
     * Generate the Connector resource
     */
    private void generateConnectorResource(Node nextKid, String scope) throws Exception
    {
        NamedNodeMap attributes = nextKid.getAttributes();
        if (attributes == null)
            return;
        
        Node jndiNameNode = attributes.getNamedItem(JNDI_NAME);
        String jndiName = getScopedName(jndiNameNode.getNodeValue(), scope);
        Node poolNameNode = attributes.getNamedItem(POOL_NAME);
        String poolName = getScopedName(poolNameNode.getNodeValue(), scope);
        Node resTypeNode = attributes.getNamedItem(RESOURCE_TYPE);
        Node enabledNode = attributes.getNamedItem(ENABLED);

        Resource connectorResource = 
                    new Resource(Resource.CONNECTOR_RESOURCE);
        connectorResource.setAttribute(JNDI_NAME, jndiName);
        connectorResource.setAttribute(POOL_NAME, poolName);
        if (resTypeNode != null) {
           String resType = resTypeNode.getNodeValue();
           connectorResource.setAttribute(RESOURCE_TYPE, resType);
        }
        if (enabledNode != null) {
           String sEnabled = enabledNode.getNodeValue();
           connectorResource.setAttribute(ENABLED, sEnabled);
        }
        
        NodeList children = nextKid.getChildNodes();
        generatePropertyElement(connectorResource, children);
        vResources.add(connectorResource);
        
        //debug strings
        printResourceElements(connectorResource);
    }

    private void generatePropertyElement(Resource rs, NodeList children) throws Exception 
    {
        if (children != null) {
            for (int i=0; i<children.getLength(); i++) {
                if (children.item(i).getNodeName().equals("property")) {
                    NamedNodeMap attNodeMap = children.item(i).getAttributes();
                    Node nameNode = attNodeMap.getNamedItem("name");
                    Node valueNode = attNodeMap.getNamedItem("value");
                    if (nameNode != null && valueNode != null) {
                        boolean bDescFound = false;
                        String sName = nameNode.getNodeValue();
                        String sValue = valueNode.getNodeValue();
                        //get property description
                        // FIX ME: Ignoring the value for description for the 
                        // time-being as there is no method available in 
                        // configMBean to set description for a property
                        Node descNode = children.item(i).getFirstChild();
                        while (descNode != null && !bDescFound) {
                            if (descNode.getNodeName().equalsIgnoreCase("description")) {
                                try {
                                    //rs.setElementProperty(sName, sValue, descNode.getFirstChild().getNodeValue());
                                    rs.setProperty(sName, sValue);
                                    bDescFound = true;
                                }
                                catch (DOMException dome) {
                                    // DOM Error
                                    throw new Exception(dome.getLocalizedMessage());
                                }
                            }
                            descNode = descNode.getNextSibling();
                        }
                        if (!bDescFound) {
                            rs.setProperty(sName, sValue);
                        }
                    }
                }
                if (children.item(i).getNodeName().equals("description")) {
                    Node descNode = children.item(i).getFirstChild();
                    if(descNode != null){
                        rs.setDescription(descNode.getNodeValue());
                    }
                }
            }
        }
    }
    
    /**
     * Generate the Connector Connection pool Resource
     */
    private void generateConnectorConnectionPoolResource(Node nextKid, String scope) throws Exception
    {
        NamedNodeMap attributes = nextKid.getAttributes();
        if (attributes == null)
            return ;
        
        Node nameNode 
            = attributes.getNamedItem(CONNECTOR_CONNECTION_POOL_NAME);
        Node raConfigNode
            = attributes.getNamedItem(RESOURCE_ADAPTER_CONFIG_NAME);
        Node conDefNode
            = attributes.getNamedItem(CONN_DEF_NAME);
        Node steadyPoolSizeNode
            = attributes.getNamedItem(CONN_STEADY_POOL_SIZE);
        Node maxPoolSizeNode 
            =  attributes.getNamedItem(CONN_MAX_POOL_SIZE);
        Node poolResizeNode
            = attributes.getNamedItem(CONN_POOL_RESIZE_QUANTITY);
        Node idleTimeOutNode
            = attributes.getNamedItem(CONN_IDLE_TIME_OUT);
        Node failAllConnNode
            = attributes.getNamedItem(CONN_FAIL_ALL_CONNECTIONS);
        Node maxWaitTimeMillisNode
            = attributes.getNamedItem(MAX_WAIT_TIME_IN_MILLIS);
        Node transactionSupportNode
            = attributes.getNamedItem(CONN_TRANSACTION_SUPPORT);
        Node connValidationReqdNode
            = attributes.getNamedItem(IS_CONNECTION_VALIDATION_REQUIRED);
        Node validateAtmostOncePeriodNode
            = attributes.getNamedItem(VALIDATE_ATMOST_ONCE_PERIOD_IN_SECONDS);
        Node connLeakTimeoutNode 
            = attributes.getNamedItem(CONNECTION_LEAK_TIMEOUT_IN_SECONDS);
        Node connLeakReclaimNode
            = attributes.getNamedItem(CONNECTION_LEAK_RECLAIM);
        Node connCreationRetryAttemptsNode
            = attributes.getNamedItem(CONNECTION_CREATION_RETRY_ATTEMPTS);
        Node connCreationRetryIntervalNode
            = attributes.getNamedItem(CONNECTION_CREATION_RETRY_INTERVAL_IN_SECONDS);
        Node lazyConnEnlistmentNode
            = attributes.getNamedItem(LAZY_CONNECTION_ENLISTMENT);
        Node lazyConnAssociationNode
            = attributes.getNamedItem(LAZY_CONNECTION_ASSOCIATION);
        Node associateWithThreadNode
            = attributes.getNamedItem(ASSOCIATE_WITH_THREAD);
        Node matchConnectionsNode
            = attributes.getNamedItem(MATCH_CONNECTIONS);
        Node maxConnUsageCountNode
            = attributes.getNamedItem(MAX_CONNECTION_USAGE_COUNT);
        Node poolingNode
            = attributes.getNamedItem(POOLING);
        Node pingNode
            = attributes.getNamedItem(PING);

        String poolName = null;
        
        Resource connectorConnPoolResource = new Resource(Resource.CONNECTOR_CONNECTION_POOL);
        if(nameNode != null){
            poolName = getScopedName(nameNode.getNodeValue(), scope);
            connectorConnPoolResource.setAttribute(CONNECTION_POOL_NAME, poolName);
        }    
       if(raConfigNode != null){
            String raConfig = raConfigNode.getNodeValue();
            connectorConnPoolResource.setAttribute(RESOURCE_ADAPTER_CONFIG_NAME,raConfig);
        }
        if(conDefNode != null){
            String conDef = conDefNode.getNodeValue();
            connectorConnPoolResource.setAttribute(CONN_DEF_NAME,conDef);
        }
        if(steadyPoolSizeNode != null){
            String steadyPoolSize = steadyPoolSizeNode.getNodeValue();
            connectorConnPoolResource.setAttribute(CONN_STEADY_POOL_SIZE,steadyPoolSize);
        }
        if(maxPoolSizeNode != null){
            String maxPoolSize = maxPoolSizeNode.getNodeValue();
            connectorConnPoolResource.setAttribute(CONN_MAX_POOL_SIZE,maxPoolSize);
        }
        if(poolResizeNode != null){
            String poolResize = poolResizeNode.getNodeValue();
            connectorConnPoolResource.setAttribute(CONN_POOL_RESIZE_QUANTITY,poolResize);
        }
        if(idleTimeOutNode != null){
            String idleTimeOut = idleTimeOutNode.getNodeValue();
            connectorConnPoolResource.setAttribute(CONN_IDLE_TIME_OUT,idleTimeOut);
        }
        if(failAllConnNode != null){
            String failAllConn = failAllConnNode.getNodeValue();
            connectorConnPoolResource.setAttribute(CONN_FAIL_ALL_CONNECTIONS,
                                                   failAllConn);
        }
        if(maxWaitTimeMillisNode != null){
           String maxWaitTimeMillis = maxWaitTimeMillisNode.getNodeValue();
           connectorConnPoolResource.setAttribute(MAX_WAIT_TIME_IN_MILLIS,
                                                      maxWaitTimeMillis);
        }
        if(transactionSupportNode != null){
           String transactionSupport = transactionSupportNode.getNodeValue();
           connectorConnPoolResource.setAttribute(CONN_TRANSACTION_SUPPORT,
                                                      transactionSupport);
        }
        if(connValidationReqdNode != null){
           String connValidationReqd = connValidationReqdNode.getNodeValue();
           connectorConnPoolResource.setAttribute(IS_CONNECTION_VALIDATION_REQUIRED,
                                                      connValidationReqd);
        }
        if(validateAtmostOncePeriodNode != null){
           String validateAtmostOncePeriod = validateAtmostOncePeriodNode.getNodeValue();
           connectorConnPoolResource.setAttribute(VALIDATE_ATMOST_ONCE_PERIOD_IN_SECONDS,
                                          validateAtmostOncePeriod);
        }
        if(connLeakTimeoutNode != null){
           String connLeakTimeout = connLeakTimeoutNode.getNodeValue();
           connectorConnPoolResource.setAttribute(CONNECTION_LEAK_TIMEOUT_IN_SECONDS,
                                          connLeakTimeout);
        }
        if(connLeakReclaimNode != null){
           String connLeakReclaim = connLeakReclaimNode.getNodeValue();
           connectorConnPoolResource.setAttribute(CONNECTION_LEAK_RECLAIM,
                                                      connLeakReclaim);
        }
        if(connCreationRetryAttemptsNode != null){
           String connCreationRetryAttempts = connCreationRetryAttemptsNode.getNodeValue();
           connectorConnPoolResource.setAttribute(CONNECTION_CREATION_RETRY_ATTEMPTS,
                                                      connCreationRetryAttempts);
        }
            if(connCreationRetryIntervalNode != null){
               String connCreationRetryInterval = connCreationRetryIntervalNode.getNodeValue();
           connectorConnPoolResource.setAttribute(CONNECTION_CREATION_RETRY_INTERVAL_IN_SECONDS,
                                          connCreationRetryInterval);
        }
        if(lazyConnEnlistmentNode != null){
               String lazyConnEnlistment = lazyConnEnlistmentNode.getNodeValue();
           connectorConnPoolResource.setAttribute(LAZY_CONNECTION_ENLISTMENT,
                                                      lazyConnEnlistment);
        }
        if(lazyConnAssociationNode != null){
               String lazyConnAssociation = lazyConnAssociationNode.getNodeValue();
           connectorConnPoolResource.setAttribute(LAZY_CONNECTION_ASSOCIATION,
                                                      lazyConnAssociation);
        }
            if(associateWithThreadNode != null){
               String associateWithThread = associateWithThreadNode.getNodeValue();
           connectorConnPoolResource.setAttribute(ASSOCIATE_WITH_THREAD,
                                                      associateWithThread);
        }
        if(matchConnectionsNode != null){
           String matchConnections = matchConnectionsNode.getNodeValue();
           connectorConnPoolResource.setAttribute(MATCH_CONNECTIONS,
                                                      matchConnections);
        }
        if(maxConnUsageCountNode != null){
           String maxConnUsageCount = maxConnUsageCountNode.getNodeValue();
           connectorConnPoolResource.setAttribute(MAX_CONNECTION_USAGE_COUNT,
                                                      maxConnUsageCount);
        }
        if(poolingNode != null){
           String pooling = poolingNode.getNodeValue();
           connectorConnPoolResource.setAttribute(POOLING,pooling);
        }
        if(pingNode != null){
           String ping = pingNode.getNodeValue();
           connectorConnPoolResource.setAttribute(PING,ping);
        }

        NodeList children = nextKid.getChildNodes();
        //get description
        generatePropertyElement(connectorConnPoolResource, children);
        
        vResources.add(connectorConnPoolResource);
        // with the given poolname ..create security-map
        if (children != null){
            for (int i=0; i<children.getLength(); i++) {
                if((children.item(i).getNodeName().equals(SECURITY_MAP)))
                    generateSecurityMap(poolName,children.item(i), scope);
                    
            }
        }
        //debug strings
        printResourceElements(connectorConnPoolResource);
    }

    private void generateWorkSecurityMap(Node node, String scope) throws Exception {

        //ignore "scope" as work-security-map is not a bindable resource
        
        NamedNodeMap attributes = node.getAttributes();
        if(attributes == null){
            return;
        }

        Node nameNode = attributes.getNamedItem(WORK_SECURITY_MAP_NAME);

        Resource workSecurityMapResource = new Resource(Resource.CONNECTOR_WORK_SECURITY_MAP);
        if(nameNode != null){
            String name = nameNode.getNodeValue();
            workSecurityMapResource.setAttribute(WORK_SECURITY_MAP_NAME, name);
        }

        Node raNameNode = attributes.getNamedItem(WORK_SECURITY_MAP_RA_NAME);
        if(raNameNode != null) {
            workSecurityMapResource.setAttribute(WORK_SECURITY_MAP_RA_NAME, raNameNode.getNodeValue());
        }

        NodeList children = node.getChildNodes();
        if(children != null){
            for(int i=0; i<children.getLength();i++){
                Node child = children.item(i);
                String nodeName = child.getNodeName();
                if(nodeName.equals(WORK_SECURITY_MAP_GROUP_MAP)){
                    Properties groupMaps = new Properties();
                    NamedNodeMap childAttributes = child.getAttributes();
                    if(childAttributes != null){
                        Node eisGroup = childAttributes.getNamedItem(WORK_SECURITY_MAP_EIS_GROUP);
                        Node mappedGroup = childAttributes.getNamedItem(WORK_SECURITY_MAP_MAPPED_GROUP);
                        if(eisGroup != null && mappedGroup != null){
                            String eisGroupValue = eisGroup.getNodeValue();
                            String serverGroupValue = mappedGroup.getNodeValue();
                            if(eisGroupValue != null && serverGroupValue != null){
                                groupMaps.put(eisGroupValue, serverGroupValue);
                            }
                        }
                        workSecurityMapResource.setAttribute(WORK_SECURITY_MAP_GROUP_MAP, groupMaps);
                    }
                }else if(nodeName.equals(WORK_SECURITY_MAP_PRINCIPAL_MAP)){
                    Properties principalMaps = new Properties();
                    NamedNodeMap childAttributes = child.getAttributes();
                    if(childAttributes != null){
                        Node eisPrincipal = childAttributes.getNamedItem(WORK_SECURITY_MAP_EIS_PRINCIPAL);
                        Node mappedPrincipal = childAttributes.getNamedItem(WORK_SECURITY_MAP_MAPPED_PRINCIPAL);
                        if(eisPrincipal != null && mappedPrincipal != null){
                            String eisPrincipalValue = eisPrincipal.getNodeValue();
                            String serverPrincipalValue = mappedPrincipal.getNodeValue();
                            if(eisPrincipalValue != null && serverPrincipalValue != null){
                                principalMaps.put(eisPrincipalValue, serverPrincipalValue);
                            }
                        }
                        workSecurityMapResource.setAttribute(WORK_SECURITY_MAP_PRINCIPAL_MAP, principalMaps);
                    }
                }
            }
        }
        vResources.add(workSecurityMapResource);

        //debug strings
        printResourceElements(workSecurityMapResource);
    }
    private void generateSecurityMap(String poolName,Node mapNode, String scope) throws Exception{

        //scope is not needed for security map.
        
        NamedNodeMap attributes = mapNode.getAttributes();
        if (attributes == null)
            return ;
        Node nameNode 
            = attributes.getNamedItem(SECURITY_MAP_NAME);
               
              
        Resource map = new Resource(Resource.CONNECTOR_SECURITY_MAP);
        if(nameNode != null){
            String name = nameNode.getNodeValue();
            map.setAttribute(SECURITY_MAP_NAME, name);
        }
        if(poolName != null)
           map.setAttribute(POOL_NAME,poolName);
        
        StringBuffer principal = new StringBuffer();
        StringBuffer usergroup = new StringBuffer();
        
        NodeList children = mapNode.getChildNodes();
        
        if(children != null){
            for (int i=0; i<children.getLength(); i++){
                Node gChild = children.item(i);
                String strNodeName = gChild.getNodeName();
                if(strNodeName.equals(PRINCIPAL)){
                    String p = (gChild.getFirstChild()).getNodeValue();
                    principal.append(p).append(",");
                }
                if(strNodeName.equals(USERGROUP)){
                    String u = (gChild.getFirstChild()).getNodeValue();
                    usergroup.append(u).append(",");
                }
                if((strNodeName.equals(BACKEND_PRINCIPAL))){
                    NamedNodeMap attributes1 = (children.item(i)).getAttributes();    
                    if(attributes1 != null){
                        Node userNode = attributes1.getNamedItem(USER_NAME);
                        if(userNode != null){
                            String userName =userNode.getNodeValue();
                            map.setAttribute(USER_NAME,userName);
                        }
                        Node passwordNode = attributes1.getNamedItem(PASSWORD);
                        if(passwordNode != null){
                            String pwd = passwordNode.getNodeValue();
                            map.setAttribute(PASSWORD,pwd);
                        }
                    }
                }
            }
        }
            map.setAttribute(PRINCIPAL,convertToStringArray(principal.toString()));
            map.setAttribute("user_group",convertToStringArray(usergroup.toString()));
       vResources.add(map);
    }//end of generateSecurityMap....     
   
    
    /**
     * Generate the Resource Adapter Config
     *
     */
    private void generateResourceAdapterConfig(Node nextKid, String scope) throws Exception
    {
        //ignore "scope" as resource-adapter-config is not a bindable resource
        
        NamedNodeMap attributes = nextKid.getAttributes();
        if (attributes == null)
            return;
        
        Resource resAdapterConfigResource = new Resource(Resource.RESOURCE_ADAPTER_CONFIG);
        Node resAdapConfigNode = attributes.getNamedItem(RES_ADAPTER_CONFIG);
        if(resAdapConfigNode != null){
            String resAdapConfig = resAdapConfigNode.getNodeValue();
            resAdapterConfigResource.setAttribute(RES_ADAPTER_CONFIG,resAdapConfig);
        }
        Node poolIdNode = attributes.getNamedItem(THREAD_POOL_IDS);
        if(poolIdNode != null){
            String threadPoolId = poolIdNode.getNodeValue();
            resAdapterConfigResource.setAttribute(THREAD_POOL_IDS,threadPoolId);
        }
        Node resAdapNameNode = attributes.getNamedItem(RES_ADAPTER_NAME);
        if(resAdapNameNode != null){
            String resAdapName = resAdapNameNode.getNodeValue();
            resAdapterConfigResource.setAttribute(RES_ADAPTER_NAME,resAdapName);
        }
        
        NodeList children = nextKid.getChildNodes();
        generatePropertyElement(resAdapterConfigResource, children);
        vResources.add(resAdapterConfigResource);
        
        //debug strings
        printResourceElements(resAdapterConfigResource);
    }
    
    /**
     * Returns an Iterator of <code>Resource</code>objects in the order as defined
     * in the resources XML configuration file. Maintained for backward compat 
     * purposes only. 
     */
    public Iterator<Resource> getResources() {
        return vResources.iterator();
    }
    
    public List<Resource> getResourcesList() {
        return vResources;
    }
    
    /**
     * Returns an List of <code>Resource</code>objects that needs to be 
     * created prior to module deployment. This includes all non-Connector 
     * resources and resource-adapter-config
     * @param resources List of resources, from which the non connector
     * resources need to be obtained.
     * @param isResourceCreation indicates if this determination needs to be
     * done during the <code>PreResCreationPhase</code>. In the
     * <code>PreResCreationPhase</code>, RA config is added to the 
     * non connector list, so that the RA config is created prior to the
     * RA deployment. For all other purpose, this flag needs to be set to false. 
     */
    public static List  getNonConnectorResourcesList(List<Resource> resources, 
                                                 boolean isResourceCreation, boolean ignoreDuplicates) {
        return getResourcesOfType(resources, NONCONNECTOR, isResourceCreation, ignoreDuplicates);
    }

    /**
     *  Returns an Iterator of <code>Resource</code> objects that correspond to 
     *  connector resources that needs to be created post module deployment. They 
     *  are arranged in the order in which the resources needs to be created
     * @param resources List of resources, from which the non connector
     * resources need to be obtained.
     * @param isResourceCreation indicates if this determination needs to be
     * done during the <code>PreResCreationPhase</code>. In the
     * <code>PreResCreationPhase</code>, RA config is added to the 
     * non connector list, so that the RA config is created prior to the
     * RA deployment. For all other purpose, this flag needs to be set to false. 
     */
    
    public static List getConnectorResourcesList(List<Resource> resources, boolean isResourceCreation,
                                                 boolean ignoreDuplicates) {
        return getResourcesOfType(resources, CONNECTOR, isResourceCreation, ignoreDuplicates);
    }
    
    /**
     * Print(Debug) the resource
     */
    private void printResourceElements(Resource resource)
    {
        HashMap attrList = resource.getAttributes();
        
        Iterator attrIter = attrList.keySet().iterator();
        while (attrIter.hasNext())
        {
            String attrName = (String)attrIter.next();
            Logger.getLogger(ResourcesXMLParser.class.getName()).log(
                        Level.FINE, "general.print_attr_name", attrName);
        }
    }
    
    // Helper Method to convert a String type to a String[]
     private String[] convertToStringArray(Object sOptions){
        StringTokenizer optionTokenizer   = new StringTokenizer((String)sOptions,",");
        int             size            = optionTokenizer.countTokens();
        String []       sOptionsList = new String[size];
        for (int ii=0; ii<size; ii++){
            sOptionsList[ii] = optionTokenizer.nextToken();
        } 
        return sOptionsList;
     }
    
    
      final class AddResourcesErrorHandler implements ErrorHandler {
          public void error(SAXParseException e) throws org.xml.sax.SAXException{
           throw e ;
        }
          public void fatalError(SAXParseException e) throws org.xml.sax.SAXException{
          throw e ;
        }
          public void warning(SAXParseException e) throws org.xml.sax.SAXException{
          throw e ;
        }
    }
      
      
    public InputSource resolveEntity(String publicId,String systemId) 
        throws SAXException {
        InputSource is = null;
        try {
             String dtd = System.getProperty(SystemPropertyConstants.INSTALL_ROOT_PROPERTY) +
                          File.separator + "lib" + File.separator + "dtds" + File.separator +
                          "glassfish-resources_1_5.dtd";
            is = new InputSource(new java.io.FileInputStream(dtd));
        } catch(Exception e) {
            throw new SAXException("cannot resolve dtd", e);
        }
        return is;
    }

    class MyLexicalHandler implements LexicalHandler{
        public void startDTD(String name, String publicId, String systemId) throws SAXException {
            isDoctypePresent = true;
        }
        
        public void endDTD() throws SAXException {
        }
        
        public void startEntity(String name) throws SAXException {
        }
        
        public void endEntity(String name) throws SAXException {
        }
        
        public void startCDATA() throws SAXException {
        }
        
        public void endCDATA() throws SAXException {
        }
        
        public void comment(char[] ch, int start, int length) throws SAXException {
        }
        
    }

}

