/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.isis.viewer.wicket.model.models;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.handler.resource.ResourceRequestHandler;
import org.apache.wicket.request.handler.resource.ResourceStreamRequestHandler;
import org.apache.wicket.request.http.handler.RedirectRequestHandler;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.ByteArrayResource;
import org.apache.wicket.request.resource.ContentDisposition;
import org.apache.wicket.util.resource.StringResourceStream;

import org.apache.isis.applib.RecoverableException;
import org.apache.isis.applib.Identifier;
import org.apache.isis.applib.annotation.ActionSemantics;
import org.apache.isis.applib.annotation.BookmarkPolicy;
import org.apache.isis.applib.value.Blob;
import org.apache.isis.applib.value.Clob;
import org.apache.isis.core.metamodel.adapter.ObjectAdapter;
import org.apache.isis.core.metamodel.adapter.mgr.AdapterManager.ConcurrencyChecking;
import org.apache.isis.core.metamodel.adapter.oid.OidMarshaller;
import org.apache.isis.core.metamodel.adapter.oid.RootOid;
import org.apache.isis.core.metamodel.adapter.oid.RootOidDefault;
import org.apache.isis.core.metamodel.consent.Consent;
import org.apache.isis.core.metamodel.facets.object.bookmarkable.BookmarkPolicyFacet;
import org.apache.isis.core.metamodel.facets.object.encodeable.EncodableFacet;
import org.apache.isis.core.metamodel.spec.ActionType;
import org.apache.isis.core.metamodel.spec.ObjectSpecId;
import org.apache.isis.core.metamodel.spec.ObjectSpecification;
import org.apache.isis.core.metamodel.spec.feature.ObjectAction;
import org.apache.isis.core.metamodel.spec.feature.ObjectActionParameter;
import org.apache.isis.core.runtime.system.context.IsisContext;
import org.apache.isis.viewer.wicket.model.mementos.ActionMemento;
import org.apache.isis.viewer.wicket.model.mementos.ActionParameterMemento;
import org.apache.isis.viewer.wicket.model.mementos.ObjectAdapterMemento;
import org.apache.isis.viewer.wicket.model.mementos.PageParameterNames;

/**
 * Models an action invocation, either the gathering of arguments for the
 * action's {@link Mode#PARAMETERS parameters}, or the handling of the
 * {@link Mode#RESULTS results} once invoked.
 */
public class ActionModel extends BookmarkableModel<ObjectAdapter> {
    private static final long serialVersionUID = 1L;
    
    private static final String NULL_ARG = "$nullArg$";

    /**
     * Whether we are obtaining arguments (eg in a dialog), or displaying the
     * results
     */
    private enum Mode {
        PARAMETERS, 
        RESULTS
    }


    
    //////////////////////////////////////////////////
    // Factory methods
    //////////////////////////////////////////////////

    /**
     * @param objectAdapter
     * @param action
     * @return
     */
    public static ActionModel create(ObjectAdapter objectAdapter, ObjectAction action) {
        final ObjectAdapterMemento serviceMemento = ObjectAdapterMemento.Functions.fromAdapter().apply(objectAdapter);
        final ActionMemento homePageActionMemento = ObjectAdapterMemento.Functions.fromAction().apply(action);
        final Mode mode = action.getParameterCount() > 0? Mode.PARAMETERS : Mode.RESULTS;
        return new ActionModel(serviceMemento, homePageActionMemento, mode);
    }

    public static ActionModel createForPersistent(final PageParameters pageParameters) {
        return new ActionModel(pageParameters);
    }

