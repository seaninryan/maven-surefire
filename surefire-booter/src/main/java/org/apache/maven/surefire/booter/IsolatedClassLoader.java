package org.apache.maven.surefire.booter;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * Loads classes from jar files added via {@link #addURL(URL)}.
 */
public class IsolatedClassLoader
    extends URLClassLoader
{
    private static final URL[] EMPTY_URL_ARRAY = new URL[0];

    private static final String JAVA9_ALL_MODULES = "java.se.ee";

    private final ClassLoader parent = ClassLoader.getSystemClassLoader();

    private final Set<URL> urls = new HashSet<URL>();

    private final AtomicStampedReference<Method> java9FindClass = new AtomicStampedReference<Method>( null, 0 );

    private final String roleName;

    private boolean childDelegation = true;

    public IsolatedClassLoader( ClassLoader parent, boolean childDelegation, String roleName )
    {
        super( EMPTY_URL_ARRAY, parent );

        this.childDelegation = childDelegation;

        this.roleName = roleName;
    }

    /**
     * @deprecated this method will use {@link java.io.File} instead of {@link URL} in the next
     * major version.
     */
    @Override
    @Deprecated
    public void addURL( URL url )
    {
        // avoid duplicates
        // todo avoid URL due to calling equals method may cause some overhead due to resolving host or file.
        if ( !urls.contains( url ) )
        {
            super.addURL( url );
            urls.add( url );
        }
    }

    @Override
    public synchronized Class loadClass( String name )
        throws ClassNotFoundException
    {
        if ( name.equals( "javax.xml.ws.Holder" ) )
        {
            try
            {
                Method m = ClassLoader.class.getDeclaredMethod( "findClass", String.class, String.class );
                m.setAccessible( true );
                Object cls = m.invoke( this, JAVA9_ALL_MODULES, name );
                System.out.println( cls );
            }
            catch ( Exception e )
            {
                e.printStackTrace();
            }
        }

        if ( childDelegation )
        {
            Class<?> c = findLoadedClass( name );

            if ( c == null )
            {
                try
                {
                    c = lookupClass( name );
                }
                catch ( ClassNotFoundException e )
                {
                    if ( parent == null )
                    {
                        throw e;
                    }
                    else
                    {
                        c = parent.loadClass( name );
                    }
                }
            }

            return c;
        }
        else
        {
            return super.loadClass( name );
        }
    }

    private Method lookupMethod()
    {
        if ( java9FindClass.getStamp() == 0 )
        {
            try
            {
                Method findClass = ClassLoader.class.getMethod( "findClass", String.class, String.class );
                findClass.setAccessible( true );
                java9FindClass.set( findClass, 1 );
            }
            catch ( NoSuchMethodException e )
            {
                java9FindClass.set( null, 1 );
            }
        }
        return java9FindClass.getReference();
    }

    private Class<?> lookupClass( String fullyQualifiedClass ) throws ClassNotFoundException
    {
        Method findClass = lookupMethod();
        if ( findClass != null )
        {
            Class<?> cls = null;
            try
            {
                cls = (Class<?>) findClass.invoke( this, JAVA9_ALL_MODULES, fullyQualifiedClass );
            }
            catch ( ReflectiveOperationException e )
            {
                // Java 9 ClassLoader.findClass() does not throw exception.
            }

            if ( cls == null )
            {
                throw new ClassNotFoundException();
            }
            return cls;
        }
        return findClass( fullyQualifiedClass );
    }

    @Override
    public String toString()
    {
        return "IsolatedClassLoader{roleName='" + roleName + "'}";
    }
}
