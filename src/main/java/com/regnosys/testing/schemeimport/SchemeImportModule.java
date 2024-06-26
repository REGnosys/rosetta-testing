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

import com.regnosys.testing.RosettaTestingModule;
import com.regnosys.testing.schemeimport.fpml.FpMLSchemeEnumReader;
import com.regnosys.testing.schemeimport.fpml.FpMLSchemeHelper;
import com.regnosys.testing.schemeimport.iso.currency.IsoCurrencySchemeEnumReader;

public class SchemeImportModule extends RosettaTestingModule {
    public Class<? extends AnnotatedRosettaEnumReader> bindAnnotatedRosettaEnumReader() {
        return AnnotatedRosettaEnumReader.class;
    }

    public Class<? extends FpMLSchemeEnumReader> bindFpMLSchemeEnumReader() {
        return FpMLSchemeEnumReader.class;
    }

    public Class<? extends RosettaResourceWriter> bindRosettaResourceWriter() {
        return RosettaResourceWriter.class;
    }

    public Class<? extends SchemeImporter> bindSchemeImporter() {
        return SchemeImporter.class;
    }

    public Class<? extends SchemeImporterTestHelper> bindSchemeImporterTestHelper() {
        return SchemeImporterTestHelper.class;
    }

    public Class<? extends FpMLSchemeHelper> bindFpMLSchemeHelper() {
        return FpMLSchemeHelper.class;
    }

    public Class<? extends IsoCurrencySchemeEnumReader> bindIsoCurrencySchemeEnumReader() {
        return IsoCurrencySchemeEnumReader.class;
    }
}
