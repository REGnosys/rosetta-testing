package com.regnosys.testing.reports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.regnosys.rosetta.common.hashing.ReferenceConfig;
import com.regnosys.rosetta.common.hashing.ReferenceResolverProcessStep;
import com.regnosys.rosetta.common.reports.RegReportPaths;
import com.regnosys.rosetta.common.reports.ReportField;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.rosetta.common.transform.TestPackModel;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.regnosys.rosetta.common.validation.ValidationReport;
import com.regnosys.testing.FieldValueFlattener;
import com.regnosys.testing.TestingExpectationUtil;
import com.regnosys.testing.projection.ProjectionTestExtension;
import com.regnosys.testing.transform.TestPackAndDataSetName;
import com.regnosys.testing.transform.TransformTestResult;
import com.rosetta.model.lib.RosettaModelObject;
import com.rosetta.model.lib.RosettaModelObjectBuilder;
import com.rosetta.model.lib.reports.ReportFunction;
import com.rosetta.model.lib.reports.Tabulator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.regnosys.testing.TestingExpectationUtil.getJsonExpectedAndActual;
import static com.regnosys.testing.reports.FileNameProcessor.removeFileExtension;
import static com.regnosys.testing.reports.FileNameProcessor.removeFilePrefix;
import static com.regnosys.testing.reports.ReportExpectationUtil.writeExpectations;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ReportTestExtension<T extends RosettaModelObject> implements BeforeAllCallback, AfterAllCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportTestExtension.class);
    private final Module runtimeModule;
    private final Class<T> inputType;
    private Path rootExpectationsPath;
    private Path outputPath;

    private String testPackFileName;


    @Inject
    private RosettaTypeValidator typeValidator;
    @Inject
    private ReferenceConfig referenceConfig;

    private Multimap<TestPackAndDataSetName, TransformTestResult> actualExpectation;

    public ReportTestExtension(Module runtimeModule, Class<T> inputType) {
        this.runtimeModule = runtimeModule;
        this.inputType = inputType;
    }

    public ReportTestExtension<T> withRootExpectationsPath(Path rootExpectationsPath) {
        this.rootExpectationsPath = rootExpectationsPath;
        return this;
    }

    public ReportTestExtension<T> withOutputPath(Path outputPath) {
        this.outputPath = outputPath;
        return this;
    }

    public ReportTestExtension<T> withTestPackFileName(String testPackFileName) {
        this.testPackFileName = testPackFileName;
        return this;
    }

    @BeforeAll
    public void beforeAll(ExtensionContext context) {
        Guice.createInjector(runtimeModule).injectMembers(this);
        actualExpectation = ArrayListMultimap.create();
    }

    public <In extends RosettaModelObject, Out extends RosettaModelObject> void runReportAndAssertExpected(
            String testPackId,
            String pipeLineId,
            String datasetName,
            Path reportExpectationsPath,
            TestPackModel.SampleModel sampleModel,
            ReportFunction<In, Out> reportFunction,
            Tabulator<Out> tabulator,
            In input) throws IOException {

        // report
        Out reportOutput = reportFunction.evaluate(resolved(input));
        Path reportExpectationPath = Paths.get(sampleModel.getOutputPath ());
        ExpectedAndActual<String> report = getJsonExpectedAndActual(reportExpectationPath, reportOutput);

        // key value
        FieldValueFlattener flattener = new FieldValueFlattener();
        tabulator.tabulate(reportOutput).forEach(
                field -> field.accept(flattener, List.of())
        );
        List<ReportField> results = flattener.accumulator;
        Path keyValueExpectationPath = Paths.get(sampleModel.getOutputTabulatedPath ());
        ExpectedAndActual<String> keyValue = getJsonExpectedAndActual(keyValueExpectationPath, results);

        if (reportOutput == null && report.getExpected() == null) {
            LOGGER.info("Empty report is expected result for {}", sampleModel.getInputPath());
            return;
        }
        assertNotNull(reportOutput);

        // validation failures
        ValidationReport validationReport = typeValidator.runProcessStep(reportOutput.getType(), reportOutput);
        validationReport.logReport();
        int actualValidationFailures = validationReport.validationFailures().size();
        ExpectedAndActual<Integer> validationFailures = new ExpectedAndActual<>(reportExpectationsPath, sampleModel.getAssertions ().getModelValidationFailures (), actualValidationFailures);

        ExpectedAndActual<Boolean> SchemaValidation = new ExpectedAndActual<> (reportExpectationsPath, sampleModel.getAssertions().isSchemaValidationFailure (), sampleModel.getAssertions().isSchemaValidationFailure ());
        ExpectedAndActual<Boolean> error = new ExpectedAndActual<> (reportExpectationsPath, sampleModel.getAssertions().isRuntimeError (), sampleModel.getAssertions().isRuntimeError ());
        TransformTestResult testExpectation = new TransformTestResult (sampleModel, keyValue, report, validationFailures, SchemaValidation, error);

        actualExpectation.put(new TestPackAndDataSetName (testPackId, pipeLineId, datasetName), testExpectation);

        TestingExpectationUtil.assertJsonEquals(keyValue.getExpected(), keyValue.getActual());
        TestingExpectationUtil.assertJsonEquals(report.getExpected(), report.getActual());
        assertEquals(validationFailures.getExpected(), validationFailures.getActual(), "Validation failures");
    }
    private <T extends RosettaModelObject> T resolved(T modelObject) {
        RosettaModelObjectBuilder builder = modelObject.toBuilder();
        new ReferenceResolverProcessStep(referenceConfig).runProcessStep(modelObject.getType(), builder);
        return (T) builder.build();
    }

    @AfterAll
    public void afterAll(ExtensionContext context) throws IOException {
       writeExpectations(actualExpectation);
    }

    public Stream<Arguments> getArguments() {
        // find list of expectation files within the report path
        // - warning this will find paths in all classpath jars, so may return additional unwanted paths
        List<URL> expectationFiles = TestingExpectationUtil.readExpectationsFromPath(rootExpectationsPath, ReportTestExtension.class.getClassLoader(), testPackFileName);
        return getArguments(expectationFiles);
    }

    public Stream<Arguments> getArguments(List<URL> expectationFiles) {
        ObjectMapper mapper = RosettaObjectMapper.getNewRosettaObjectMapper();
        return expectationFiles.stream()
                .flatMap(expectationUrl -> {
                    Path expectationFilePath = generateRelativeExpectationFilePath(outputPath, expectationUrl);
                    TestPackModel testPackModel = TestingExpectationUtil.readFile(expectationUrl, mapper, TestPackModel.class);
                    return testPackModel.getSamples().stream()
                            .map(sampleModel -> {
                                // input file to be tested
                                String inputFile = sampleModel.getInputPath();
                                URL inputFileUrl = getInputFileUrl(inputFile);
                                // input files can be missing if the upstream report has thrown an exception
                                if (inputFileUrl == null) {
                                    return null;
                                }
                                // deserialise into input (e.g. ESMAEMIRMarginReport)
                                T input = TestingExpectationUtil.readFile(inputFileUrl, mapper, inputType);
                                return Arguments.of(
                                        testPackModel.getId(),
                                        testPackModel.getPipelineId(),
                                        testPackModel.getName(),
                                        expectationFilePath,
                                        input,
                                        sampleModel);
                            });
                })
                .filter(Objects::nonNull);
    }

    private Path generateRelativeExpectationFilePath(Path outputPath, URL expectationUrl) {
        try {
            Path path = Path.of(expectationUrl.toURI());
            String relativePath = path.toString().replaceAll("^.*?(\\Q" + outputPath + "\\E.*)", "$1");
            return Path.of(relativePath);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static URL getInputFileUrl(String inputFile) {
        try {
            return Resources.getResource(inputFile);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Failed to load input file " + inputFile);
            return null;
        }
    }
    public Module getRuntimeModule() {
        return runtimeModule;
    }
}
