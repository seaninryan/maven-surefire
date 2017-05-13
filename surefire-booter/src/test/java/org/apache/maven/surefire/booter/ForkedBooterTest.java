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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

import static org.apache.maven.surefire.booter.ForkedBooter.isMasterProcessIdle;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author <a href="mailto:tibordigana@apache.org">Tibor Digana (tibor17)</a>
 * @since 2.20.1
 */
@RunWith( MockitoJUnitRunner.class )
public class ForkedBooterTest
{
    @Mock
    private MemoryMXBean memoryMXBean;

    @Test
    public void test1()
    {
        long previousUptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime() - 9000L;
        long committedHeap = 1024L * 1024L;

        assertThat( isMasterProcessIdle( previousUptimeMillis, committedHeap ) ).isFalse();

        when( memoryMXBean.getHeapMemoryUsage() ).thenReturn( new MemoryUsage( 0L, 0L, committedHeap, committedHeap ) );
        assertThat( isMasterProcessIdle( previousUptimeMillis, memoryMXBean ) ).isFalse();
    }

    @Test
    public void test2()
    {
        long previousUptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime() - 10001L;
        long committedHeap = 1024L * 1024L;

        assertThat( isMasterProcessIdle( previousUptimeMillis, committedHeap ) ).isTrue();

        when( memoryMXBean.getHeapMemoryUsage() ).thenReturn( new MemoryUsage( 0L, 0L, committedHeap, committedHeap ) );
        assertThat( isMasterProcessIdle( previousUptimeMillis, memoryMXBean ) ).isTrue();
    }

    @Test
    public void test3()
    {
        long previousUptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime() - 9000L;
        long committedHeap = 512L * 1024L * 1024L;

        assertThat( isMasterProcessIdle( previousUptimeMillis, committedHeap ) ).isFalse();

        when( memoryMXBean.getHeapMemoryUsage() ).thenReturn( new MemoryUsage( 0L, 0L, committedHeap, committedHeap ) );
        assertThat( isMasterProcessIdle( previousUptimeMillis, memoryMXBean ) ).isFalse();
    }

    @Test
    public void test4()
    {
        long previousUptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime() - 10001L;
        long committedHeap = 512L * 1024L * 1024L;

        assertThat( isMasterProcessIdle( previousUptimeMillis, committedHeap ) ).isTrue();

        when( memoryMXBean.getHeapMemoryUsage() ).thenReturn( new MemoryUsage( 0L, 0L, committedHeap, committedHeap ) );
        assertThat( isMasterProcessIdle( previousUptimeMillis, memoryMXBean ) ).isTrue();
    }

    @Test
    public void test5()
    {
        long previousUptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime() - 59000L;
        long committedHeap = 4096L * 1024L * 1024L;

        assertThat( isMasterProcessIdle( previousUptimeMillis, committedHeap ) ).isFalse();

        when( memoryMXBean.getHeapMemoryUsage() ).thenReturn( new MemoryUsage( 0L, 0L, committedHeap, committedHeap ) );
        assertThat( isMasterProcessIdle( previousUptimeMillis, memoryMXBean ) ).isFalse();
    }

    @Test
    public void test6()
    {
        long previousUptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime() - 60001L;
        long committedHeap = 4096L * 1024L * 1024L;

        assertThat( isMasterProcessIdle( previousUptimeMillis, committedHeap ) ).isTrue();

        when( memoryMXBean.getHeapMemoryUsage() ).thenReturn( new MemoryUsage( 0L, 0L, committedHeap, committedHeap ) );
        assertThat( isMasterProcessIdle( previousUptimeMillis, memoryMXBean ) ).isTrue();
    }
}