    /**
     * Factory method for creating {@link PageParameters}.
     * 
     * see {@link #ActionModel(PageParameters)}
     */
    public static PageParameters createPageParameters(
            final ObjectAdapter adapter, final ObjectAction objectAction, final ConcurrencyChecking concurrencyChecking) {
        
        final PageParameters pageParameters = new PageParameters();
        
        final String oidStr = concurrencyChecking == ConcurrencyChecking.CHECK?
                adapter.getOid().enString(getOidMarshaller()):
                adapter.getOid().enStringNoVersion(getOidMarshaller());
        PageParameterNames.OBJECT_OID.addStringTo(pageParameters, oidStr);
        
        final ActionType actionType = objectAction.getType();
        PageParameterNames.ACTION_TYPE.addEnumTo(pageParameters, actionType);
        
        final ObjectSpecification actionOnTypeSpec = objectAction.getOnType();
        if (actionOnTypeSpec != null) {
            PageParameterNames.ACTION_OWNING_SPEC.addStringTo(pageParameters, actionOnTypeSpec.getFullIdentifier());
        }
        
        final String actionId = determineActionId(objectAction);
        PageParameterNames.ACTION_ID.addStringTo(pageParameters, actionId);
        
        return pageParameters;
    }


    public static Entry<Integer, String> parse(final String paramContext) {
        final Pattern compile = Pattern.compile("([^=]+)=(.+)");
        final Matcher matcher = compile.matcher(paramContext);
        if (!matcher.matches()) {
            return null;
        }

        final int paramNum;
        try {
            paramNum = Integer.parseInt(matcher.group(1));
        } catch (final Exception e) {
            // ignore
            return null;
        }

        final String oidStr;
        try {
            oidStr = matcher.group(2);
        } catch (final Exception e) {
            return null;
        }

        return new Map.Entry<Integer, String>() {

            @Override
            public Integer getKey() {
                return paramNum;
            }

            @Override
            public String getValue() {
                return oidStr;
            }

            @Override
            public String setValue(final String value) {
                return null;
            }
        };
    }

    //////////////////////////////////////////////////
    // BookmarkableModel
    //////////////////////////////////////////////////

    public PageParameters getPageParameters() {
        final ObjectAdapter adapter = getTargetAdapter();
        final ObjectAction objectAction = getActionMemento().getAction();
        final PageParameters pageParameters = createPageParameters(
                adapter, objectAction, ConcurrencyChecking.NO_CHECK);

        // capture argument values
        final ObjectAdapter[] argumentsAsArray = getArgumentsAsArray();
        for(ObjectAdapter argumentAdapter: argumentsAsArray) {
            final String encodedArg = encodeArg(argumentAdapter);
            PageParameterNames.ACTION_ARGS.addStringTo(pageParameters, encodedArg);
        }

        return pageParameters;
    }

    @Override
    public String getTitle() {
        final ObjectAdapter adapter = getTargetAdapter();
        final ObjectAction objectAction = getActionMemento().getAction();
        
        final StringBuilder buf = new StringBuilder();
        final ObjectAdapter[] argumentsAsArray = getArgumentsAsArray();
        for(ObjectAdapter argumentAdapter: argumentsAsArray) {
            if(buf.length() > 0) {
                buf.append(",");
            }
            buf.append(abbreviated(titleOf(argumentAdapter), 8));
        }

        return adapter.titleString(null) + "." + objectAction.getName() + (buf.length()>0?"(" + buf.toString() + ")":"");
    }

    @Override
    public boolean hasAsRootPolicy() {
        return true;
    }

    //////////////////////////////////////////////////
    // helpers
    //////////////////////////////////////////////////

    
    private static String titleOf(ObjectAdapter argumentAdapter) {
        return argumentAdapter!=null?argumentAdapter.titleString(null):"";
    }
    
    private static String abbreviated(final String str, final int maxLength) {
        return str.length() < maxLength ? str : str.substring(0, maxLength - 3) + "...";
    }


    private static String determineActionId(final ObjectAction objectAction) {
        final Identifier identifier = objectAction.getIdentifier();
        if (identifier != null) {
            return identifier.toNameParmsIdentityString();
        }
        // fallback (used for action sets)
        return objectAction.getId();
    }

    public static Mode determineMode(final ObjectAction action) {
        return action.getParameterCount() > 0 ? Mode.PARAMETERS : Mode.RESULTS;
    }

    private final ObjectAdapterMemento targetAdapterMemento;
    private final ActionMemento actionMemento;
    private Mode actionMode;


