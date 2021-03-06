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
package org.apache.isis.core.runtime.services.memento;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.joda.time.LocalDate;

import org.apache.isis.applib.services.bookmark.Bookmark;
import org.apache.isis.core.commons.exceptions.IsisException;

class Dom4jUtil {
    
    
    private Dom4jUtil(){}
    
    private final static String NULL_MARKER = "$$_isis_null_value_$$";

    static void addChild(final Element el, final String name, final Object value) {
        el.addElement(name).setText(encodeForNulls(value));
    }

    static boolean isSupportedClass(final Class<?> cls) {
        return Parseable.isSupported(cls);
    }

    /**
     * @param el
     * @param name
     * @param cls - see {@link Parseable}
     * @return
     */
    static <T> T getChild(final Element el, final String name, final Class<T> cls) {
        Parseable.assertSupported(cls);
        final Element child = el.element(name);
        if(child == null) { 
            return null;
        }
        final String str = decodeForNulls(child.getText());
        if(str == null) {
            return null;
        }
        return Parseable.parse(str, cls);
    }

    static Document parse(final String xmlStr) {
        try {
            final SAXReader saxReader = new SAXReader();
            Document doc = saxReader.read(new StringReader(xmlStr));
            return doc;
        } catch (DocumentException e) {
            throw new IsisException(e);
        }
    }

    static String asString(final Document doc) {
        XMLWriter writer = null;
        final StringWriter sw = new StringWriter();
        try {
            OutputFormat outputFormat = OutputFormat.createPrettyPrint();
            writer = new XMLWriter(sw, outputFormat);
            writer.write(doc);
            return sw.toString();
        } catch (IOException e) {
            throw new IsisException(e);
        } finally {
            if(writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private static String encodeForNulls(final Object value) {
        return value != null ? value.toString() : NULL_MARKER;
    }
    private static String decodeForNulls(final String valueStr) {
        return NULL_MARKER.equals(valueStr)? null: valueStr;
    }

    // //////////////////////////////////////

    static enum Parseable {
        STRING(String.class) {
            @SuppressWarnings("unchecked")
            public <T> T parseStr(String str) {
                return (T) str;
            }
        },
        BOOLEAN(Boolean.class, boolean.class) {
            @SuppressWarnings("unchecked")
            @Override
            public <T> T parseStr(String str) {
                return (T) new Boolean(str);
            }
        },
        BYTE(Byte.class, byte.class) {
            @SuppressWarnings("unchecked")
            @Override
            public <T> T parseStr(String str) {
                return (T) new Byte(str);
            }
        },
        SHORT(Short.class, short.class) {

            @SuppressWarnings("unchecked")
            @Override
            <T> T parseStr(String str) {
                return (T) new Short(str);
            }
        },
        INTEGER(Integer.class, int.class) {
            @SuppressWarnings("unchecked")
            @Override
            <T> T parseStr(String str) {
                return (T) new Integer(str);
            }
        },
        LONG(Long.class, long.class) {
            @SuppressWarnings("unchecked")
            @Override
            <T> T parseStr(String str) {
                return (T) new Long(str);
            }
        },
        FLOAT(Float.class, float.class) {
            @SuppressWarnings("unchecked")
            @Override
            <T> T parseStr(String str) {
                return (T) new Float(str);
            }
        },
        DOUBLE(Double.class, double.class) {
            @SuppressWarnings("unchecked")
            @Override
            <T> T parseStr(String str) {
                return (T) new Double(str);
            }
        },
        BIG_DECIMAL(BigDecimal.class) {
            @SuppressWarnings("unchecked")
            @Override
            <T> T parseStr(String str) {
                return (T) new BigDecimal(str);
            }
        },
        BIG_INTEGER(BigInteger.class) {
            @SuppressWarnings("unchecked")
            @Override
            <T> T parseStr(String str) {
                return (T) new BigInteger(str);
            }
        },
        LOCAL_DATE(LocalDate.class) {
            @SuppressWarnings("unchecked")
            @Override
            <T> T parseStr(String str) {
                return (T) new LocalDate(str);
            } 
        },
        BOOKMARK(Bookmark.class) {
            @SuppressWarnings("unchecked")
            @Override
            <T> T parseStr(String str) {
                return (T) new Bookmark(str);
            } 
        };
        private final Class<?>[] classes;
        private Parseable(Class<?>... classes) {
            this.classes = classes;
        }
        public Class<?>[] getClasses() {
            return classes;
        }
        abstract <T> T parseStr(String str);
        
        // //////////////////////////////////////

        static <T> T parse(final String str, final Class<?> cls) {
            assertSupported(cls);
            for (Parseable sc : values()) {
                for (Class<?> eachCls: sc.getClasses()) {
                    if(eachCls.isAssignableFrom(cls)) {
                        if(!eachCls.isPrimitive() || str != null) {
                            return sc.parseStr(str);
                        }
                    }
                }
            }
            return null;
        }

        static boolean isSupported(final Class<?> cls) {
            for (Parseable sc : values()) {
                for (Class<?> eachCls: sc.getClasses()) {
                    if(eachCls.isAssignableFrom(cls)) {
                        return true;
                    }
                }
            }
            return false;
        }

        static void assertSupported(final Class<?> cls) {
            if(!isSupported(cls)) {
                throw new IllegalArgumentException("Parsing string to type " + cls.getName() + " is not supported");
            }
        }
        
    }
}
