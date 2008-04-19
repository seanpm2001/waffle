/*****************************************************************************
 * Copyright (c) 2005-2008 Michael Ward                                      *
 * All rights reserved.                                                      *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by: Michael Ward                                            *
 *****************************************************************************/
package org.codehaus.waffle.servlet;

import static java.util.Arrays.asList;
import static org.codehaus.waffle.Constants.ERRORS_VIEW_KEY;
import static org.codehaus.waffle.Constants.VIEW_PREFIX_KEY;
import static org.codehaus.waffle.Constants.VIEW_SUFFIX_KEY;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.waffle.ComponentRegistry;
import org.codehaus.waffle.WaffleException;
import org.codehaus.waffle.action.ActionMethodExecutor;
import org.codehaus.waffle.action.ActionMethodInvocationException;
import org.codehaus.waffle.action.ActionMethodResponse;
import org.codehaus.waffle.action.ActionMethodResponseHandler;
import org.codehaus.waffle.action.MethodDefinition;
import org.codehaus.waffle.action.annotation.PRG;
import org.codehaus.waffle.bind.DataBinder;
import org.codehaus.waffle.bind.RequestAttributeBinder;
import org.codehaus.waffle.context.ContextContainer;
import org.codehaus.waffle.context.RequestLevelContainer;
import org.codehaus.waffle.controller.ControllerDefinition;
import org.codehaus.waffle.controller.ControllerDefinitionFactory;
import org.codehaus.waffle.monitor.ServletMonitor;
import org.codehaus.waffle.validation.ErrorsContext;
import org.codehaus.waffle.validation.GlobalErrorMessage;
import org.codehaus.waffle.validation.Validator;
import org.codehaus.waffle.view.RedirectView;
import org.codehaus.waffle.view.View;

/**
 * Waffle's FrontController for handling user requests.
 *
 * @author Michael Ward
 * @author Mauro Talevi
 */
@SuppressWarnings("serial")
public class WaffleServlet extends HttpServlet {

    private static final String DEFAULT_VIEW_PREFIX = "/";
    private static final String DEFAULT_VIEW_SUFFIX = ".jspx";
    private static final String DEFAULT_ERRORS_VIEW = "errors";
    private static final String EMPTY = "";
    private static final String POST = "POST";
    private String viewPrefix;
    private String viewSuffix;
    private String errorsView;
    private ActionMethodExecutor actionMethodExecutor;
    private ActionMethodResponseHandler actionMethodResponseHandler;
    private ControllerDefinitionFactory controllerDefinitionFactory;
    private DataBinder dataBinder;
    private RequestAttributeBinder requestAttributeBinder;
    private Validator validator;
    private ServletMonitor servletMonitor;
    private boolean componentsRetrieved = false;

    /**
     * Default constructor used by servlet container
     */
    public WaffleServlet() {
        // initialisation will be performed in init() method
    }

    /**
     * Constructor required by builder and useful for testing
     *
     * @param actionMethodExecutor
     * @param actionMethodResponseHandler
     * @param servletMonitor
     * @param dataBinder
     * @param requestAttributeBinder
     * @param controllerDefinitionFactory
     * @param validator
     */
    public WaffleServlet(ActionMethodExecutor actionMethodExecutor,
                         ActionMethodResponseHandler actionMethodResponseHandler,
                         ServletMonitor servletMonitor,
                         DataBinder dataBinder,
                         RequestAttributeBinder requestAttributeBinder,
                         ControllerDefinitionFactory controllerDefinitionFactory,
                         Validator validator) {
        this.actionMethodExecutor = actionMethodExecutor;
        this.actionMethodResponseHandler = actionMethodResponseHandler;
        this.servletMonitor = servletMonitor;
        this.dataBinder = dataBinder;
        this.requestAttributeBinder = requestAttributeBinder;
        this.controllerDefinitionFactory = controllerDefinitionFactory;
        this.validator = validator;
        componentsRetrieved = true;
    }

    public void init() throws ServletException {
        viewPrefix = initParam(VIEW_PREFIX_KEY, DEFAULT_VIEW_PREFIX);
        viewSuffix = initParam(VIEW_SUFFIX_KEY, DEFAULT_VIEW_SUFFIX);
        errorsView = initParam(ERRORS_VIEW_KEY, DEFAULT_ERRORS_VIEW);

        if (!componentsRetrieved) {
            // Retrieve instance components from the ComponentRegistry
            ComponentRegistry registry = getComponentRegistry();
            actionMethodExecutor = registry.getActionMethodExecutor();
            actionMethodResponseHandler = registry.getActionMethodResponseHandler();
            controllerDefinitionFactory = registry.getControllerDefinitionFactory();
            dataBinder = registry.getDataBinder();
            requestAttributeBinder = registry.getRequestAttributeBinder();
            validator = registry.getValidator();
            servletMonitor = registry.getServletMonitor();
        }
        servletMonitor.servletInitialized(this);
    }

    private String initParam(String key, String defaultValue) {
        String value = getInitParameter(key);
        if (value == null || value.equals(EMPTY)) {
            value = defaultValue; // default
        }
        return value;
    }

    private ComponentRegistry getComponentRegistry() {
        return ServletContextHelper.getComponentRegistry(getServletContext());
    }