    /**
     * Lazily populated in {@link #getArgumentModel(ActionParameterMemento)}
     */
    private Map<Integer, ScalarModel> arguments = Maps.newHashMap();
    private ActionExecutor executor;


    private ActionModel(final PageParameters pageParameters) {
        this(newObjectAdapterMementoFrom(pageParameters), newActionMementoFrom(pageParameters), actionModeFrom(pageParameters));

        setArgumentsIfPossible(pageParameters);
        setContextArgumentIfPossible(pageParameters);
    }

    private static ActionMemento newActionMementoFrom(final PageParameters pageParameters) {
        final ObjectSpecId owningSpec = ObjectSpecId.of(PageParameterNames.ACTION_OWNING_SPEC.getStringFrom(pageParameters));
        final ActionType actionType = PageParameterNames.ACTION_TYPE.getEnumFrom(pageParameters, ActionType.class);
        final String actionNameParms = PageParameterNames.ACTION_ID.getStringFrom(pageParameters);
        return new ActionMemento(owningSpec, actionType, actionNameParms);
    }

    private static Mode actionModeFrom(PageParameters pageParameters) {
        final ActionMemento actionMemento = newActionMementoFrom(pageParameters);
        if(actionMemento.getAction().getParameterCount() == 0) {
            return Mode.RESULTS;
        }
        final List<String> listFrom = PageParameterNames.ACTION_ARGS.getListFrom(pageParameters);
        return listFrom != null && !listFrom.isEmpty()? Mode.RESULTS: Mode.PARAMETERS;
    }


    private static ObjectAdapterMemento newObjectAdapterMementoFrom(final PageParameters pageParameters) {
        RootOid oid = oidFor(pageParameters);
        if(oid.isTransient()) {
            return null;
        } else {
            return ObjectAdapterMemento.createPersistent(oid);
        }
    }

    private static RootOid oidFor(final PageParameters pageParameters) {
        String oidStr = PageParameterNames.OBJECT_OID.getStringFrom(pageParameters);
        return getOidMarshaller().unmarshal(oidStr, RootOid.class);
    }


    private ActionModel(final ObjectAdapterMemento adapterMemento, final ActionMemento actionMemento, final Mode actionMode) {
        this.targetAdapterMemento = adapterMemento;
        this.actionMemento = actionMemento;
        this.actionMode = actionMode;
    }

    private void setArgumentsIfPossible(final PageParameters pageParameters) {
        List<String> args = PageParameterNames.ACTION_ARGS.getListFrom(pageParameters);

        final ObjectAction action = actionMemento.getAction();
        final List<ObjectSpecification> parameterTypes = action.getParameterTypes();

        for (int paramNum = 0; paramNum < args.size(); paramNum++) {
            String encoded = args.get(paramNum);
            setArgument(paramNum, parameterTypes.get(paramNum), encoded);
        }
    }

    public boolean hasParameters() {
        return actionMode == ActionModel.Mode.PARAMETERS;
    }

    private boolean setContextArgumentIfPossible(final PageParameters pageParameters) {
        final String paramContext = PageParameterNames.ACTION_PARAM_CONTEXT.getStringFrom(pageParameters);
        if (paramContext == null) {
            return false;
        }

        final ObjectAction action = actionMemento.getAction();
        final List<ObjectSpecification> parameterTypes = action.getParameterTypes();
        final int parameterCount = parameterTypes.size();

        final Map.Entry<Integer, String> mapEntry = parse(paramContext);

        final int paramNum = mapEntry.getKey();
        if (paramNum >= parameterCount) {
            return false;
        }

        final String encoded = mapEntry.getValue();
        setArgument(paramNum, parameterTypes.get(paramNum), encoded);

        return true;
    }

    private void setArgument(final int paramNum, final ObjectSpecification argSpec, final String encoded) {
        final ObjectAdapter argumentAdapter = decodeArg(argSpec, encoded);
        setArgument(paramNum, argumentAdapter);
    }

