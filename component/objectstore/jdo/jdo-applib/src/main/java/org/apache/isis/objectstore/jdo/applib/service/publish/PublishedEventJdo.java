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

package org.apache.isis.objectstore.jdo.applib.service.publish;

import java.util.UUID;

import javax.jdo.annotations.IdentityType;

import org.apache.isis.applib.DomainObjectContainer;
import org.apache.isis.applib.Identifier;
import org.apache.isis.applib.annotation.ActionSemantics;
import org.apache.isis.applib.annotation.ActionSemantics.Of;
import org.apache.isis.applib.annotation.Bulk;
import org.apache.isis.applib.annotation.Hidden;
import org.apache.isis.applib.annotation.Immutable;
import org.apache.isis.applib.annotation.MemberGroupLayout;
import org.apache.isis.applib.annotation.MemberOrder;
import org.apache.isis.applib.annotation.MultiLine;
import org.apache.isis.applib.annotation.Named;
import org.apache.isis.applib.annotation.NotPersisted;
import org.apache.isis.applib.annotation.ObjectType;
import org.apache.isis.applib.annotation.Programmatic;
import org.apache.isis.applib.annotation.TypicalLength;
import org.apache.isis.applib.annotation.Where;
import org.apache.isis.applib.services.HasTransactionId;
import org.apache.isis.applib.services.bookmark.Bookmark;
import org.apache.isis.applib.services.bookmark.BookmarkService;
import org.apache.isis.applib.services.publish.EventType;
import org.apache.isis.applib.util.ObjectContracts;
import org.apache.isis.applib.util.TitleBuffer;
import org.apache.isis.objectstore.jdo.applib.service.JdoColumnLength;
import org.apache.isis.objectstore.jdo.applib.service.Util;

@javax.jdo.annotations.PersistenceCapable(
        identityType=IdentityType.APPLICATION,
        table="IsisPublishedEvent", 
        objectIdClass=PublishedEventJdoPK.class)
@javax.jdo.annotations.Queries( {
    @javax.jdo.annotations.Query(
            name="findByStateOrderByTimestamp", language="JDOQL",  
            value="SELECT "
                    + "FROM org.apache.isis.objectstore.jdo.applib.service.publish.PublishedEventJdo "
                    + "WHERE state == :state ORDER BY timestamp"),
    @javax.jdo.annotations.Query(
            name="findByTransactionId", language="JDOQL",  
            value="SELECT "
                    + "FROM org.apache.isis.objectstore.jdo.applib.service.publish.PublishedEventJdo "
                    + "WHERE transactionId == :transactionId")
})
@MemberGroupLayout(
        columnSpans={6,0,6},
        left={"Identifiers","Target"},
        right={"Detail","State"})
@Immutable
@ObjectType("IsisPublishedEvent")
public class PublishedEventJdo implements HasTransactionId {

    public static enum State {
        QUEUED, PROCESSED
    }
    

    // //////////////////////////////////////
    // Identification
    // //////////////////////////////////////

    public String title() {
        final TitleBuffer buf = new TitleBuffer();
        buf.append(getEventType().name()).append(" ").append(getTargetStr());
        if(getEventType()==EventType.ACTION_INVOCATION) {
            buf.append(" ").append(getMemberIdentifier());
        }
        buf.append(",").append(getState());
        return buf.toString();
    }


    
    // //////////////////////////////////////
    // user (property)
    // //////////////////////////////////////

    private String user;
    
    @javax.jdo.annotations.Column(allowsNull="false", length=50)
    @MemberOrder(name="Identifiers", sequence = "10")
    @Hidden(where=Where.PARENTED_TABLES)
    public String getUser() {
        return user;
    }
    
    public void setUser(final String user) {
        this.user = user;
    }
    

    // //////////////////////////////////////
    // timestamp (property)
    // //////////////////////////////////////

    private java.sql.Timestamp timestamp;

