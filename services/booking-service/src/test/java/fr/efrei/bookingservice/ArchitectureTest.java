package fr.efrei.bookingservice;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "fr.efrei.bookingservice")
class ArchitectureTest {
    @ArchTest static final ArchRule domainIsFrameworkIndependent = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideOutsideOfPackages("..domain..", "java..");
    @ArchTest static final ArchRule controllerUsesUseCases = noClasses().that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAnyPackage("..repository..", "..outbox..", "org.springframework.jdbc..", "org.springframework.amqp..");
    @ArchTest static final ArchRule useCasesDoNotDependOnBroker = noClasses().that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAnyPackage("..outbox..", "org.springframework.amqp..");
}
