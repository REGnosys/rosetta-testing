package com.regnosys.testing.schemeimport;

/*-
 * ===============
 * Rune Testing
 * ===============
 * Copyright (C) 2022 - 2024 REGnosys
 * ===============
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ===============
 */

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.regnosys.rosetta.RosettaRuntimeModule;
import com.regnosys.rosetta.RosettaStandaloneSetup;
import com.regnosys.testing.RosettaTestingInjectorProvider;
import org.eclipse.xtext.testing.GlobalRegistries;

public class SchemeImportInjectorProvider extends RosettaTestingInjectorProvider {
    static {
        GlobalRegistries.initializeDefaults();
    }

    @Override
    protected Injector internalCreateInjector() {
        return new RosettaStandaloneSetup() {
            @Override
            public Injector createInjector() {
                return Guice.createInjector(createRuntimeModule());
            }
        }.createInjectorAndDoEMFRegistration();
    }

    @Override
    protected RosettaRuntimeModule createRuntimeModule() {
        // make it work also with Maven/Tycho and OSGI
        // see https://bugs.eclipse.org/bugs/show_bug.cgi?id=493672
        return new SchemeImportModule() {
            @Override
            public ClassLoader bindClassLoaderToInstance() {
                return RosettaTestingInjectorProvider.class
                        .getClassLoader();
            }
        };
    }
}

