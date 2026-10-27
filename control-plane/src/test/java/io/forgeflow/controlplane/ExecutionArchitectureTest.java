package io.forgeflow.controlplane;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
class ExecutionArchitectureTest {
    @Test void coreAndAdaptersKeepTheirBoundaries() {
        var feature = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.forgeflow.controlplane.execution");
        classes().that().resideInAnyPackage("..execution.application..", "..execution.domain..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..execution.application..", "..execution.domain..",
                        "..lifecycle.application..", "..lifecycle.domain..").check(feature);
        classes().that().resideInAPackage("..execution.domain..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..execution.domain..").check(feature);
        noClasses().that().resideInAPackage("..execution.adapter..")
                .should().dependOnClassesThat().resideInAnyPackage("..execution.configuration..", "..execution.application",
                        "..lifecycle.adapter..").check(feature);
        noClasses().that().resideInAPackage("..execution.adapter.in..")
                .should().dependOnClassesThat().resideInAPackage("..execution.adapter.out..").check(feature);
    }
}
