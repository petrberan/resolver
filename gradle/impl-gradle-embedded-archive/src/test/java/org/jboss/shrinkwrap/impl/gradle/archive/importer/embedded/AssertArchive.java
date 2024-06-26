/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.shrinkwrap.impl.gradle.archive.importer.embedded;


import org.jboss.shrinkwrap.api.Archive;

import static org.assertj.core.api.Assertions.assertThat;

public final class AssertArchive {

    private AssertArchive() {
        throw new UnsupportedOperationException("Utils class");
    }

    public static void assertContains(Archive<?> archive, String path) {
        assertThat(archive.contains(path)).as(path + " should be included in archive " + archive.toString(true))
            .isTrue();
    }

    public static void assertNotContains(Archive<?> archive, String path) {
        assertThat(archive.contains(path)).as(path + " should NOT be included in archive " + archive.toString(true))
            .isFalse();
    }
}