    @javax.jdo.annotations.Persistent
    @javax.jdo.annotations.Column(allowsNull="false")
    @MemberOrder(name="Identifiers", sequence = "20")
    @Hidden(where=Where.PARENTED_TABLES)
    public java.sql.Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final java.sql.Timestamp timestamp) {
        this.timestamp = timestamp;
    }
    


    // //////////////////////////////////////
    // transactionId
    // //////////////////////////////////////

    private UUID transactionId;

    /**
     * The unique identifier (a GUID) of the transaction in which this published event was persisted.
     * 
     * <p>
     * The combination of ({@link #getTransactionId() transactionId}, {@link #getSequence() sequence}) makes up the
     * primary key.
     */
    @javax.jdo.annotations.PrimaryKey
    @javax.jdo.annotations.Column(allowsNull="false",length=JdoColumnLength.TRANSACTION_ID)
    @MemberOrder(name="Identifiers", sequence = "30")
    @Hidden(where=Where.PARENTED_TABLES)
    @Override
    public UUID getTransactionId() {
        return transactionId;
    }

    @Override
    public void setTransactionId(final UUID transactionId) {
        this.transactionId = transactionId;
    }

    
    // //////////////////////////////////////
    // sequence
    // //////////////////////////////////////

    private int sequence;

    /**
     * The 0-based additional identifier of a published event within the given {@link #getTransactionId() transaction}.
     * 
     * <p>
     * The combination of ({@link #getTransactionId() transactionId}, {@link #getSequence() sequence}) makes up the
     * primary key.
     */
    @javax.jdo.annotations.PrimaryKey
    @MemberOrder(name="Identifiers", sequence = "40")
    public int getSequence() {
        return sequence;
    }

    public void setSequence(final int sequence) {
        this.sequence = sequence;
    }
    

    // //////////////////////////////////////
    // title
    // //////////////////////////////////////

    private String title;

    /**
     * Consists of the full oidStr (with version info etc), concatenated 
     * (if an {@link EventType#ACTION_INVOCATION}) with the name/parms of the action.
     * 
     * <p>
     * @deprecated - the oid of the target is also available (without the version info) through {@link #getTarget()}, and
     *               the action identifier is available through {@link #getMemberIdentifier()}.
     */
    @javax.jdo.annotations.Column(allowsNull="false", length=JdoColumnLength.PublishedEvent.TITLE)
    @Hidden
    @Deprecated
    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }
    
    
    // //////////////////////////////////////
    // eventType (property)
    // //////////////////////////////////////

    private EventType eventType;

    @javax.jdo.annotations.Column(allowsNull="false", length=JdoColumnLength.PublishedEvent.EVENT_TYPE)
    @MemberOrder(name="Identifiers",sequence = "50")
    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(final EventType eventType) {
        this.eventType = eventType;
    }
    

    // //////////////////////////////////////
    // targetClass (property)
    // //////////////////////////////////////

    private String targetClass;

    @javax.jdo.annotations.Column(allowsNull="false", length=JdoColumnLength.TARGET_CLASS)
    @TypicalLength(30)
    @MemberOrder(name="Target", sequence = "10")
    @Named("Class")
    public String getTargetClass() {
        return targetClass;
    }

    public void setTargetClass(final String targetClass) {
        this.targetClass = Util.abbreviated(targetClass, JdoColumnLength.TARGET_CLASS);
    }


    // //////////////////////////////////////
    // targetAction (property)
    // //////////////////////////////////////
    
    private String targetAction;
    
    /**
     * Only populated for {@link EventType#ACTION_INVOCATION}
     */
    @javax.jdo.annotations.Column(allowsNull="true", length=JdoColumnLength.TARGET_ACTION)
    @TypicalLength(30)
    @MemberOrder(name="Target", sequence = "20")
    @Named("Action")
    public String getTargetAction() {
        return targetAction;
    }
    
    public void setTargetAction(final String targetAction) {
        this.targetAction = Util.abbreviated(targetAction, JdoColumnLength.TARGET_ACTION);
    }
    

    // //////////////////////////////////////
    // target (property)
    // openTargetObject (action)
    // //////////////////////////////////////

    @Programmatic
    public Bookmark getTarget() {
        return Util.bookmarkFor(getTargetStr());
    }
    
    @Programmatic
    public void setTarget(Bookmark target) {
        setTargetStr(Util.asString(target));
    }

    // //////////////////////////////////////
    
    private String targetStr;
    @javax.jdo.annotations.Column(allowsNull="false", length=JdoColumnLength.BOOKMARK, name="target")
