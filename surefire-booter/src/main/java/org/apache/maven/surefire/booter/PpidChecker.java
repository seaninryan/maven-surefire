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

import org.apache.maven.shared.utils.cli.CommandLineException;
import org.apache.maven.shared.utils.cli.Commandline;
import org.apache.maven.shared.utils.cli.StreamConsumer;

import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.SystemUtils.IS_OS_UNIX;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.apache.maven.shared.utils.cli.CommandLineUtils.executeCommandLine;
import static org.apache.maven.surefire.booter.ProcessInfo.INVALID_PROCESS_INFO;

/**
 * Recognizes PPID. Determines lifetime of parent process.
 *
 * @author <a href="mailto:tibordigana@apache.org">Tibor Digana (tibor17)</a>
 * @since 2.20.1
 */
final class PpidChecker
{
    private static final String WMIC_PPID = "ParentProcessId";

    private static final String WMIC_CREATION_DATE = "CreationDate";

    private static final String WINDOWS_CMD =
            "wmic process where (ProcessId=%s) get " + WMIC_CREATION_DATE + ", " + WMIC_PPID;

    private static final String UNIX_CMD = "ps -o etimes= -p $PPID";

    private static final Pattern NUMBER_PATTERN = Pattern.compile( "^[\\d]+$" );

    private final ProcessInfo parentProcessInfo;

    PpidChecker()
    {
        ProcessInfo parentProcess = INVALID_PROCESS_INFO;
        if ( IS_OS_WINDOWS )
        {
            String pid = pid();
            if ( pid != null )
            {
                ProcessInfo currentProcessInfo = windows( pid );
                String ppid = currentProcessInfo.getPPID();
                parentProcess = currentProcessInfo.isValid() ? windows( ppid ) : INVALID_PROCESS_INFO;
            }
        }
        else if ( IS_OS_UNIX )
        {
            parentProcess = unix();
        }
        parentProcessInfo = parentProcess.isValid() ? parentProcess : INVALID_PROCESS_INFO;
    }

    boolean canUse()
    {
        return parentProcessInfo.isValid();
    }

    boolean isParentProcessAlive()
    {
        if ( !canUse() )
        {
            throw new IllegalStateException();
        }

        if ( IS_OS_WINDOWS )
        {
            ProcessInfo pp = windows( parentProcessInfo.getPID() );
            // let's compare creation time, should be same unless killed or PPID is reused by OS into another process
            return pp.isValid() && parentProcessInfo.getTime().equals( pp.getTime() );
        }
        else if ( IS_OS_UNIX )
        {
            ProcessInfo pp = unix();
            // let's compare elapsed time, should be greater or equal if parent process is the same and still alive
            return pp.isValid() && (Long) pp.getTime() >= (Long) parentProcessInfo.getTime();
        }

        throw new IllegalStateException();
    }

    // https://www.freebsd.org/cgi/man.cgi?ps(1)
    // etimes elapsed running time, in decimal integer seconds

    // http://manpages.ubuntu.com/manpages/xenial/man1/ps.1.html
    // etimes elapsed time since the process was started, in seconds.
    static ProcessInfo unix()
    {
        final AtomicReference<ProcessInfo> processInfo = new AtomicReference<ProcessInfo>();
        StreamConsumer processResponse = new StreamConsumer()
        {
            @Override
            public void consumeLine( String line )
            {
                line = line.trim();
                if ( !line.isEmpty() )
                {
                    Matcher matcher = NUMBER_PATTERN.matcher( line );
                    if ( matcher.matches() )
                    {
                        long pidUptime = Long.valueOf( line );
                        processInfo.set( new ProcessInfo( "pid not needed in unix", pidUptime, null ) );
                    }
                }
            }
        };

        try
        {
            Commandline cl = new Commandline();
            cl.createArg().setLine( UNIX_CMD );
            int exitCode = executeCommandLine( cl, processResponse, null );
            processInfo.compareAndSet( null, INVALID_PROCESS_INFO );
            return exitCode == 0 ? processInfo.get() : INVALID_PROCESS_INFO;
        }
        catch ( CommandLineException e )
        {
            return INVALID_PROCESS_INFO;
        }
    }

    static String pid()
    {
        String processName = ManagementFactory.getRuntimeMXBean().getName();
        if ( processName != null && processName.contains( "@" ) )
        {
            String pid = processName.substring( 0, processName.indexOf( '@' ) ).trim();
            if ( NUMBER_PATTERN.matcher( pid ).matches() )
            {
                return pid;
            }
        }
        return null;
    }

    static ProcessInfo windows( final String pid )
    {
        final AtomicReference<ProcessInfo> processInfo = new AtomicReference<ProcessInfo>();
        StreamConsumer processResponse = new StreamConsumer()
        {
            private volatile boolean hasHeader;
            private volatile boolean isStartTimestampFirst;

            @Override
            public void consumeLine( String line )
            {
                line = line.trim();

                if ( line.isEmpty() )
                {
                    return;
                }

                if ( hasHeader )
                {
                    StringTokenizer args = new StringTokenizer( line );
                    if ( args.countTokens() == 2 )
                    {
                        if ( isStartTimestampFirst )
                        {
                            String startTimestamp = args.nextToken();
                            String ppid = args.nextToken();
                            processInfo.set( new ProcessInfo( pid, startTimestamp, ppid ) );
                        }
                        else
                        {
                            String ppid = args.nextToken();
                            String startTimestamp = args.nextToken();
                            processInfo.set( new ProcessInfo( pid, startTimestamp, ppid ) );
                        }
                    }
                }
                else
                {
                    StringTokenizer args = new StringTokenizer( line );
                    if ( args.countTokens() == 2 )
                    {
                        String arg0 = args.nextToken();
                        String arg1 = args.nextToken();
                        isStartTimestampFirst = WMIC_CREATION_DATE.equals( arg0 );
                        hasHeader = isStartTimestampFirst || WMIC_PPID.equals( arg0 );
                        hasHeader &= WMIC_CREATION_DATE.equals( arg1 ) || WMIC_PPID.equals( arg1 );
                    }
                }
            }
        };

        try
        {
            Commandline cl = new Commandline();
            cl.createArg().setLine( String.format( Locale.ROOT, WINDOWS_CMD, pid ) );
            int exitCode = executeCommandLine( cl, processResponse, null );
            processInfo.compareAndSet( null, INVALID_PROCESS_INFO );
            return exitCode == 0 ? processInfo.get() : INVALID_PROCESS_INFO;
        }
        catch ( CommandLineException e )
        {
            return INVALID_PROCESS_INFO;
        }
    }
}
