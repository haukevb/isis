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
package org.apache.isis.core.progmodel.facets.properties.javaxvaldigits;

import java.math.BigDecimal;

import javax.validation.constraints.Digits;

import org.apache.isis.core.metamodel.facetapi.FacetUtil;
import org.apache.isis.core.metamodel.facetapi.FeatureType;
import org.apache.isis.core.metamodel.facets.Annotations;
import org.apache.isis.core.metamodel.facets.FacetFactoryAbstract;
import org.apache.isis.core.metamodel.facets.FacetedMethod;
import org.apache.isis.core.progmodel.facets.value.bigdecimal.BigDecimalValueFacet;

public class BigDecimalForPropertyDerivedFromJavaxValidationDigitsFacetFactory extends FacetFactoryAbstract {

    public BigDecimalForPropertyDerivedFromJavaxValidationDigitsFacetFactory() {
        super(FeatureType.PROPERTIES_ONLY);
    }

    @Override
    public void process(final ProcessMethodContext processMethodContext) {

        if (BigDecimal.class != processMethodContext.getMethod().getReturnType()) {
            return;
        }

        final Digits annotation = Annotations.getAnnotation(processMethodContext.getMethod(), Digits.class);
        if (annotation == null) {
            return;
        }
        FacetUtil.addFacet(create(processMethodContext, annotation));
    }

    private BigDecimalValueFacet create(final ProcessMethodContext processMethodContext, final Digits annotation) {
        final FacetedMethod holder = processMethodContext.getFacetHolder();
        final int length = annotation.integer() + annotation.fraction();
        final int scale = annotation.fraction();
        return new BigDecimalFacetForPropertyFromJavaxValidationDigitsAnnotation(holder, length, scale);
    }    

}
