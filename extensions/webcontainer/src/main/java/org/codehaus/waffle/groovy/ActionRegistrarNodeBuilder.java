/*****************************************************************************
 * Copyright (C) NanoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 *****************************************************************************/
package org.codehaus.waffle.groovy;

import groovy.util.NodeBuilder;

import java.util.Map;

import org.codehaus.waffle.registrar.Registrar;
import org.nanocontainer.webcontainer.PicoContext;
import org.picocontainer.PicoContainer;

public class ActionRegistrarNodeBuilder extends NodeBuilder {

    private static final String EMPTY_NODE = "";
    private final PicoContainer parentContainer;
    private Object registrarClass;
    private final PicoContext context;

    public ActionRegistrarNodeBuilder(PicoContainer parentContainer, Object registrarClass, PicoContext context) {
        this.parentContainer = parentContainer;
        this.registrarClass = registrarClass;
        this.context = context;
        context.addInitParam(Registrar.class.getName(), registrarClass instanceof Class ? ((Class) registrarClass)
                .getName() : (String) registrarClass);
    }

    protected Object createNode(Object current, Map attributes) {
        return EMPTY_NODE;
    }
}
