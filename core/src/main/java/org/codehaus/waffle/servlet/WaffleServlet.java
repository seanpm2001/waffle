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
package org.codehaus.waffle.servlet;

import org.codehaus.waffle.ComponentRegistry;
import static org.codehaus.waffle.Constants.ERRORS_KEY;
import static org.codehaus.waffle.Constants.VIEW_PREFIX_KEY;
import static org.codehaus.waffle.Constants.VIEW_SUFFIX_KEY;
import org.codehaus.waffle.action.ActionMethodExecutor;
import org.codehaus.waffle.action.ActionMethodInvocationException;
import org.codehaus.waffle.action.ActionMethodResponse;
import org.codehaus.waffle.action.ActionMethodResponseHandler;
import org.codehaus.waffle.action.MethodDefinition;
import org.codehaus.waffle.bind.DataBinder;
import org.codehaus.waffle.bind.RequestAttributeBinder;
import org.codehaus.waffle.controller.ControllerDefinition;
import org.codehaus.waffle.controller.ControllerDefinitionFactory;
import org.codehaus.waffle.validation.DefaultErrorsContext;
import org.codehaus.waffle.validation.ErrorsContext;
import org.codehaus.waffle.validation.Validator;
import org.codehaus.waffle.view.RedirectView;
import org.codehaus.waffle.view.View;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Waffle's FrontController for handling user requests.
 *
 * @author Michael Ward
 */
public class WaffleServlet extends HttpServlet {
    private static final String DEFAULT_VIEW_SUFFIX = ".jspx";
    private static final String DEFAULT_VIEW_PREFIX = "/";
    private static final String EMPTY = "";
    private ControllerDefinitionFactory controllerDefinitionFactory;
    private DataBinder dataBinder;
    private ActionMethodExecutor actionMethodExecutor;
    private ActionMethodResponseHandler actionMethodResponseHandler;
    private Validator validator;
    private RequestAttributeBinder requestAttributeBinder;
    private String viewPrefix;
    private String viewSuffix;
    private boolean depsDone = false;

    public WaffleServlet() {
    }

    /**
     * Needed for builder ... and helpful for testing
     */
    public WaffleServlet(ControllerDefinitionFactory controllerDefinitionFactory,
                         DataBinder dataBinder,
                         ActionMethodExecutor actionMethodExecutor,
                         ActionMethodResponseHandler actionMethodResponseHandler,
                         Validator validator,
                         RequestAttributeBinder requestAttributeBinder) {
        this.controllerDefinitionFactory = controllerDefinitionFactory;
        this.dataBinder = dataBinder;
        this.actionMethodExecutor = actionMethodExecutor;
        this.actionMethodResponseHandler = actionMethodResponseHandler;
        this.validator = validator;
        this.requestAttributeBinder = requestAttributeBinder;
        depsDone = true;
    }

    public void init() throws ServletException {
        viewPrefix = getInitParameter(VIEW_PREFIX_KEY);
        if (viewPrefix == null || viewPrefix.equals(EMPTY)) {
            viewPrefix = DEFAULT_VIEW_PREFIX; // default
        }

        viewSuffix = getInitParameter(VIEW_SUFFIX_KEY);
        if (viewSuffix == null || viewSuffix.equals(EMPTY)) {
            viewSuffix = DEFAULT_VIEW_SUFFIX; // default
        }

        if (!depsDone) {
            // Obtain required components from the Component Registry
            ComponentRegistry componentRegistry = ServletContextHelper
                    .getComponentRegistry(getServletContext());
            controllerDefinitionFactory = componentRegistry.getControllerDefinitionFactory();
            dataBinder = componentRegistry.getDataBinder();
            actionMethodExecutor = componentRegistry.getActionMethodExecutor();
            actionMethodResponseHandler = componentRegistry.getActionMethodResponseHandler();
            validator = componentRegistry.getValidator();
            requestAttributeBinder = componentRegistry.getRequestAttributeBinder();
        }
    }

    /**
     * Obtain the controller the user is requesting.
     */
    protected ControllerDefinition getControllerDefinition(HttpServletRequest request,
                                                           HttpServletResponse response) throws ServletException {
        ControllerDefinition controllerDefinition = controllerDefinitionFactory.getControllerDefinition(request, response);
        if (controllerDefinition.getController() == null) {
            throw new ServletException("Unable to locate the Waffle Controller: " + request.getServletPath());
        }

        return controllerDefinition;
    }

    /**
     * Responsible for servicing the requests from the users.
     *
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    protected void service(HttpServletRequest request,
                           HttpServletResponse response) throws ServletException, IOException {
        ErrorsContext errorsContext = new DefaultErrorsContext();
        request.setAttribute(ERRORS_KEY, errorsContext);

        ControllerDefinition controllerDefinition = getControllerDefinition(request, response);
        dataBinder.bind(request, response, errorsContext, controllerDefinition.getController());
        validator.validate(controllerDefinition, errorsContext);

        try {
            ActionMethodResponse actionMethodResponse = new ActionMethodResponse();
            MethodDefinition methodDefinition = controllerDefinition.getMethodDefinition();
            View view = null;

            if (errorsContext.hasErrorMessages() || methodDefinition == null) {
                view = buildViewToReferrer(controllerDefinition);
            } else {
                actionMethodExecutor.execute(actionMethodResponse, controllerDefinition);

                if (errorsContext.hasErrorMessages()) {
                    view = buildViewToReferrer(controllerDefinition);
                } else if (actionMethodResponse.getReturnValue() == null) {
                    // Null or VOID indicate a Waffle convention (return to referring page)
                    if (request.getMethod().equalsIgnoreCase("POST")) {
                        // PRG (Post/Redirect/Get): see http://en.wikipedia.org/wiki/Post/Redirect/Get
                        String url = request.getRequestURL().toString();
                        view = new RedirectView(url, controllerDefinition.getController());
                    } else { // was a GET
                        view = buildViewToReferrer(controllerDefinition);
                    }
                }
            }

            if (view != null) {
                actionMethodResponse.setReturnValue(view);
            }

            requestAttributeBinder.bind(request, controllerDefinition.getController());
            actionMethodResponseHandler.handle(request, response, actionMethodResponse);
        } catch (ActionMethodInvocationException e) {
            log("ERROR: " + e.getMessage());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Build a view back to the referring page (use the Controller's name as the View name).
     */
    protected View buildViewToReferrer(ControllerDefinition controllerDefinition) {
        String controllerValue = viewPrefix + controllerDefinition.getName() + viewSuffix;
        return new View(controllerValue, controllerDefinition.getController());
    }

}
