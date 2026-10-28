package io.forgeflow.controlplane;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ExecutionArchitectureTest {
    @Test void coreAndPortsStayFrameworkFree() {
        var feature = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.forgeflow.controlplane.execution");
        classes().that().resideInAnyPackage("..execution.domain..", "..execution.application..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..execution.domain..", "..execution.application..", "..lifecycle.domain..")
                .check(feature);
        classes().that().resideInAPackage("..execution.domain..").should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "..execution.domain..").check(feature);
        classes().that().resideInAPackage("..execution.application.port..").should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "..execution.domain..", "..execution.application.port..", "..lifecycle.domain..").check(feature);
        noClasses().that().resideInAPackage("..execution.adapter..")
                .should().dependOnClassesThat().resideInAnyPackage("..execution.configuration..", "..execution.application").check(feature);
    }
}
