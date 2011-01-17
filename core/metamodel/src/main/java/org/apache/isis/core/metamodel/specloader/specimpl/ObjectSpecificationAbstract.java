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

package org.apache.isis.core.metamodel.specloader.specimpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.google.common.collect.Lists;
import com.google.inject.internal.Maps;

import org.apache.isis.applib.Identifier;
import org.apache.isis.applib.filter.Filter;
import org.apache.isis.core.commons.authentication.AuthenticationSession;
import org.apache.isis.core.commons.authentication.AuthenticationSessionProvider;
import org.apache.isis.core.commons.lang.JavaClassUtils;
import org.apache.isis.core.commons.lang.ToString;
import org.apache.isis.core.metamodel.adapter.ObjectAdapter;
import org.apache.isis.core.metamodel.adapter.ServicesProvider;
import org.apache.isis.core.metamodel.consent.Consent;
import org.apache.isis.core.metamodel.consent.InteractionInvocationMethod;
import org.apache.isis.core.metamodel.consent.InteractionResult;
import org.apache.isis.core.metamodel.facetapi.Facet;
import org.apache.isis.core.metamodel.facetapi.FacetHolderImpl;
import org.apache.isis.core.metamodel.facetapi.FeatureType;
import org.apache.isis.core.metamodel.facets.actcoll.typeof.TypeOfFacet;
import org.apache.isis.core.metamodel.facets.collections.modify.CollectionFacet;
import org.apache.isis.core.metamodel.facets.help.HelpFacet;
import org.apache.isis.core.metamodel.facets.hide.HiddenFacet;
import org.apache.isis.core.metamodel.facets.naming.describedas.DescribedAsFacet;
import org.apache.isis.core.metamodel.facets.naming.named.NamedFacet;
import org.apache.isis.core.metamodel.facets.object.aggregated.AggregatedFacet;
import org.apache.isis.core.metamodel.facets.object.dirty.ClearDirtyObjectFacet;
import org.apache.isis.core.metamodel.facets.object.dirty.IsDirtyObjectFacet;
import org.apache.isis.core.metamodel.facets.object.dirty.MarkDirtyObjectFacet;
import org.apache.isis.core.metamodel.facets.object.encodeable.EncodableFacet;
import org.apache.isis.core.metamodel.facets.object.ident.icon.IconFacet;
import org.apache.isis.core.metamodel.facets.object.ident.plural.PluralFacet;
import org.apache.isis.core.metamodel.facets.object.ident.title.TitleFacet;
import org.apache.isis.core.metamodel.facets.object.immutable.ImmutableFacet;
import org.apache.isis.core.metamodel.facets.object.notpersistable.InitiatedBy;
import org.apache.isis.core.metamodel.facets.object.notpersistable.NotPersistableFacet;
import org.apache.isis.core.metamodel.facets.object.parseable.ParseableFacet;
import org.apache.isis.core.metamodel.facets.object.value.ValueFacet;
import org.apache.isis.core.metamodel.interactions.InteractionContext;
import org.apache.isis.core.metamodel.interactions.InteractionUtils;
import org.apache.isis.core.metamodel.interactions.ObjectTitleContext;
import org.apache.isis.core.metamodel.interactions.ObjectValidityContext;
import org.apache.isis.core.metamodel.spec.ActionType;
import org.apache.isis.core.metamodel.spec.Instance;
import org.apache.isis.core.metamodel.spec.ObjectActionSet;
import org.apache.isis.core.metamodel.spec.ObjectInstantiator;
import org.apache.isis.core.metamodel.spec.ObjectSpecification;
import org.apache.isis.core.metamodel.spec.ObjectSpecificationException;
import org.apache.isis.core.metamodel.spec.Persistability;
import org.apache.isis.core.metamodel.spec.SpecificationContext;
import org.apache.isis.core.metamodel.spec.SpecificationLookup;
import org.apache.isis.core.metamodel.spec.feature.ObjectAction;
import org.apache.isis.core.metamodel.spec.feature.ObjectActionParameter;
import org.apache.isis.core.metamodel.spec.feature.ObjectAssociation;
import org.apache.isis.core.metamodel.spec.feature.ObjectAssociationFilters;
import org.apache.isis.core.metamodel.spec.feature.OneToManyAssociation;
import org.apache.isis.core.metamodel.spec.feature.OneToOneAssociation;
import org.apache.isis.core.metamodel.specloader.specimpl.objectlist.ObjectSpecificationForObjectList;

