package com.example.payments.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/** Executable architecture rules (hexagonal): dependencies point inwards. */
@AnalyzeClasses(packages = ["com.example.payments"], importOptions = [ImportOption.DoNotIncludeTests::class])
class ArchitectureTest {
    companion object {
        // ArchUnit needs static fields, hence @JvmField inside a companion.
        @JvmField
        @ArchTest
        val domainDependsOnNothingButTheJdk: ArchRule =
            noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "..application..", "..adapter..", "jakarta..", "com.fasterxml..",
                )

        @JvmField
        @ArchTest
        val applicationDoesNotKnowAdapters: ArchRule =
            noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..")

        @JvmField
        @ArchTest
        val webAdapterDoesNotTouchOtherAdapters: ArchRule =
            noClasses().that().resideInAPackage("..adapter.web..")
                .should().dependOnClassesThat().resideInAnyPackage("..adapter.persistence..", "..adapter.messaging..")
    }
}