//    @Hidden(where=Where.ALL_TABLES)
    @MemberOrder(name="Target", sequence="30")
    @Named("Object")
    public String getTargetStr() {
        return targetStr;
    }

    public void setTargetStr(final String targetStr) {
        this.targetStr = targetStr;
    }

    // //////////////////////////////////////

    @Bulk
    @ActionSemantics(Of.SAFE)
    @MemberOrder(name="TargetStr", sequence="1")
    @Named("Open")
    public Object openTargetObject() {
        return Util.lookupBookmark(getTarget(), bookmarkService, container);
    }
    public boolean hideOpenTargetObject() {
        return getTarget() == null;
    }


    // //////////////////////////////////////
    // memberIdentifier (property)
    // //////////////////////////////////////

    private String memberIdentifier;
    
    /**
     * Holds a string representation of the invoked action, equivalent to
     * {@link Identifier#toClassAndNameIdentityString()}.
     * 
     * <p>
     * Only populated for {@link EventType#ACTION_INVOCATION}, 
     * returns <tt>null</tt> otherwise.
     * 
     * <p>
     * This property is called 'memberIdentifier' rather than 'actionIdentifier' for
     * consistency with other services (such as auditing and publishing) that may act on
     * properties rather than simply just actions.
     */
    @javax.jdo.annotations.Column(allowsNull="true", length=JdoColumnLength.MEMBER_IDENTIFIER)
    @TypicalLength(60)
    @Hidden(where=Where.ALL_TABLES)
    @MemberOrder(name="Detail",sequence = "20")
    public String getMemberIdentifier() {
        return memberIdentifier;
    }

    public void setMemberIdentifier(final String actionIdentifier) {
        this.memberIdentifier = Util.abbreviated(actionIdentifier, JdoColumnLength.MEMBER_IDENTIFIER);
    }



    // //////////////////////////////////////
    // state (property)
    // //////////////////////////////////////

    private State state;

    @javax.jdo.annotations.Column(allowsNull="false", length=JdoColumnLength.PublishedEvent.STATE)
    @MemberOrder(name="State", sequence = "30")
    public State getState() {
        return state;
    }

    public void setState(final State state) {
        this.state = state;
    }
    private PublishedEventJdo setStateAndReturn(State state) {
        setState(state);
        return this;
    }
    

    // //////////////////////////////////////
    // serializedFormZipped (property)
    // serializedForm (derived property)
    // //////////////////////////////////////

    @javax.jdo.annotations.NotPersistent
    @NotPersisted
    @MultiLine(numberOfLines=14)
    @Hidden(where=Where.ALL_TABLES)
    @MemberOrder(name="Detail", sequence = "40")
    public String getSerializedForm() {
        return IoUtils.fromUtf8ZippedBytes("serializedForm", getSerializedFormZipped());
    }

    public void setSerializedForm(final String serializedForm) {
        final byte[] zippedBytes = IoUtils.toUtf8ZippedBytes("serializedForm", serializedForm);
        setSerializedFormZipped(zippedBytes);
    }

    // //////////////////////////////////////

    
    @javax.jdo.annotations.Column
    private byte[] serializedFormZipped;

    @Programmatic // ignored by Isis
    public byte[] getSerializedFormZipped() {
        return serializedFormZipped;
    }

    public void setSerializedFormZipped(final byte[] serializedFormZipped) {
        this.serializedFormZipped = serializedFormZipped;
    }

    

    // //////////////////////////////////////
    // processed (action)
    // reQueue   (action)
    // delete    (action)
    // //////////////////////////////////////

 
    @Bulk
    @ActionSemantics(Of.IDEMPOTENT)
    @MemberOrder( name="State", sequence="10")
    public PublishedEventJdo processed() {
        return setStateAndReturn(State.PROCESSED);
    }


    @Bulk
    @ActionSemantics(Of.IDEMPOTENT)
    @MemberOrder(name="State", sequence="11")
    public PublishedEventJdo reQueue() {
        return setStateAndReturn(State.QUEUED);
    }

    @Bulk
    @MemberOrder(name="State", sequence="12")
    public void delete() {
        container.removeIfNotAlready(this);
    }
    

    
    // //////////////////////////////////////
    // toString
    // //////////////////////////////////////

    @Override
    public String toString() {
        return ObjectContracts.toString(this, "targetStr,timestamp,user,eventType,memberIdentifier,state");
    }


    // //////////////////////////////////////
    // dependencies
    // //////////////////////////////////////

    @javax.inject.Inject
    private BookmarkService bookmarkService;

    @javax.inject.Inject
    private DomainObjectContainer container;

}
