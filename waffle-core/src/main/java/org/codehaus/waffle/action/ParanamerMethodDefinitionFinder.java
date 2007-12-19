/*****************************************************************************
 * Copyright (C) 2005,2006 Michael Ward                                      *
 * All rights reserved.                                                      *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by: Michael Ward                                            *
 *****************************************************************************/
package org.codehaus.waffle.action;

import com.thoughtworks.paranamer.CachingParanamer;
import com.thoughtworks.paranamer.ParameterNamesNotFoundException;
import com.thoughtworks.paranamer.Paranamer;
import org.codehaus.waffle.bind.StringTransmuter;
import org.codehaus.waffle.monitor.ActionMonitor;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Pananamer-based method definition finder, which can be used in alternative to
 * other definition finders, eg {@link AnnotatedMethodDefinitionFinder}.
 * <p/>
 * <b>Note</b>: Pragmatic method calls will always take precedence.
 * 
 * @author Paul Hammant 
 * @see AnnotatedMethodDefinitionFinder
 */
public class ParanamerMethodDefinitionFinder extends AbstractOgnlMethodDefinitionFinder {
    private final CachingParanamer paranamer = new CachingParanamer();
   
    public ParanamerMethodDefinitionFinder(ServletContext servletContext,
                                           ArgumentResolver argumentResolver,
                                           MethodNameResolver methodNameResolver,
                                           StringTransmuter stringTransmuter, 
                                           ActionMonitor actionMonitor) {
        super(servletContext, argumentResolver, methodNameResolver, stringTransmuter, actionMonitor);
    }

    protected List<Object> getArguments(Method method, HttpServletRequest request) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        String[] parameterNames = null;
        
        try {
            parameterNames = paranamer.lookupParameterNames(method);
        } catch ( ParameterNamesNotFoundException e ){
            Class<?> declaringClass = method.getDeclaringClass();
            int rc = paranamer.areParameterNamesAvailable(declaringClass, method.getName());
            if (rc == Paranamer.NO_PARAMETER_NAMES_LIST) {
                paranamer.switchtoAsm();
                rc = paranamer.areParameterNamesAvailable(declaringClass, method.getName());
                parameterNames = paranamer.lookupParameterNames(method);
            }
            if (rc == Paranamer.NO_PARAMETER_NAMES_LIST ) {
                throw new MatchingActionMethodException("No parameter names list found by paranamer "+paranamer);
            } else if (rc == Paranamer.NO_PARAMETER_NAMES_FOR_CLASS ) {
                throw new MatchingActionMethodException("No parameter names found for class '" + declaringClass.getName() + "' by paranamer "+paranamer);
            } else if (rc == Paranamer.NO_PARAMETER_NAMES_FOR_CLASS_AND_MEMBER) {
                throw new MatchingActionMethodException("No parameter names found for class '" + declaringClass.getName() + "' and method '" + method.getName()+ "' by paranamer "+paranamer);
   //         } else if (rc == Paranamer.PARAMETER_NAMES_FOUND ){
   //             throw new MatchingMethodException("Invalid parameter names list for paranamer "+paranamer);
            }
        }
        List<String> arguments = new ArrayList<String>(parameterNames.length);

        // these should always be of the same length
        assert parameterTypes.length == parameterNames.length;

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];

            if (HttpServletRequest.class.isAssignableFrom(parameterType)
                    || HttpServletResponse.class.isAssignableFrom(parameterType)
                    || HttpSession.class.isAssignableFrom(parameterType)
                    || ServletContext.class.isAssignableFrom(parameterType)) {
                // do nothing
            } else {
                arguments.add(formatArgument(parameterNames[i]));
            }
        }

        return resolveArguments(request, arguments.iterator());
    }

}
