package io.forgeflow.controlplane.lifecycle;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class LifecycleArchitectureTest {
    @Test
    void policyAndPortsStayFrameworkFreeAndAdaptersDoNotCallConcreteUseCases() {
        var feature=new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.forgeflow.controlplane.lifecycle");
        classes().that().resideInAnyPackage("..lifecycle.domain..","..lifecycle.application..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..","..lifecycle.domain..","..lifecycle.application..").check(feature);
        classes().that().resideInAPackage("..lifecycle.domain..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..","..lifecycle.domain..").check(feature);
        classes().that().resideInAPackage("..lifecycle.application.port..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..","..lifecycle.domain..","..lifecycle.application.port..").check(feature);
        noClasses().that().resideInAPackage("..lifecycle.adapter..")
                .should().dependOnClassesThat().resideInAnyPackage("..lifecycle.configuration..","..lifecycle.application").check(feature);
    }
}
