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
package org.apache.isis.applib.services.background;

import org.apache.isis.applib.services.command.Command;


/**
 * Execute a {@link ActionInvocationMemento memento-ized} action as a
 * decoupled task.
 * 
 * <p>
 * Separate from {@link BackgroundService} primarily so that the default
 * implementation, <tt>BackgroundServiceDefault</tt> (in core-runtime) can
 * delegate to different implementations of this service.
 */
public interface BackgroundCommandService {

    void schedule(
            final ActionInvocationMemento aim, 
            final Command command, 
            final String targetClassName, final String targetActionName, final String targetArgs);
}
