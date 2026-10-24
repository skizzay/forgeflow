package io.forgeflow.controlplane.webhook;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class WebhookArchitectureTest {
    @Test
    void coreIsFrameworkFreeAndAdaptersDependOnPortsInsteadOfEachOther() {
        var feature = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.forgeflow.controlplane.webhook");
        classes().that().resideInAnyPackage("..webhook.domain..", "..webhook.application..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..webhook.domain..", "..webhook.application..")
                .check(feature);
        classes().that().resideInAPackage("..webhook.domain..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..webhook.domain..")
                .check(feature);
        classes().that().resideInAPackage("..webhook.application.port..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..webhook.domain..", "..webhook.application.port..")
                .check(feature);
        noClasses().that().resideInAPackage("..webhook.adapter..")
                .should().dependOnClassesThat().resideInAnyPackage("..webhook.configuration..", "..webhook.application")
                .check(feature);
        noClasses().that().resideInAPackage("..webhook.adapter.in..")
                .should().dependOnClassesThat().resideInAPackage("..webhook.adapter.out..")
                .check(feature);
        noClasses().that().resideInAPackage("..webhook.adapter.out..")
                .should().dependOnClassesThat().resideInAPackage("..webhook.adapter.in..")
                .check(feature);
    }
}