    /**
     * Responsible for servicing the requests from the users.
     *
     * @param request  the HttpServletResponse
     * @param response the HttpServletResponse
     * @throws ServletException
     * @throws IOException
     */
    protected void service(HttpServletRequest request,
                           HttpServletResponse response) throws ServletException, IOException {
        servletMonitor.servletServiceRequested(parametersOf(request));
        ContextContainer requestContainer = RequestLevelContainer.get();
        ErrorsContext errorsContext = requestContainer.getComponentInstanceOfType(ErrorsContext.class);

        ActionMethodResponse actionMethodResponse = new ActionMethodResponse();
        View view = null;
        try {
            ControllerDefinition controllerDefinition = controllerDefinitionFactory.getControllerDefinition(request,
            response);
            dataBinder.bind(request, response, errorsContext, controllerDefinition.getController());
            validator.validate(controllerDefinition, errorsContext);
            try {

                if (errorsContext.hasErrorMessages() || noMethodDefinition(controllerDefinition)) {
                    view = buildView(controllerDefinition);
                } else {
                    actionMethodExecutor.execute(actionMethodResponse, controllerDefinition);

                    if (errorsContext.hasErrorMessages()) {
                        view = buildView(controllerDefinition);
                    } else if (actionMethodResponse.getReturnValue() == null) {
                        // Null or VOID indicate a Waffle convention (return to referring page)
                        // unless PRG is disabled 
                        if (request.getMethod().equalsIgnoreCase(POST)) {
                            if (usePRG(controllerDefinition.getMethodDefinition())) {
                                // PRG (Post/Redirect/Get): see http://en.wikipedia.org/wiki/Post/Redirect/Get
                                view = buildRedirectingView(request, controllerDefinition);
                            } else {
                                // PRG is disabled
                                view = buildView(controllerDefinition);
                            }
                        } else { // was a GET
                            view = buildView(controllerDefinition);
                        }
                    }
                }

            } catch (ActionMethodInvocationException e) {
                servletMonitor.actionMethodInvocationFailed(e);
                errorsContext.addErrorMessage(new GlobalErrorMessage("Action method invocation failed for controller "
                        + controllerDefinition.getName() + ", :" + e.getMessage(), e));
                view = buildActionMethodFailureView(controllerDefinition);
            }
            requestAttributeBinder.bind(request, controllerDefinition.getController());
        } catch (WaffleException e) {      
            servletMonitor.servletServiceFailed(e);
            errorsContext.addErrorMessage(new GlobalErrorMessage(e.getMessage(), e));
            view = buildErrorsView(request);
        }
        
        if (view != null) {
            actionMethodResponse.setReturnValue(view);
        }
        actionMethodResponseHandler.handle(request, response, actionMethodResponse);
    }

    private boolean noMethodDefinition(ControllerDefinition controllerDefinition) {
        return controllerDefinition.getMethodDefinition() == null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> parametersOf(HttpServletRequest request) {
        Map<String, List<String>> parameters = new HashMap<String, List<String>>();
        for ( Enumeration<String> e = request.getParameterNames(); e.hasMoreElements(); ){
            String name = e.nextElement();
            parameters.put(name, asList(request.getParameterValues(name)));
        }
        return parameters;
    }

    /**
     * Determine if PRG paradigm is used from the @PRG annotation of the action method
     * 
     * @param methodDefinition the MethodDefinition
     * @return A boolean flag, defaults to <code>true</code> if no annotation found
     */
    private boolean usePRG(MethodDefinition methodDefinition) {
        Method method = methodDefinition.getMethod();
        // look for PRG annotation
        PRG prg = method.getAnnotation(PRG.class);
        if ( prg != null ){
            return prg.value();
        }
        // else default to true
        return true;
    }

    /**
     * Build a view back to the referring page, using the Controller's name as the View name.
     * 
     * @param controllerDefinition the ControllerDefinition
     * @return The View
     */
    protected View buildView(ControllerDefinition controllerDefinition) {
        String controllerValue = viewPrefix + controllerDefinition.getName() + viewSuffix;
        return new View(controllerValue, controllerDefinition.getController());
    }
    
    /**
     * Build redirecting view, used by PRG paradigm.
     * 
     * @param request the request
     * @param controllerDefinition the ControllerDefinition
     * @return The RedirectView
     */
    protected View buildRedirectingView(HttpServletRequest request, ControllerDefinition controllerDefinition) {
        String url = request.getRequestURL().toString();
        return new RedirectView(url, controllerDefinition.getController());
    }

    /**
     * Builds the view for action method failures, by default the referring view. 
     * The user can extend and override behaviour, eg to throw a ServletException.
     * 
     * @param controllerDefinition the ControllerDefinition
     * @return The referring View
     * @throws ServletException if required
     */
    protected View buildActionMethodFailureView(ControllerDefinition controllerDefinition) throws ServletException {
        return buildView(controllerDefinition);
    }

    /**
     * Builds the errors view, for cases in which the context container or the controller are not found.
     * The user can extend and override behaviour, eg to throw a ServletException.
     * 
     * @param request the HttpServletRequest
     * @return The referring View
     * @throws ServletException if required
     */
    protected View buildErrorsView(HttpServletRequest request) throws ServletException {
        return buildView(new ControllerDefinition(errorsView, null, null));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[WaffleServlet ");
        sb.append(" viewPrefix=");
        sb.append(viewPrefix);
        sb.append(", viewSuffix=");
        sb.append(viewSuffix);
        sb.append(", errorsView=");
        sb.append(errorsView);
        sb.append(", actionMethodExecutor=");
        sb.append(actionMethodExecutor);
        sb.append(", actionMethodResponseHandler=");
        sb.append(actionMethodResponseHandler);
        sb.append(", controllerDefinitionFactory=");
        sb.append(controllerDefinitionFactory);
        sb.append(", dataBinder=");
        sb.append(dataBinder);
        sb.append(", requestAttributeBinder=");
        sb.append(requestAttributeBinder);
        sb.append(", validator=");
        sb.append(validator);
        sb.append("]");
        return sb.toString();
    }

}
