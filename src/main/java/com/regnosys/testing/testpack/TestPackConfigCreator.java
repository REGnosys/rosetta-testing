package com.regnosys.testing.testpack;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.ImplementedBy;
import com.google.inject.Injector;

import java.util.List;

@ImplementedBy(TestPackConfigCreatorImpl.class)
public interface TestPackConfigCreator {

    /**
     * Generates pipeline and test-pack config files.
     *
     * @param rosettaPaths    - list of folders that contain rosetta model files, e.g. "drr/rosetta"
     * @param filter          - provides filters to include or exclude
     * @param testPackDefs    - provides list of test-pack information such as test pack name, input type and sample input paths
     * @param outputSchemaMap - output Document type / xsd look up map
     * @param injector        - model runtime guice injector
     */
    void createPipelineAndTestPackConfig(ImmutableList<String> rosettaPaths,
                                         TestPackFilter filter,
                                         List<TestPackDef> testPackDefs,
                                         ImmutableMap<Class<?>, String> outputSchemaMap,
                                         Injector injector);
}
