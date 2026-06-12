package br.dev.ctrls.inovareti;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Ficheiro de teste unitário responsável por validar as regras de governação e o isolamento 
 * das camadas da Arquitetura Hexagonal do sistema Inovare-TI.
 */
@AnalyzeClasses(packages = "br.dev.ctrls.inovareti")
public class ArchitectureTest {

    /**
     * Valida que a camada de domínio é isolada e não acede a classes da camada de infraestrutura.
     * Exclui-se pragmaticamente o 'CryptoConverter' que é necessário para a criptografia JPA transparente.
     */
    @ArchTest
    public static void camada_domain_nao_deve_depender_de_infrastructure(JavaClasses classes) {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat(
                DescribedPredicate.and(
                    JavaClass.Predicates.resideInAPackage("..infrastructure.."),
                    new DescribedPredicate<JavaClass>("não seja CryptoConverter") {
                        @Override
                        public boolean test(JavaClass input) {
                            return !input.getSimpleName().equals("CryptoConverter");
                        }
                    }
                )
            ).check(classes);
    }

    /**
     * Valida que a camada de aplicação acede apenas às portas de entrada/saída (interfaces),
     * sem aceder diretamente a adaptadores JPA concretos de infraestrutura.
     */
    @ArchTest
    public static void camada_application_nao_deve_aceder_directamente_a_adapters_jpa(JavaClasses classes) {
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure.adapter.output.jpa..")
            .check(classes);
    }

    /**
     * Valida que os módulos independentes (como 'finance' e 'appointment') permanecem desacoplados,
     * impedindo imports diretos entre eles. A comunicação deve ser feita via interfaces ou eventos.
     */
    @ArchTest
    public static void modulos_devem_ser_independentes(JavaClasses classes) {
        noClasses()
            .that().resideInAPackage("..modules.appointment..")
            .should().dependOnClassesThat().resideInAPackage("..modules.finance..")
            .because("O módulo appointment não deve depender diretamente de classes do módulo finance.")
            .check(classes);

        noClasses()
            .that().resideInAPackage("..modules.finance..")
            .should().dependOnClassesThat().resideInAPackage("..modules.appointment..")
            .because("O módulo finance não deve depender diretamente de classes do módulo appointment.")
            .check(classes);
    }
}
