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


package org.apache.isis.runtimes.dflt.runtime.context;

import java.util.List;

import com.google.common.collect.Lists;

import org.apache.isis.core.commons.debug.DebugString;
import org.apache.isis.core.commons.debug.DebuggableWithTitle;


public class DebugList {
    private final List<DebuggableWithTitle> l = Lists.newArrayList();
    private final DebugString summary = new DebugString();

    public DebugList(final String name) {
        l.add(new DebuggableWithTitle() {
            @Override
            public void debugData(DebugString debug) {
                debug.append(summary.toString());
            }

            @Override
            public String debugTitle() {
                return name;
            }
        });
    }

    public void add(String name, Object object) {
        boolean b = object instanceof DebuggableWithTitle;
        if (b) {
            l.add((DebuggableWithTitle) object);
        }
        if (object != null) {
            summary.appendln(name + (b ? "*" : ""), object.toString());
        }
    }

    public DebuggableWithTitle[] debug() {
        return l.toArray(new DebuggableWithTitle[l.size()]);
    }
}