    private String encodeArg(ObjectAdapter adapter) {
        if(adapter == null) {
            return NULL_ARG;
        }
        
        ObjectSpecification objSpec = adapter.getSpecification();
        if(objSpec.isEncodeable()) {
            EncodableFacet encodeable = objSpec.getFacet(EncodableFacet.class);
            return encodeable.toEncodedString(adapter);
        }
        
        return adapter.getOid().enStringNoVersion(getOidMarshaller());
    }

    private ObjectAdapter decodeArg(ObjectSpecification objSpec, String encoded) {
        if(NULL_ARG.equals(encoded)) {
            return null;
        }
        
        if(objSpec.isEncodeable()) {
            EncodableFacet encodeable = objSpec.getFacet(EncodableFacet.class);
            return encodeable.fromEncodedString(encoded);
        }
        
        try {
            final RootOid oid = RootOidDefault.deStringEncoded(encoded, getOidMarshaller());
            return getAdapterManager().adapterFor(oid);
        } catch (final Exception e) {
            return null;
        }
    }

    private void setArgument(final int paramNum, final ObjectAdapter argumentAdapter) {
        final ObjectAction action = actionMemento.getAction();
        final ObjectActionParameter actionParam = action.getParameters().get(paramNum);
        final ActionParameterMemento apm = new ActionParameterMemento(actionParam);
        final ScalarModel argumentModel = getArgumentModel(apm);
        argumentModel.setObject(argumentAdapter);
    }


    public ScalarModel getArgumentModel(final ActionParameterMemento apm) {
        int i = apm.getNumber();
		ScalarModel scalarModel = arguments.get(i);
        if (scalarModel == null) {
            scalarModel = new ScalarModel(targetAdapterMemento, apm);
            final int number = scalarModel.getParameterMemento().getNumber();
            arguments.put(number, scalarModel);
        }
        return scalarModel;
    }

    public ObjectAdapter getTargetAdapter() {
        return targetAdapterMemento.getObjectAdapter(getConcurrencyChecking());
    }

    protected ConcurrencyChecking getConcurrencyChecking() {
        return actionMemento.getConcurrencyChecking();
    }

    public ActionMemento getActionMemento() {
        return actionMemento;
    }

    @Override
    protected ObjectAdapter load() {
        
        // from getObject()/reExecute
        detach(); // force re-execute
        
        // TODO: think we need another field to determine if args have been populated.
        final ObjectAdapter results = executeAction();
        this.actionMode = Mode.RESULTS;
        
        return results;
    }
    
    private ObjectAdapter executeAction() {
        final ObjectAdapter targetAdapter = getTargetAdapter();
        final ObjectAdapter[] arguments = getArgumentsAsArray();
        final ObjectAction action = getActionMemento().getAction();

        // let any exceptions propogate, will be caught by UI layer 
        // (ActionPanel at time of writing)
        final ObjectAdapter results = action.execute(targetAdapter, arguments);
        return results;
    }

    public String getReasonInvalidIfAny() {
        final ObjectAdapter targetAdapter = getTargetAdapter();
        final ObjectAdapter[] proposedArguments = getArgumentsAsArray();
        final ObjectAction objectAction = getActionMemento().getAction();
        final Consent validity = objectAction.isProposedArgumentSetValid(targetAdapter, proposedArguments);
        return validity.isAllowed() ? null : validity.getReason();
    }

    @Override
    public void setObject(final ObjectAdapter object) {
        throw new UnsupportedOperationException("target adapter for ActionModel cannot be changed");
    }

    public ObjectAdapter[] getArgumentsAsArray() {
    	if(this.arguments.size() < this.getActionMemento().getAction().getParameterCount()) {
    		primeArgumentModels();
    	}
    	
        final ObjectAction objectAction = getActionMemento().getAction();
        final ObjectAdapter[] arguments = new ObjectAdapter[objectAction.getParameterCount()];
        for (int i = 0; i < arguments.length; i++) {
            final ScalarModel scalarModel = this.arguments.get(i);
            arguments[i] = scalarModel.getObject();
        }
        return arguments;
    }
    
