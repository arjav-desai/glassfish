/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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

package org.glassfish.admingui.devtests;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JndiTest extends BaseSeleniumTestClass {
    private static final String TRIGGER_CUSTOM_RESOURCES = "Custom resources are nonstandard resources";
    private static final String TRIGGER_NEW_CUSTOM_RESOURCE = "New Custom Resource";
    private static final String TRIGGER_EDIT_CUSTOM_RESOURCE = "Edit Custom Resource";
    private static final String TRIGGER_EDIT_EXTERNAL_RESOURCE = "Edit External Resource";
    private static final String TRIGGER_EXTERNAL_RESOURCES = "Manage external JNDI resources when";
    private static final String TRIGGER_NEW_EXTERNAL_RESOURCE = "New External Resource";

    @Test
    public void testCustomResources() {
        final String resourceName = generateRandomString();

        clickAndWait("treeForm:tree:resources:jndi:customResources:customResources_link", TRIGGER_CUSTOM_RESOURCES);
        clickAndWait("propertyForm:resourcesTable:topActionsGroup1:newButton", TRIGGER_NEW_CUSTOM_RESOURCE);

        selenium.type("form1:propertySheet:propertSectionTextField:jndiTextProp:jnditext", resourceName);
        selenium.select("form1:propertySheet:propertSectionTextField:cp:Classname", "label=java.lang.Double");
        int count = addTableRow("form1:basicTable", "form1:basicTable:topActionsGroup1:addSharedTableButton");

        selenium.type("form1:basicTable:rowGroup1:0:col2:col1St", "property"+generateRandomString());
        selenium.type("form1:basicTable:rowGroup1:0:col3:col1St", "value");
        selenium.type("form1:basicTable:rowGroup1:0:col4:col1St", "description");
        clickAndWait("form1:propertyContentPage:topButtons:newButton", TRIGGER_CUSTOM_RESOURCES);

        assertTrue(selenium.isTextPresent(resourceName));

        testDisableButton(resourceName,
                "propertyForm:resourcesTable",
                "propertyForm:resourcesTable:topActionsGroup1:button3",
                "form1:propertySheet:propertSectionTextField:statusProp:enabled",
                "form1:propertyContentPage:topButtons:cancelButton",
                TRIGGER_CUSTOM_RESOURCES,
                TRIGGER_EDIT_CUSTOM_RESOURCE);
        testEnableButton(resourceName,
                "propertyForm:resourcesTable",
                "propertyForm:resourcesTable:topActionsGroup1:button2",
                "form1:propertySheet:propertSectionTextField:statusProp:enabled",
                "form1:propertyContentPage:topButtons:cancelButton",
                TRIGGER_CUSTOM_RESOURCES,
                TRIGGER_EDIT_CUSTOM_RESOURCE);

        deleteRow("propertyForm:resourcesTable:topActionsGroup1:button1", "propertyForm:resourcesTable", resourceName);
    }

    @Test
    public void testExternalResources() {
        final String resourceName = generateRandomString();
        final String description = resourceName + " - description";

        clickAndWait("treeForm:tree:resources:jndi:externalResources:externalResources_link", TRIGGER_EXTERNAL_RESOURCES);
        clickAndWait("propertyForm:resourcesTable:topActionsGroup1:newButton", TRIGGER_NEW_EXTERNAL_RESOURCE);

        selenium.type("form1:propertySheet:propertSectionTextField:jndiTextProp:jnditext", resourceName);
        selenium.select("form1:propertySheet:propertSectionTextField:cp:Classname", "label=java.lang.Double");
        selenium.type("form1:propertySheet:propertSectionTextField:jndiLookupProp:jndiLookup", resourceName);
        selenium.type("form1:propertySheet:propertSectionTextField:descProp:desc", description);
        int count = addTableRow("form1:basicTable", "form1:basicTable:topActionsGroup1:addSharedTableButton");

        selenium.type("form1:basicTable:rowGroup1:0:col2:col1St", "property"+generateRandomString());
        selenium.type("form1:basicTable:rowGroup1:0:col3:col1St", "value");
        selenium.type("form1:basicTable:rowGroup1:0:col4:col1St", "description");
        clickAndWait("form1:propertyContentPage:topButtons:newButton", TRIGGER_EXTERNAL_RESOURCES);

        testDisableButton(resourceName,
                "propertyForm:resourcesTable",
                "propertyForm:resourcesTable:topActionsGroup1:button3",
                "form1:propertySheet:propertSectionTextField:statusProp:enabled",
                "form1:propertyContentPage:topButtons:cancelButton",
                TRIGGER_EXTERNAL_RESOURCES,
                TRIGGER_EDIT_EXTERNAL_RESOURCE);
        testEnableButton(resourceName,
                "propertyForm:resourcesTable",
                "propertyForm:resourcesTable:topActionsGroup1:button2",
                "form1:propertySheet:propertSectionTextField:statusProp:enabled",
                "form1:propertyContentPage:topButtons:cancelButton",
                TRIGGER_EXTERNAL_RESOURCES,
                TRIGGER_EDIT_EXTERNAL_RESOURCE);

//        selectTableRowByValue("propertyForm:resourcesTable", resourceName);
//        waitForButtonEnabled("propertyForm:resourcesTable:topActionsGroup1:button3");
//        selenium.click("propertyForm:resourcesTable:topActionsGroup1:button3");
//        waitForButtonDisabled("propertyForm:resourcesTable:topActionsGroup1:button3");
//
//        clickAndWait(getLinkIdByLinkText("propertyForm:resourcesTable", resourceName), TRIGGER_EDIT_EXTERNAL_RESOURCE);
//
//        assertEquals("off", selenium.getValue("form1:propertySheet:propertSectionTextField:statusProp:enabled"));
//        clickAndWait("form1:propertyContentPage:topButtons:cancelButton", TRIGGER_EXTERNAL_RESOURCES);
//
//        selectTableRowByValue("propertyForm:resourcesTable", resourceName);
//        waitForButtonEnabled("propertyForm:resourcesTable:topActionsGroup1:button2");
//        selenium.click("propertyForm:resourcesTable:topActionsGroup1:button2");
//        waitForButtonDisabled("propertyForm:resourcesTable:topActionsGroup1:button2");
//
//        clickAndWait("propertyForm:resourcesTable:rowGroup1:0:col1:link", TRIGGER_EDIT_EXTERNAL_RESOURCE);
//        assertEquals("on", selenium.getValue("form1:propertySheet:propertSectionTextField:statusProp:enabled"));
//        clickAndWait("form1:propertyContentPage:topButtons:cancelButton", TRIGGER_EXTERNAL_RESOURCES);

        deleteRow("propertyForm:resourcesTable:topActionsGroup1:button1", "propertyForm:resourcesTable", resourceName);
    }
}