public abstract class ObjectSpecificationAbstract extends FacetHolderImpl implements
    ObjectSpecification {

    private final static Logger LOG = Logger.getLogger(ObjectSpecificationAbstract.class);

    private static class SubclassList {
        private final List<ObjectSpecification> classes = Lists.newArrayList();

        public void addSubclass(final ObjectSpecification subclass) {
            classes.add(subclass);
        }

        public boolean hasSubclasses() {
            return !classes.isEmpty();
        }

        /**
         * @return
         */
        public List<ObjectSpecification> toList() {
            return Collections.unmodifiableList(classes);
        }
    }

    private final AuthenticationSessionProvider authenticationSessionProvider;
    private final ServicesProvider servicesProvider;
    private final ObjectInstantiator objectInstantiator;
    private final SpecificationLookup specificationLookup;

    private final List<ObjectAction> objectActions = Lists.newArrayList();
    private final List<ObjectAssociation> associations = Lists.newArrayList();
    private final List<ObjectSpecification> interfaces = Lists.newArrayList();
    private final SubclassList subclasses = new SubclassList();

    /**
     * Lazily populated.
     */
    private final Map<ActionType, List<ObjectAction>> contributedActionSetsByType = Maps.newLinkedHashMap();

    private final Class<?> correspondingClass;
    private final String fullName;
    private final String shortName;
    private final Identifier identifier;
    private final boolean isAbstract;

    private ObjectSpecification superclassSpec;

    /**
     * Expect to be populated using {@link #setSingularName(String)}, but has default name as well.
     */
    private String singularName = "(no name)";
    /**
     * Expect to be populated using {@link #setPluralName(String)} but has default name as well.
     */
    private String pluralName = "(no name)";
    /**
     * Expect to be populated using {@link #setDescribedAs(String)} but has default name as well.
     */
    private String describedAs = "(no description)";
    private String help = null; /* help is typically a reference (eg a URL) and so should not default to a textual value if not set up */ 

    private Persistability persistability = Persistability.USER_PERSISTABLE;

    private MarkDirtyObjectFacet markDirtyObjectFacet;
    private ClearDirtyObjectFacet clearDirtyObjectFacet;
    private IsDirtyObjectFacet isDirtyObjectFacet;

    private TitleFacet titleFacet;
    private IconFacet iconFacet;

    private boolean introspected = false;

    // //////////////////////////////////////////////////////////////////////
    // Constructor
    // //////////////////////////////////////////////////////////////////////

    public ObjectSpecificationAbstract(
            final Class<?> introspectedClass, final String shortName,
            final SpecificationContext specificationContext) {

        this.correspondingClass = introspectedClass;
        this.fullName = introspectedClass.getName();
        this.shortName = shortName;
        this.isAbstract = JavaClassUtils.isAbstract(introspectedClass);
        this.identifier = Identifier.classIdentifier(introspectedClass);

        // dependencies
        this.authenticationSessionProvider = specificationContext.getAuthenticationSessionProvider();
        this.servicesProvider = specificationContext.getServicesProvider();
        this.objectInstantiator = specificationContext.getObjectInstantiator();
        this.specificationLookup = specificationContext.getSpecificationLookup();
    }

    // //////////////////////////////////////////////////////////////////////
    // Stuff immediately derivable from class
    // //////////////////////////////////////////////////////////////////////

    @Override
    public FeatureType getFeatureType() {
        return FeatureType.OBJECT;
    }

    /**
     * As provided explicitly within the
     * {@link #IntrospectableSpecificationAbstract(Class, String, SpecificationContext)
     * constructor}.
     * 
     * <p>
     * Not API, but <tt>public</tt> so that {@link FacetedMethodsBuilder} can call it.
     */
    @Override
    public Class<?> getCorrespondingClass() {
        return correspondingClass;
    }

    /**
     * As provided explicitly within the
     * {@link #IntrospectableSpecificationAbstract(Class, String, SpecificationContext)
     * constructor}.
     */
    @Override
    public String getShortIdentifier() {
        return shortName;
    }

    /**
     * The {@link Class#getName() (full) name} of the {@link #getCorrespondingClass() class}.
     */
    @Override
    public String getFullIdentifier() {
        return fullName;
    }

    /**
     * Only if {@link #setIntrospected(boolean)} has been called (should be called within
     * {@link #updateFromFacetValues()}.
     */
    @Override
    public boolean isIntrospected() {
        return introspected;
    }

    // //////////////////////////////////////////////////////////////////////
    // Introspection (part 1)
    // //////////////////////////////////////////////////////////////////////

    /**
     * Intended to be called within {@link #introspectTypeHierarchyAndMembers()}.
     */
    protected void setSuperclass(Class<?> superclass) {
        if (superclass == null) {
            return;
        }
        superclassSpec = getSpecificationLookup().loadSpecification(superclass);
        if (superclassSpec != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("  Superclass " + superclass.getName());
            }
            addAsSubclassTo(superclassSpec);
        }
    }

    /**
     * Intended to be called within {@link #introspectTypeHierarchyAndMembers()}.
     */
    protected void addInterfaces(List<ObjectSpecification> interfaces) {
        this.interfaces.addAll(interfaces);
    }

    /**
     * Intended to be called within {@link #introspectTypeHierarchyAndMembers()}.
     */
    protected void addAsSubclassTo(ObjectSpecification supertypeSpec) {
        if (!(supertypeSpec instanceof ObjectSpecificationAbstract)) {
            return;
        }
        // downcast required because addSubclass is (deliberately) not public API
        ObjectSpecificationAbstract introspectableSpec = (ObjectSpecificationAbstract) supertypeSpec;
        introspectableSpec.addSubclass(this);
    }

    /**
     * Intended to be called within {@link #introspectTypeHierarchyAndMembers()}.
     */
    protected void addAsSubclassTo(List<ObjectSpecification> supertypeSpecs) {
        for (ObjectSpecification supertypeSpec : supertypeSpecs) {
            addAsSubclassTo(supertypeSpec);
        }
    }

    private void addSubclass(final ObjectSpecification subclass) {
        this.subclasses.addSubclass(subclass);
    }

    /**
     * Intended to be called within {@link #introspectTypeHierarchyAndMembers()}.
     */
    protected void addAssociations(List<ObjectAssociation> associations) {
        if(associations == null) {
            return;
        }
        this.associations.addAll(associations);
    }

    /**
     * Intended to be called within {@link #introspectTypeHierarchyAndMembers()}.
     */
    protected void addObjectActions(List<ObjectAction> objectActions) {
        if(objectActions == null) {
            return;
        }
        this.objectActions.addAll(objectActions);
    }

    // //////////////////////////////////////////////////////////////////////
    // Introspection (part 2)
    // //////////////////////////////////////////////////////////////////////

    @Override
    public void updateFromFacetValues() {
        clearDirtyObjectFacet = getFacet(ClearDirtyObjectFacet.class);
        markDirtyObjectFacet = getFacet(MarkDirtyObjectFacet.class);
        isDirtyObjectFacet = getFacet(IsDirtyObjectFacet.class);

        titleFacet = getFacet(TitleFacet.class);
        iconFacet = getFacet(IconFacet.class);

        NamedFacet namedFacet = getFacet(NamedFacet.class);
        singularName = namedFacet.value();

        PluralFacet pluralFacet = getFacet(PluralFacet.class);
        pluralName = pluralFacet.value();

        final DescribedAsFacet describedAsFacet = getFacet(DescribedAsFacet.class);
        describedAs = describedAsFacet.value();
        
        final HelpFacet helpFacet = getFacet(HelpFacet.class);
        help = helpFacet == null ? null : helpFacet.value();

        Persistability persistability = determinePersistability();
        this.persistability = persistability;
    }

    private Persistability determinePersistability() {
        final NotPersistableFacet notPersistableFacet = getFacet(NotPersistableFacet.class);
        if(notPersistableFacet==null) {
            return Persistability.USER_PERSISTABLE;
        }
        final InitiatedBy initiatedBy = notPersistableFacet.value();
        if (initiatedBy == InitiatedBy.USER_OR_PROGRAM) {
            return Persistability.TRANSIENT;
        } else if (initiatedBy == InitiatedBy.USER) {
            return Persistability.PROGRAM_PERSISTABLE;
        } else {
            return Persistability.USER_PERSISTABLE;
        }
    }


    /**
     * Intended to be called (if at all) within {@link #updateFromFacetValues()}.
     */
    protected void setClearDirtyObjectFacet(ClearDirtyObjectFacet clearDirtyObjectFacet) {
        this.clearDirtyObjectFacet = clearDirtyObjectFacet;
    }

    /**
     * Intended to be called within {@link #updateFromFacetValues()}.
     */
    protected void setIntrospected(boolean introspected) {
        this.introspected = introspected;
    }

    // //////////////////////////////////////////////////////////////////////
    // Title, Icon
    // //////////////////////////////////////////////////////////////////////
    
    @Override
    public String getTitle(final ObjectAdapter object) {
        if (titleFacet != null) {
            final String titleString = titleFacet.title(object);
            if (titleString != null && !titleString.equals("")) {
                return titleString;
            }
        }
        return (this.isService() ? "" : "Untitled ") + getSingularName();
    }

    @Override
    public String getIconName(final ObjectAdapter reference) {
        return iconFacet == null ? null : iconFacet.iconName(reference);
    }


    // //////////////////////////////////////////////////////////////////////
    // Specification
    // //////////////////////////////////////////////////////////////////////

    @Override
    public Instance getInstance(ObjectAdapter adapter) {
        return adapter;
    }

    // //////////////////////////////////////////////////////////////////////
    // Hierarchical
    // //////////////////////////////////////////////////////////////////////

    /**
     * Determines if this class represents the same class, or a subclass, of the specified class.
     * 
     * <p>
     * cf {@link Class#isAssignableFrom(Class)}, though target and parameter are the opposite way around, ie:
     * 
     * <pre>
     * cls1.isAssignableFrom(cls2);
     * </pre>
     * <p>
     * is equivalent to:
     * 
     * <pre>
     * spec2.isOfType(spec1);
     * </pre>
     * 
     * <p>
     * Callable after {@link #introspectTypeHierarchyAndMembers()} has been called.
     */
    @Override
    public boolean isOfType(final ObjectSpecification specification) {
        if (specification == this) {
            return true;
        }
        for (ObjectSpecification interfaceSpec : interfaces()) {
            if (interfaceSpec.isOfType(specification)) {
                return true;
            }
        }
        final ObjectSpecification superclassSpec = superclass();
        return superclassSpec != null ? superclassSpec.isOfType(specification) : false;
    }

    // //////////////////////////////////////////////////////////////////////
    // Name, Description, Persistability
    // //////////////////////////////////////////////////////////////////////

    @Override
    public String getSingularName() {
        return singularName;
    }

    @Override
    public String getPluralName() {
        return pluralName;
    }

    @Override
    public String getDescription() {
        return describedAs == null ? "" : describedAs;
    }
    
    public String getHelp() {
        return help;
    }

    @Override
    public Persistability persistability() {
        return persistability;
    }

    // //////////////////////////////////////////////////////////////////////
    // Dirty object support
    // //////////////////////////////////////////////////////////////////////

    @Override
    public boolean isDirty(final ObjectAdapter object) {
        return isDirtyObjectFacet == null ? false : isDirtyObjectFacet.invoke(object);
    }

    @Override
    public void clearDirty(final ObjectAdapter object) {
        if (clearDirtyObjectFacet != null) {
            clearDirtyObjectFacet.invoke(object);
        }
    }

    @Override
    public void markDirty(final ObjectAdapter object) {
        if (markDirtyObjectFacet != null) {
            markDirtyObjectFacet.invoke(object);
        }
    }

    // //////////////////////////////////////////////////////////////////////
    // Facet Handling
    // //////////////////////////////////////////////////////////////////////

    @Override
    public <Q extends Facet> Q getFacet(final Class<Q> facetType) {
        final Q facet = super.getFacet(facetType);
        Q noopFacet = null;
        if (isNotANoopFacet(facet)) {
            return facet;
        } else {
            noopFacet = facet;
        }
        if (interfaces() != null) {
            final List<ObjectSpecification> interfaces = interfaces();
            for (int i = 0; i < interfaces.size(); i++) {
                final ObjectSpecification interfaceSpec = interfaces.get(i);
                if (interfaceSpec == null) {
                    // HACK: shouldn't happen, but occurring on occasion when running
                    // XATs under JUnit4. Some sort of race condition?
                    continue;
                }
                final Q interfaceFacet = interfaceSpec.getFacet(facetType);
                if (isNotANoopFacet(interfaceFacet)) {
                    return interfaceFacet;
                } else {
                    if (noopFacet == null) {
                        noopFacet = interfaceFacet;
                    }
                }
            }
        }
        // search up the inheritance hierarchy
        ObjectSpecification superSpec = superclass();
        if (superSpec != null) {
            Q superClassFacet = superSpec.getFacet(facetType);
            if (isNotANoopFacet(superClassFacet)) {
                return superClassFacet;
            }
        }
        return noopFacet;
    }

    private boolean isNotANoopFacet(final Facet facet) {
        return facet != null && !facet.isNoop();
    }

    // //////////////////////////////////////////////////////////////////////
    // DefaultValue
    // //////////////////////////////////////////////////////////////////////

    @Override
    public Object getDefaultValue() {
        return null;
    }

    // //////////////////////////////////////////////////////////////////////
    // Identifier
    // //////////////////////////////////////////////////////////////////////

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }

    // //////////////////////////////////////////////////////////////////
    // create InteractionContext
    // //////////////////////////////////////////////////////////////////

    @Override
    public ObjectTitleContext createTitleInteractionContext(final AuthenticationSession session,
        final InteractionInvocationMethod interactionMethod, final ObjectAdapter targetObjectAdapter) {
        return new ObjectTitleContext(session, interactionMethod, targetObjectAdapter, getIdentifier(),
            targetObjectAdapter.titleString());
    }

    // //////////////////////////////////////////////////////////////////////
    // Superclass, Interfaces, Subclasses, isAbstract
    // //////////////////////////////////////////////////////////////////////

    @Override
    public ObjectSpecification superclass() {
        return superclassSpec;
    }

    @Override
    public List<ObjectSpecification> interfaces() {
        return Collections.unmodifiableList(interfaces);
    }

    @Override
    public List<ObjectSpecification> subclasses() {
        return subclasses.toList();
    }

    @Override
    public boolean hasSubclasses() {
        return subclasses.hasSubclasses();
    }

    @Override
    public final boolean isAbstract() {
        return isAbstract;
    }

    // //////////////////////////////////////////////////////////////////////
    // Associations
    // //////////////////////////////////////////////////////////////////////

    @Override
    public List<ObjectAssociation> getAssociations() {
        return Collections.unmodifiableList(associations);
    }

    /**
     * The association with the given {@link ObjectAssociation#getId() id}.
     * 
     * <p>
     * This is overridable because {@link ObjectSpecificationForObjectList} simply returns <tt>null</tt>.
     * 
     * <p>
     * TODO put fields into hash.
     * 
     * <p>
     * TODO: could this be made final? (ie does the framework ever call this method for an
     * {@link ObjectSpecificationForObjectList})
     */
    @Override
    public ObjectAssociation getAssociation(final String id) {
        for (ObjectAssociation objectAssociation : getAssociations()) {
            if (objectAssociation.getId().equals(id)) {
                return objectAssociation;
            }
        }
        throw new ObjectSpecificationException("No association called '" + id + "' in '" + getSingularName() + "'");
    }

    @Override
    public List<ObjectAssociation> getAssociations(final Filter<ObjectAssociation> filter) {
        final List<ObjectAssociation> allFields = getAssociations();

        final List<ObjectAssociation> selectedFields = Lists.newArrayList();
        for (int i = 0; i < allFields.size(); i++) {
            if (filter.accept(allFields.get(i))) {
                selectedFields.add(allFields.get(i));
            }
        }

        return selectedFields;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<OneToOneAssociation> getProperties() {
        List<OneToOneAssociation> list = new ArrayList<OneToOneAssociation>();
        List associationList = getAssociations(ObjectAssociationFilters.PROPERTIES);
        list.addAll(associationList);
        return list;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<OneToManyAssociation> getCollections() {
        List<OneToManyAssociation> list = new ArrayList<OneToManyAssociation>();
        List associationList = getAssociations(ObjectAssociationFilters.COLLECTIONS);
        list.addAll(associationList);
        return list;
    }

    // //////////////////////////////////////////////////////////////////////
    // getObjectAction, getAction, getActions
    // //////////////////////////////////////////////////////////////////////

    @Override
    public List<ObjectAction> getObjectActionsAll() {
        return Collections.unmodifiableList(objectActions);
    }

    @Override
    public List<ObjectAction> getObjectActions(final ActionType... requestedTypes) {
        List<ObjectAction> actions = Lists.newArrayList();
        for (ActionType type : requestedTypes) {
            addActions(type, actions);
        }
        return actions;
    }

    private void addActions(ActionType type, List<ObjectAction> actions) {
        if (!isService()) {
            actions.addAll(getContributedActions(type));
        }
        actions.addAll(getActions(objectActions, type));
    }

    private List<ObjectAction> getActions(final List<ObjectAction> availableActions, final ActionType type) {
        final List<ObjectAction> actions = Lists.newArrayList();
        for (final ObjectAction action : availableActions) {
            final ActionType actionType = action.getType();
            if (actionType == ActionType.SET) {
                final ObjectActionSet actionSet = (ObjectActionSet) action;
                final List<ObjectAction> subActions = actionSet.getActions();
                for (final ObjectAction subAction : subActions) {
                    if (sameActionTypeOrNotSpecified(type, subAction)) {
                        actions.add(subAction);
                        // REVIEW: why was there a break here?
                        // break;
                    }
                }
            } else {
                if (sameActionTypeOrNotSpecified(type, action)) {
                    actions.add(action);
                }
            }
        }

        return actions;
    }

    protected boolean sameActionTypeOrNotSpecified(final ActionType type, final ObjectAction action) {
        return type == null || action.getType().equals(type);
    }


    // //////////////////////////////////////////////////////////////////////
    // service actions
    // //////////////////////////////////////////////////////////////////////

    @Override
    public List<ObjectAction> getServiceActionsReturning(final ActionType... types) {
        final List<ObjectAction> serviceActions = Lists.newArrayList();
        final List<ObjectAdapter> services = getServicesProvider().getServices();
        for (ObjectAdapter serviceAdapter : services) {
            appendServiceActionsReturning(serviceAdapter, Arrays.asList(types), serviceActions);
        }
        return serviceActions;
    }

    private void appendServiceActionsReturning(ObjectAdapter serviceAdapter, final List<ActionType> types,
        final List<ObjectAction> relatedActionsToAppendTo) {
        final List<ObjectAction> matchingActionsToAppendTo = Lists.newArrayList();
        for (ActionType type : types) {
            final List<ObjectAction> serviceActions = serviceAdapter.getSpecification().getObjectActions(type);
            for (ObjectAction serviceAction : serviceActions) {
                addIfReturnsSubtype(serviceAction, matchingActionsToAppendTo);
            }
        }
        if (matchingActionsToAppendTo.size() > 0) {
            final ObjectActionSet set = new ObjectActionSet("id", serviceAdapter.titleString(), matchingActionsToAppendTo);
            relatedActionsToAppendTo.add(set);
        }
    }

    private void addIfReturnsSubtype(final ObjectAction serviceAction, final List<ObjectAction> matchingActionsToAppendTo) {
        final ObjectSpecification returnType = serviceAction.getReturnType();
        if (returnType == null) {
            return;
        } 
        if (returnType.isCollection()) {
            final TypeOfFacet facet = serviceAction.getFacet(TypeOfFacet.class);
            if (facet != null) {
                final ObjectSpecification elementType = facet.valueSpec();
                addIfReturnsSubtype(serviceAction, elementType, matchingActionsToAppendTo);
            }
        } else {
            addIfReturnsSubtype(serviceAction, returnType, matchingActionsToAppendTo);
        }
    }

    private void addIfReturnsSubtype(final ObjectAction serviceAction, final ObjectSpecification actionType,
        final List<ObjectAction> matchingActionsToAppendTo) {
        if (actionType.isOfType(this)) {
            matchingActionsToAppendTo.add(serviceAction);
        }
    }

    // //////////////////////////////////////////////////////////////////////
    // contributed actions
    // //////////////////////////////////////////////////////////////////////

    /**
     * Finds all service actions that contribute to this spec, if any.
     * 
     * <p>
     * If this specification {@link #isService() is actually for} a service, then returns an empty array.
     * 
     * @return an array of {@link ObjectActionSet}s (!!), each of which contains {@link ObjectAction}s of the requested
     *         type.
     * 
     */
    protected List<ObjectAction> getContributedActions(final ActionType actionType) {
        if (isService()) {
            return Collections.emptyList();
        }
        List<ObjectAction> contributedActionSets = contributedActionSetsByType.get(actionType);
        if (contributedActionSets == null) {
            // populate an ActionSet with all actions contributed by each service
            contributedActionSets = Lists.newArrayList();
            final List<ObjectAdapter> services = getServicesProvider().getServices();
            for (ObjectAdapter serviceAdapter : services) {
                addContributedActionsIfAny(serviceAdapter, actionType, contributedActionSets);
            }
            contributedActionSetsByType.put(actionType, contributedActionSets);
        }
        return contributedActionSets;
    }

    private void addContributedActionsIfAny(ObjectAdapter serviceAdapter, final ActionType actionType,
        final List<ObjectAction> contributedActionSetsToAppendTo) {
        final ObjectSpecification specification = serviceAdapter.getSpecification();
        if (specification == this) {
            return;
        }
        final List<ObjectAction> contributedActions = findContributedActions(specification, actionType);
        // only add if there are matching subactions.
        if (contributedActions.size() == 0) {
            return;
        }
        final ObjectActionSet contributedActionSet =
            new ObjectActionSet("id", serviceAdapter.titleString(), contributedActions);
        contributedActionSetsToAppendTo.add(contributedActionSet);
    }

    private List<ObjectAction> findContributedActions(final ObjectSpecification specification,
        final ActionType actionType) {
        final List<ObjectAction> contributedActions = Lists.newArrayList();
        final List<ObjectAction> serviceActions = specification.getObjectActions(actionType);
        for (ObjectAction serviceAction : serviceActions) {
            if (serviceAction.isAlwaysHidden()) {
                continue;
            }
            // see if qualifies by inspecting all parameters
            if (matchesParameterOf(serviceAction)) {
                contributedActions.add(serviceAction);
            }
        }
        return contributedActions;
    }

    private boolean matchesParameterOf(final ObjectAction serviceAction) {
        final List<ObjectActionParameter> params = serviceAction.getParameters();
        for (ObjectActionParameter param : params) {
            if (isOfType(param.getSpecification())) {
                return true;
            }
        }
        return false;
    }

    // //////////////////////////////////////////////////////////////////////
    // validity
    // //////////////////////////////////////////////////////////////////////

    @Override
    public Consent isValid(final ObjectAdapter inObject) {
        return isValidResult(inObject).createConsent();
    }

    /**
     * TODO: currently this method is hard-coded to assume all interactions are initiated
     * {@link InteractionInvocationMethod#BY_USER by user}.
     */
    @Override
    public InteractionResult isValidResult(final ObjectAdapter targetObjectAdapter) {
        final ObjectValidityContext validityContext =
            createValidityInteractionContext(getAuthenticationSession(), InteractionInvocationMethod.BY_USER,
                targetObjectAdapter);
        return InteractionUtils.isValidResult(this, validityContext);
    }

    /**
     * Create an {@link InteractionContext} representing an attempt to save the object.
     */
    @Override
    public ObjectValidityContext createValidityInteractionContext(final AuthenticationSession session,
        final InteractionInvocationMethod interactionMethod, final ObjectAdapter targetObjectAdapter) {
        return new ObjectValidityContext(session, interactionMethod, targetObjectAdapter, getIdentifier());
    }

    // //////////////////////////////////////////////////////////////////////
    // convenience isXxx (looked up from facets)
    // //////////////////////////////////////////////////////////////////////

    @Override
    public boolean isImmutable() {
        return containsFacet(ImmutableFacet.class);
    }

    @Override
    public boolean isHidden() {
        return containsFacet(HiddenFacet.class);
    }

    @Override
    public boolean isParseable() {
        return containsFacet(ParseableFacet.class);
    }

    @Override
    public boolean isEncodeable() {
        return containsFacet(EncodableFacet.class);
    }

    @Override
    public boolean isValue() {
        return containsFacet(ValueFacet.class);
    }

    @Override
    public boolean isAggregated() {
        return containsFacet(AggregatedFacet.class);
    }

    @Override
    public boolean isCollection() {
        return containsFacet(CollectionFacet.class);
    }

    @Override
    public boolean isNotCollection() {
        return !isCollection();
    }

    @Override
    public boolean isValueOrIsAggregated() {
        return isValue() || isAggregated();
    }

    // //////////////////////////////////////////////////////////////////////
    // misc
    // //////////////////////////////////////////////////////////////////////

    @Override
    public boolean isCollectionOrIsAggregated() {
        return false;
    }

    @Override
    public Object createObject(CreationMode creationMode) {
        throw new UnsupportedOperationException(getFullIdentifier());
    }

    // //////////////////////////////////////////////////////////////////////
    // toString
    // //////////////////////////////////////////////////////////////////////

    @Override
    public String toString() {
        final ToString str = new ToString(this);
        str.append("class", getFullIdentifier());
        return str.toString();
    }

    // //////////////////////////////////////////////////////////////////////
    // Convenience
    // //////////////////////////////////////////////////////////////////////

    /**
     * convenience method to return the current {@link AuthenticationSession} from the
     * {@link #getAuthenticationSessionProvider() injected} {@link AuthenticationSessionProvider}.
     */
    protected final AuthenticationSession getAuthenticationSession() {
        return getAuthenticationSessionProvider().getAuthenticationSession();
    }

    // //////////////////////////////////////////////////////////////////////
    // Dependencies (injected in constructor)
    // //////////////////////////////////////////////////////////////////////

    protected AuthenticationSessionProvider getAuthenticationSessionProvider() {
        return authenticationSessionProvider;
    }

    public ServicesProvider getServicesProvider() {
        return servicesProvider;
    }

    public ObjectInstantiator getObjectInstantiator() {
        return objectInstantiator;
    }

    public SpecificationLookup getSpecificationLookup() {
        return specificationLookup;
    }
}