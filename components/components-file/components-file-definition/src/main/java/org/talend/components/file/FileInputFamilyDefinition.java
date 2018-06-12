// ============================================================================
//
// Copyright (C) 2006-2017 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.components.file;

import org.talend.components.api.AbstractComponentFamilyDefinition;
import org.talend.components.api.ComponentInstaller;
import org.talend.components.api.Constants;
import org.talend.components.file.tfileinput.FileInputDefinition;

import aQute.bnd.annotation.component.Component;

import com.google.auto.service.AutoService;

/**
 * Install all of the definitions provided for the FileInput family of components.
 */
@AutoService(ComponentInstaller.class)
@Component(name = Constants.COMPONENT_INSTALLER_PREFIX + FileInputFamilyDefinition.NAME, provide = ComponentInstaller.class)
public class FileInputFamilyDefinition extends AbstractComponentFamilyDefinition implements ComponentInstaller {

    public static final String NAME = "FileInput";

    public FileInputFamilyDefinition() {
        super(NAME, new FileInputDefinition());

    }

    @Override
    public void install(ComponentFrameworkContext ctx) {
        ctx.registerComponentFamilyDefinition(this);
    }
}