    public ActionExecutor getExecutor() {
        return executor;
    }

    public void setExecutor(final ActionExecutor executor) {
        this.executor = executor;
    }

    public void reset() {
        this.actionMode = determineMode(actionMemento.getAction());
    }

    public void clearArguments() {
        for (ScalarModel argumentModel : arguments.values()) {
            argumentModel.reset();
        }
        this.actionMode = determineMode(actionMemento.getAction());
    }

    /**
     * Bookmarkable if the {@link ObjectAction action} has a {@link BookmarkPolicyFacet bookmark} policy
     * of {@link BookmarkPolicy#AS_ROOT root}, and has safe {@link ObjectAction#getSemantics() semantics}.
     */
    public boolean isBookmarkable() {
        final ObjectAction action = getActionMemento().getAction();
        final BookmarkPolicyFacet bookmarkPolicy = action.getFacet(BookmarkPolicyFacet.class);
        final boolean safeSemantics = action.getSemantics() == ActionSemantics.Of.SAFE;
        return bookmarkPolicy.value() == BookmarkPolicy.AS_ROOT && safeSemantics;
    }

    
    // //////////////////////////////////////
    
    private ActionPrompt actionPrompt;

    public void setActionPrompt(ActionPrompt actionPrompt) {
        this.actionPrompt = actionPrompt;
    }

    public ActionPrompt getActionPrompt() {
        return actionPrompt;
    }

    // //////////////////////////////////////
    
    public static RecoverableException getApplicationExceptionIfAny(Exception ex) {
        Iterable<RecoverableException> appEx = Iterables.filter(Throwables.getCausalChain(ex), RecoverableException.class);
        Iterator<RecoverableException> iterator = appEx.iterator();
        return iterator.hasNext() ? iterator.next() : null;
    }

    public static IRequestHandler redirectHandler(final Object value) {
        if(value instanceof java.net.URL) {
            java.net.URL url = (java.net.URL) value;
            return new RedirectRequestHandler(url.toString());
        }
        return null;
    }

    public static IRequestHandler downloadHandler(final Object value) {
        if(value instanceof Clob) {
            return downloadHandler((Clob)value);
        }
        if(value instanceof Blob) {
            return downloadHandler((Blob)value);
        }
        return null;
    }
    
    private static IRequestHandler downloadHandler(final Blob blob) {
        ResourceRequestHandler handler = 
            new ResourceRequestHandler(new ByteArrayResource(blob.getMimeType().toString(), blob.getBytes(), blob.getName()), null);
        return handler;
    }
    private static IRequestHandler downloadHandler(final Clob clob) {
        ResourceStreamRequestHandler handler = 
            new ResourceStreamRequestHandler(new StringResourceStream(clob.getChars(), clob.getMimeType().toString()), clob.getName());
        handler.setContentDisposition(ContentDisposition.ATTACHMENT);
        return handler;
    }

    // //////////////////////////////////////
    
    public List<ActionParameterMemento> primeArgumentModels() {
        final ObjectAction objectAction = getActionMemento().getAction();

        final List<ObjectActionParameter> parameters = objectAction.getParameters();
        final List<ActionParameterMemento> mementos = buildParameterMementos(parameters);
        for (final ActionParameterMemento apm : mementos) {
            getArgumentModel(apm);
        }
        
        return mementos;
    }

    
    private static List<ActionParameterMemento> buildParameterMementos(final List<ObjectActionParameter> parameters) {
        final List<ActionParameterMemento> parameterMementoList = Lists.transform(parameters, ObjectAdapterMemento.Functions.fromActionParameter());
        // we copy into a new array list otherwise we get lazy evaluation =
        // reference to a non-serializable object
        return Lists.newArrayList(parameterMementoList);
    }

    //////////////////////////////////////////////////
    // Dependencies (from context)
    //////////////////////////////////////////////////
    
    private static OidMarshaller getOidMarshaller() {
        return IsisContext.getOidMarshaller();
    }



}
