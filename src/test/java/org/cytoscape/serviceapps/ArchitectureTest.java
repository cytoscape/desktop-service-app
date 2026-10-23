package org.cytoscape.serviceapps;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The registry is the only way to installed-app data, and the form layer stays pure: checked on the
 * compiled classes.
 */
@AnalyzeClasses(
        packages = "org.cytoscape.serviceapps",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String REGISTRY = "org.cytoscape.serviceapps.registry..";

    /** CyActivator only resolves the configuration directory, which it hands to the registry. */
    @ArchTest
    static final ArchRule onlyTheRegistryReadsOrWritesFiles =
            noClasses()
                    .that()
                    .resideOutsideOfPackage(REGISTRY)
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("java.nio.file.Files")
                    .orShould()
                    .dependOnClassesThat()
                    .haveNameMatching(
                            "java\\.io\\.File(Input|Output)Stream|java\\.io\\.File(Reader|Writer)")
                    .because("installed apps are stored by ServiceAppRegistry alone");

    @ArchTest
    static final ArchRule onlyTheRegistryUsesItsStore =
            noClasses()
                    .that()
                    .resideOutsideOfPackage(REGISTRY)
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleNameStartingWith("RegistryStore");

    @ArchTest
    static final ArchRule formsDoNotDependOnTheRegistry =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            "org.cytoscape.serviceapps.form..",
                            "org.cytoscape.serviceapps.placement..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(REGISTRY)
                    .because(
                            "FormSpecBuilder and ServiceAppFormHandler are pure: values come in, a form comes out");
}
