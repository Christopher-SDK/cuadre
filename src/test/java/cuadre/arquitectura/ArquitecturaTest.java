package cuadre.arquitectura;

import io.qameta.allure.Epic;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@Epic("0 · Arquitectura")
@AnalyzeClasses(packages = "cuadre", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    @ArchTest
    static final ArchRule elDominioNoConoceFrameworks = noClasses()
            .that().resideInAPackage("cuadre.dominio..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "javax.sql..", "java.sql..", "org.postgresql..",
                    "com.fasterxml..", "cuadre.adaptador..", "cuadre.mutantes..", "cuadre.ensamblaje..")
            .as("El dominio es Java puro: no conoce Spring, SQL, JSON, adaptadores ni mutantes");

    @ArchTest
    static final ArchRule elDominioNoUsaDouble = noClasses()
            .that().resideInAPackage("cuadre.dominio..")
            .should().dependOnClassesThat().haveFullyQualifiedName("java.lang.Double")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.Float")
            .as("El dominio nunca usa Double ni Float para dinero ni tasas");
}
