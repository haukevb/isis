/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.isis.core.progmodel.facets.properties.event;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.joda.time.LocalDate;
import org.junit.Test;

import org.apache.isis.applib.services.eventbus.PropertyChangedEvent;

public class PostsPropertyChangedEventFacetAnnotationTest_newEvent {

    public static class SomeDomainObject {}
    
    public static class SomeDatePropertyChangedEvent extends PropertyChangedEvent<SomeDomainObject, LocalDate> {}
    
    @Test
    public void test() throws Exception {
        SomeDomainObject sdo = new SomeDomainObject();
        final PropertyChangedEvent<SomeDomainObject, LocalDate> ev = PostsPropertyChangedEventFacetAnnotation.newEvent(SomeDatePropertyChangedEvent.class, new LocalDate(2013,4,1), new LocalDate(2013,5,2), sdo);
        assertThat(ev.getSource(), is(sdo));
        assertThat(ev.getOldValue(), is(new LocalDate(2013,4,1)));
        assertThat(ev.getNewValue(), is(new LocalDate(2013,5,2)));
    }

}
