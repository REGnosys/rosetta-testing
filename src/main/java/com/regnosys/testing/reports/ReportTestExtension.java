package com.regnosys.testing.reports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.regnosys.rosetta.common.reports.RegReportIdentifier;
import com.regnosys.rosetta.common.reports.RegReportPaths;
import com.regnosys.rosetta.common.reports.RegReportUseCase;
import com.regnosys.rosetta.common.reports.ReportField;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;
import com.regnosys.rosetta.common.util.UrlUtils;
import com.regnosys.rosetta.common.validation.RosettaTypeValidator;
import com.regnosys.rosetta.common.validation.ValidationReport;
import com.rosetta.model.lib.RosettaModelObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.regnosys.testing.reports.FileNameProcessor.removeFileExtension;
import static com.regnosys.testing.reports.FileNameProcessor.removeFilePrefix;
import static com.regnosys.testing.reports.ReportExpectationUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ReportTestExtension<T extends RosettaModelObject> implements BeforeAllCallback, AfterAllCallback {

    private final Module runtimeModule;
    private final ImmutableList<String> rosettaPaths;
    private final Class<T> inputType;
    private Path rootExpectationsPath;

    private List<RegReportIdentifier> reportIdentifiers;
    @Inject @SuppressWarnings("unused")
    private RosettaTypeValidator typeValidator;
    @Inject
    private ReportUtil reportUtil;

    private Multimap<ReportIdentifierAndDataSetName, ReportTestResult> actualExpectation;

    public ReportTestExtension(Module runtimeModule, ImmutableList<String> rosettaPaths, Class<T> inputType) {
        this.runtimeModule = runtimeModule;
        this.rosettaPaths = rosettaPaths;
        this.inputType = inputType;
    }

    public ReportTestExtension<T> withRootExpectationsPath(Path rootExpectationsPath) {
        this.rootExpectationsPath = rootExpectationsPath;
        return this;
    }

    @BeforeAll
    public void beforeAll(ExtensionContext context) {
        Guice.createInjector(runtimeModule).injectMembers(this);
        actualExpectation = ArrayListMultimap.create();
        reportIdentifiers = reportUtil.loadRegReportIdentifier(rosettaPaths);
    }

    public void assertTest(RegReportIdentifier reportIdentifier, String dataSetName, ReportDataItemExpectation expectation, RegReportUseCase reportResult) throws IOException {
        assertNotNull(reportResult);

        Path inputFileName = Paths.get(expectation.getFileName());
        Path outputPath = RegReportPaths.getDefault().getOutputRelativePath();

        // key value
        List<ReportField> results = filterEmptyReportFields(reportResult.getResults());
        Path keyValueExpectationPath = RegReportPaths.getKeyValueExpectationFilePath(outputPath, reportIdentifier, dataSetName, inputFileName);
        ExpectedAndActual<String> keyValue = getExpectedAndActual(keyValueExpectationPath, results);

        // report
        RosettaModelObject useCaseReport = reportResult.getUseCaseReport();
        Path reportExpectationPath = RegReportPaths.getReportExpectationFilePath(outputPath, reportIdentifier, dataSetName, inputFileName);
        ExpectedAndActual<String> report = getExpectedAndActual(reportExpectationPath, useCaseReport);

        // validation failures
        ValidationReport validationReport = typeValidator.runProcessStep(useCaseReport.getType(), useCaseReport);
        validationReport.logReport();
        int actualValidationFailures = validationReport.validationFailures().size();
        Path reportDataSetExpectationsPath = RegReportPaths.getReportExpectationsFilePath(outputPath, reportIdentifier, dataSetName);
        ExpectedAndActual<Integer> validationFailures = new ExpectedAndActual<>(reportDataSetExpectationsPath, expectation.getValidationFailures(), actualValidationFailures);

        ReportTestResult testExpectation = new ReportTestResult(expectation.getFileName(), keyValue, report, validationFailures);

        actualExpectation.put(new ReportIdentifierAndDataSetName(reportIdentifier, dataSetName), testExpectation);

        assertJsonEquals(keyValue.getExpected(), keyValue.getActual());
        assertJsonEquals(report.getExpected(), report.getActual());
        assertEquals(validationFailures.getExpected(), validationFailures.getActual(), "Validation failures");
    }

    @AfterAll
    public void afterAll(ExtensionContext context) throws IOException {
        writeExpectations(actualExpectation);
    }

    public Stream<Arguments> getArguments() {
        // find list of expectation files within the report path
        // - warning this will find paths in all classpath jars, so may return additional unwanted paths
        List<URL> expectationFiles = readReportExpectationsFromPath(rootExpectationsPath, ReportTestExtension.class.getClassLoader());
        return getArguments(expectationFiles);
    }

    public Stream<Arguments> getArguments(List<URL> expectationFiles) {
        ObjectMapper mapper = RosettaObjectMapper.getNewRosettaObjectMapper();
        return expectationFiles.stream()
                // report-data-set expectation contains all expectations for a data-set (e.g. a test pack)
                .map(reportExpectationUrl -> readFile(reportExpectationUrl, mapper, ReportDataSetExpectation.class))
                .flatMap(reportExpectation ->
                        // report-data-item expectation contains expectations of a single input file
                        reportExpectation.getDataItemExpectations().stream()
                                .map(dataItemExpectation -> {
                                    // input file to be tested
                                    String fileName = dataItemExpectation.getFileName();
                                    URL inputFileUrl = Resources.getResource(fileName);
                                    // deserialise into input (e.g. ReportableEvent)
                                    T input = readFile(inputFileUrl, mapper, inputType);
                                    // get the report identifier
                                    String reportName = reportExpectation.getReportName();
                                    RegReportIdentifier reportIdentifier = reportIdentifiers.stream()
                                            .filter(r -> r.getName().equals(reportName))
                                            .findFirst().orElseThrow();
                                    // data item name
                                    String inputName = removeFileExtension(removeFilePrefix(fileName)).replace("-", " ");
                                    return Arguments.of(
                                            String.format("%s | %s", reportName, inputName),
                                            reportIdentifier,
                                            reportExpectation.getDataSetName(),
                                            input,
                                            dataItemExpectation);
                                }));
    }

    private List<ReportField> filterEmptyReportFields(List<ReportField> results) {
        return results.stream()
                .filter(r -> !isEmpty(r.getValue()))
                .collect(Collectors.toList());
    }

    private boolean isEmpty(String s) {
        return s == null || s.length() == 0;
    }

    private static <T> T readFile(URL u, ObjectMapper mapper, Class<T> clazz) {
        try {
            return mapper.readValue(UrlUtils.openURL(u), clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void assertJsonEquals(String expectedJson, String resultJson) {
        assertEquals(
                normaliseLineEndings(expectedJson),
                normaliseLineEndings(resultJson));
    }

    private String normaliseLineEndings(String str) {
        return Optional.ofNullable(str)
                .map(s -> s.replace("\r", ""))
                .orElse(null);
    }

    public Module getRuntimeModule() {
        return runtimeModule;
    }

    public List<RegReportIdentifier> getReportIdentifiers() {
        return reportIdentifiers;
    }
}
