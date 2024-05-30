package com.regnosys.testing.transform;

/*-
 * #%L
 * Rune Testing
 * %%
 * Copyright (C) 2022 - 2024 REGnosys
 * %%
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
 * #L%
 */

import com.regnosys.rosetta.common.transform.TestPackModel;

public class TransformTestResult {

    private final String output;
    private final TestPackModel.SampleModel sampleModel;

    public TransformTestResult(String output, TestPackModel.SampleModel sampleModel) {
        this.output = output;
        this.sampleModel = sampleModel;
    }

    public String getOutput() {
        return output;
    }

    public TestPackModel.SampleModel getSampleModel() {
        return sampleModel;
    }
}
